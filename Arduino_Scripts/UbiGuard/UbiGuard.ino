#include <Keypad.h>
#include <DHT.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Preferences.h> 
#include <time.h> 

// === Bibliotecas Wi-Fi e Firebase ===
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// === Credenciais de Rede e Firebase ===
#define WIFI_SSID "iPhone de Afonso"
#define WIFI_PASSWORD "1234567810"
#define FIREBASE_API_KEY "AIzaSyCgQ5-OmUPRlFlopVW7nrqnwSy5xcNmqdk"
#define FIREBASE_DATABASE_URL "https://ubiguard-sim-default-rtdb.europe-west1.firebasedatabase.app/"

// === Objetos Firebase ===
FirebaseData fbdo;         
FirebaseData streamFbdo;   
FirebaseAuth auth;
FirebaseConfig config;
bool signupOK = false;

// === Sensores ===
#define DOOR_SENSOR_PIN 16   
#define PIR_SENSOR_PIN  34   
#define DHT_PIN         15 
#define DHT_TYPE        DHT11

DHT dht(DHT_PIN, DHT_TYPE);
unsigned long lastDhtRead = 0;
unsigned long lastHeartbeat = 0;
float lastTemp = 0.0; 

// === Atuadores ===
#define LED_GREEN_PIN   4    
#define LED_RED_PIN     2    
#define BUZZER_PIN      17   

// === OLED Display ===
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET    -1 
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// === Keypad 4x4 ===
const byte ROWS = 4;
const byte COLS = 4;
char keys[COLS][ROWS] = {
  {'1','2','3','A'},
  {'4','5','6','B'},
  {'7','8','9','C'},
  {'*','0','#','D'}
};
byte colPins[COLS] = {27, 14, 12, 13};  
byte rowPins[ROWS] = {32, 33, 25, 26};  
Keypad keypad = Keypad(makeKeymap(keys), rowPins, colPins, ROWS, COLS);

// === Estado do sistema ===
Preferences preferences;     
String CORRECT_PIN = ""; 
String alarmUID = ""; 
String inputBuffer = "";
bool armed = false;
bool alarmTriggered = false; 
bool fireTriggered = false;  
bool changingPin = false;    
bool setupMode = false;      
bool isActivated = false;    

int lastDoorState = HIGH;
int lastPirState  = LOW;

// === Relógio Interno ===
unsigned long lastMinuteTick = 0;
int currentHour = 0;
int currentMinute = 0;

// ==========================================
// FUNÇÕES AUXILIARES E LOGS
// ==========================================
void updateLeds() {
  digitalWrite(LED_GREEN_PIN, armed ? LOW : HIGH);
  digitalWrite(LED_RED_PIN,   armed ? HIGH : LOW);
}

void addLog(String message) {
  if (!signupOK || !isActivated) return;
  
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    Serial.println("[LOG] Falha a obter a hora da internet");
    return;
  }
  
  char timeStringBuff[60];
  strftime(timeStringBuff, sizeof(timeStringBuff), "[%Y-%m-%d %H:%M] - ", &timeinfo);
  String logEntry = String(timeStringBuff) + message;

  Firebase.RTDB.pushStringAsync(&fbdo, "/alarms/" + alarmUID + "/logs", logEntry);
}

void updateDisplay() {
  display.clearDisplay();

  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.printf("%02d:%02d", currentHour, currentMinute);
  
  display.setCursor(85, 0);
  if (lastTemp > 0) {
    display.printf("%.1f C", lastTemp);
  } else {
    display.print("--.- C");
  }

  display.drawLine(0, 10, 128, 10, SSD1306_WHITE);

  display.setTextSize(1);
  display.setCursor(0, 22);

  if (setupMode) {
    display.print("BEM-VINDO!");
    display.setCursor(0, 32);
    display.print("CRIE UM PIN NOVO:");
  } else if (changingPin) {
    display.setTextSize(2);
    display.print("NOVO PIN?");
  } else if (fireTriggered) {
    display.setTextSize(2);
    display.print("INCENDIO!");
  } else if (alarmTriggered) {
    display.setTextSize(2);
    display.print("DISPARADO!");
  } else {
    display.setTextSize(2);
    if (armed) {
      display.print("ARMADO");
    } else {
      display.print("DESARMADO");
    }
  }

  display.setTextSize(1);
  display.setCursor(0, 50);
  display.print("PIN: ");
  for (int i = 0; i < inputBuffer.length(); i++) {
    display.print("*");
  }

  display.display();
}

// ==========================================
// FUNÇÕES DE PROCESSAMENTO DE DADOS 
// (Usadas pelo Stream para não repetir código)
// ==========================================
void processPinChange(String novoPin) {
  if (novoPin != "" && novoPin != CORRECT_PIN) {
    CORRECT_PIN = novoPin;
    preferences.putString("pin", CORRECT_PIN);
    Serial.println("[FIREBASE] Novo PIN recebido da App: " + CORRECT_PIN);
    if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 2000, 100); delay(150); tone(BUZZER_PIN, 2000, 100); }
    addLog("PIN do alarme alterado pela App");
  }
}

void processStatusChange(String novoStatus) {
  if (novoStatus == "Armado" && !armed) {
    armed = true;
    alarmTriggered = false; 
    updateLeds();
    updateDisplay();
    Serial.println("[FIREBASE] Sistema ARMADO remotamente.");
    tone(BUZZER_PIN, 1500, 200); 
    addLog("Sistema Armado pela App");
  } 
  else if (novoStatus == "Desarmado" && armed) {
    armed = false;
    alarmTriggered = false;
    noTone(BUZZER_PIN); 
    updateLeds();
    updateDisplay();
    Serial.println("[FIREBASE] Sistema DESARMADO remotamente.");
    tone(BUZZER_PIN, 1000, 150); delay(150); tone(BUZZER_PIN, 1000, 150); 
    addLog("Sistema Desarmado pela App");
  }
}

// ==========================================
// FUNÇÕES DE STREAM (FIREBASE -> ESP32)
// ==========================================
void streamCallback(FirebaseStream data) {
  String path = data.dataPath(); 
  String dataType = data.dataType(); 

  // Descomenta a linha abaixo se quiseres ver no Serial Monitor a estrutura dos dados que chegam
  // Serial.println("[STREAM] Evento: " + path + " | Tipo: " + dataType);

  // 1. Alteração direta de um único campo
  if (path == "/pin") {
    processPinChange(data.stringData());
  }
  else if (path == "/status") {
    processStatusChange(data.stringData());
  }
  
  // 2. Alteração por "Pacote" JSON (Ex: quando a App usa updateChildren)
  else if (path == "/" && dataType == "json") {
    FirebaseJson json;
    json.setJsonData(data.payload()); 
    FirebaseJsonData jsonData;

    if (json.get(jsonData, "status")) {
      processStatusChange(jsonData.stringValue);
    }
    
    if (json.get(jsonData, "pin")) {
      processPinChange(jsonData.stringValue);
    }
  }
}

void streamTimeoutCallback(bool timeout) {
  if (timeout) Serial.println("[FIREBASE] Stream timeout. A reconectar...");
}

// ==========================================
// SETUP
// ==========================================
void setup() {
  Serial.begin(115200);
  delay(1000); 

  Serial.println("\n[SISTEMA] A iniciar...");

  if(!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) { 
    Serial.println("ERRO CRITICO: Ecra OLED não detetado!");
    for(;;); 
  }
  display.clearDisplay();
  display.display();

  // LIGAR AO WI-FI
  Serial.print("[WIFI] A ligar a ");
  Serial.println(WIFI_SSID);
  display.setCursor(0,20); display.print("A ligar Wi-Fi..."); display.display();
  
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println("\n[WIFI] Ligado! IP: " + WiFi.localIP().toString());

  // CONFIGURAR HORA PELA INTERNET (NTP)
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  setenv("TZ", "WET0WEST,M3.5.0/1,M10.5.0", 1);
  tzset();

  // CONFIGURAR FIREBASE
  config.api_key = FIREBASE_API_KEY;
  config.database_url = FIREBASE_DATABASE_URL;
  if (Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("[FIREBASE] Autenticado de forma anónima!");
    signupOK = true;
  }
  
  config.token_status_callback = tokenStatusCallback; 
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  // LER MEMÓRIA E GERAR UID 
  preferences.begin("alarme", false); 
  CORRECT_PIN = preferences.getString("pin", ""); 
  alarmUID = preferences.getString("uid", "");
  isActivated = preferences.getBool("isActivated", false); 

  if (alarmUID == "") {
    String mac = WiFi.macAddress();
    mac.replace(":", ""); 
    alarmUID = "alarm_" + mac;
    preferences.putString("uid", alarmUID);
    Serial.println("[SISTEMA] Novo UID gerado: " + alarmUID);
  }
  
  // MODO PRIMEIRA UTILIZAÇÃO
  if (CORRECT_PIN == "") {
    setupMode = true; 
    Serial.println("[SISTEMA] Aguardar definicao de PIN no teclado...");
    updateDisplay(); 

    while (setupMode) {
      char key = keypad.getKey();
      if (key) {
        tone(BUZZER_PIN, 2000, 50); 
        
        if (key == '#') {
          if (inputBuffer.length() >= 4) {
            CORRECT_PIN = inputBuffer;
            preferences.putString("pin", CORRECT_PIN); 
            setupMode = false; 
            tone(BUZZER_PIN, 1500, 400); 

            if (signupOK) {
              FirebaseJson json;
              json.set("activated", false); 
              json.set("status", "Desarmado");
              json.set("isFired", false); 
              json.set("pin", CORRECT_PIN); 
              json.set("ownerId", ""); 
              json.set("sensors/Keypad", true);
              json.set("sensors/PIR/activated", true);
              json.set("sensors/PIR/last_read", "not_detected");
              json.set("sensors/Temperature/activated", true);
              json.set("sensors/Temperature/last_read", "0º");
              json.set("sensors/Door/activated", true);
              json.set("sensors/Door/last_read", "fechada");
              json.set("sensors/rfid", false);

              String path = "/alarms/" + alarmUID;
              Firebase.RTDB.setJSON(&fbdo, path.c_str(), &json);
            }
          } else {
            tone(BUZZER_PIN, 500, 150); delay(200); tone(BUZZER_PIN, 500, 150);
          }
          inputBuffer = "";
        } else if (key == '*') {
          inputBuffer = "";
        } else if (key >= '0' && key <= '9') {
          if (inputBuffer.length() < 8) inputBuffer += key;
        }
        updateDisplay();
      }
      delay(10); 
    }
  }

  // =========================================================
  // MODO STANDBY - MOSTRA O ID NO ECRÃ PARA O INSTALADOR
  // =========================================================
  if (!isActivated) {
    Serial.println("[SISTEMA] Aguardando instalador na App...");
    
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 5); 
    display.print("Aguardando App...");
    display.setCursor(0, 25); 
    display.print("ID do Alarme:");
    
    // Mostra o UID
    display.setCursor(0, 40); 
    display.print(alarmUID); 
    display.display();

    while (!isActivated) {
      if (signupOK) {
        if (Firebase.RTDB.getBool(&fbdo, "/alarms/" + alarmUID + "/activated")) {
          if (fbdo.boolData() == true) {
            isActivated = true;
            preferences.putBool("isActivated", true); 
            
            if (Firebase.RTDB.getString(&fbdo, "/alarms/" + alarmUID + "/pin")) {
              String pinInstalador = fbdo.stringData();
              if(pinInstalador != "" && pinInstalador != CORRECT_PIN) {
                CORRECT_PIN = pinInstalador;
                preferences.putString("pin", CORRECT_PIN);
              }
            }
            tone(BUZZER_PIN, 1500, 500); 
            addLog("Sistema configurado e ativado pelo instalador");
          }
        }
      }
      delay(3000); 
    }
  }

// INICIAR ESCUTA FIREBASE
  if (signupOK) {
    String alarmPath = "/alarms/" + alarmUID;
    Firebase.RTDB.setStringAsync(&fbdo, (alarmPath + "/network/ssid").c_str(), WiFi.SSID());

    double homeLat = 38.662778;
    double homeLng = -9.204889;
    Firebase.RTDB.setDoubleAsync(&fbdo, (alarmPath + "/network/lat").c_str(), homeLat);
    Firebase.RTDB.setDoubleAsync(&fbdo, (alarmPath + "/network/lng").c_str(), homeLng);

    Firebase.RTDB.beginStream(&streamFbdo, alarmPath.c_str());
    Firebase.RTDB.setStreamCallback(&streamFbdo, streamCallback, streamTimeoutCallback);
  }

  // INICIALIZAÇÃO DE SENSORES
  dht.begin();
  pinMode(DOOR_SENSOR_PIN, INPUT_PULLUP); 
  pinMode(PIR_SENSOR_PIN,  INPUT);
  pinMode(LED_GREEN_PIN, OUTPUT);
  pinMode(LED_RED_PIN,   OUTPUT);
  
  tone(BUZZER_PIN, 1500, 500); 
  updateLeds();  

  Serial.println("Aguardar ~30s para o PIR calibrar...");
  display.clearDisplay(); display.setTextSize(1); display.setCursor(0, 25);
  display.print("Aguardar ~30s"); display.setCursor(0, 35); display.print("para calibrar PIR..."); display.display();
  delay(30000); 
  
  lastDoorState = digitalRead(DOOR_SENSOR_PIN);
  lastTemp = dht.readTemperature(); 
  updateDisplay(); 
  addLog("Sistema Ligado e Operacional");
}

// ==========================================
// LOOP PRINCIPAL
// ==========================================
void loop() {
  // Atualizar Relógio Ecrã 
  struct tm timeinfo;
  if (getLocalTime(&timeinfo)) {
    if (currentMinute != timeinfo.tm_min) {
      currentHour = timeinfo.tm_hour;
      currentMinute = timeinfo.tm_min;
      updateDisplay();
    }
  }

  // --- Keypad Lógica ---
  char key = keypad.getKey();
  if (key) {
    if (!alarmTriggered && !fireTriggered) tone(BUZZER_PIN, 2000, 50); 
    
    if (key == '#') {
      if (changingPin) {
        if (inputBuffer.length() >= 4) { 
          CORRECT_PIN = inputBuffer;
          preferences.putString("pin", CORRECT_PIN); 
          changingPin = false;
          Serial.println("[SISTEMA] PIN alterado no teclado!");
          
          if (signupOK) Firebase.RTDB.setStringAsync(&fbdo, "/alarms/" + alarmUID + "/pin", CORRECT_PIN);
          if (!alarmTriggered && !fireTriggered) tone(BUZZER_PIN, 2000, 500); 
          addLog("PIN do alarme alterado fisicamente");
        } else {
          if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 500, 150); delay(200); tone(BUZZER_PIN, 500, 150); }
        }
      } else {
        if (inputBuffer == CORRECT_PIN) {
          armed = !armed;
          if (!armed) {
            alarmTriggered = false;
            if (!fireTriggered) noTone(BUZZER_PIN); 
            Serial.println("[SISTEMA] DESARMADO");
            addLog("Sistema Desarmado pelo teclado");
            
            if (signupOK) {
              Firebase.RTDB.setStringAsync(&fbdo, "/alarms/" + alarmUID + "/status", "Desarmado");
              if (!fireTriggered) Firebase.RTDB.setBoolAsync(&fbdo, "/alarms/" + alarmUID + "/isFired", false);
            }
          } else {
            Serial.println("[SISTEMA] ARMADO");
            addLog("Sistema Armado pelo teclado");
            if (signupOK) Firebase.RTDB.setStringAsync(&fbdo, "/alarms/" + alarmUID + "/status", "Armado");
          }
          
          updateLeds();
          delay(60); 
          if (!alarmTriggered && !fireTriggered) tone(BUZZER_PIN, 1500, 200);  
        } else {
          Serial.println("[SISTEMA] PIN INCORRETO");
          delay(60);
          if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 500, 100); delay(150); tone(BUZZER_PIN, 500, 100); }
        }
      }
      inputBuffer = "";
    } else if (key == 'B' && !armed) {
      if (inputBuffer == CORRECT_PIN) {
        changingPin = true;
        if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 1800, 100); delay(150); tone(BUZZER_PIN, 1800, 100); }
      } else {
        delay(60); if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 500, 100); delay(150); tone(BUZZER_PIN, 500, 100); }
      }
      inputBuffer = "";
    } else if (key == 'A') {
      if (inputBuffer == CORRECT_PIN) {
        if (fireTriggered) {
          addLog("Alarme de INCÊNDIO silenciado no teclado");
          
          if (alarmTriggered) {
            tone(BUZZER_PIN, 1000); 
          } else { 
            noTone(BUZZER_PIN); 
            delay(60); 
            tone(BUZZER_PIN, 1500, 200); 
            
            if (signupOK) Firebase.RTDB.setBoolAsync(&fbdo, "/alarms/" + alarmUID + "/isFired", false);
          }
        } 
      } else {
        delay(60); if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 500, 100); delay(150); tone(BUZZER_PIN, 500, 100); }
      }
      inputBuffer = "";
    } else if (key == '*') {
      inputBuffer = ""; changingPin = false; 
      delay(60); if (!alarmTriggered && !fireTriggered) tone(BUZZER_PIN, 1000, 100);
    } else if (key >= '0' && key <= '9') {
      if (inputBuffer.length() < 8) inputBuffer += key;
    }
    updateDisplay();
  }

  // --- Sensor magnético (porta) ---
  int currentDoorState = digitalRead(DOOR_SENSOR_PIN);
  if (currentDoorState != lastDoorState) {
    if (currentDoorState == LOW) {
      Serial.println("[MC-38] Porta FECHADA");
      if (signupOK) Firebase.RTDB.setStringAsync(&fbdo, "/alarms/" + alarmUID + "/sensors/Door/last_read", "fechada");
      addLog("Porta principal fechada");
    } else {
      Serial.println("[MC-38] Porta ABERTA");
      if (signupOK) Firebase.RTDB.setStringAsync(&fbdo, "/alarms/" + alarmUID + "/sensors/Door/last_read", "aberta");
      addLog("Porta principal aberta");
    }
    lastDoorState = currentDoorState;
    delay(30); 
  }

  // --- Sensor PIR (movimento) ---
  int currentPirState = digitalRead(PIR_SENSOR_PIN);
  if (currentPirState != lastPirState) {
    if (currentPirState == HIGH) {
      Serial.println("[PIR] Movimento DETETADO");
      if (signupOK) Firebase.RTDB.setStringAsync(&fbdo, "/alarms/" + alarmUID + "/sensors/PIR/last_read", "detected");
    } else {
      Serial.println("[PIR] Movimento terminou");
      if (signupOK) Firebase.RTDB.setStringAsync(&fbdo, "/alarms/" + alarmUID + "/sensors/PIR/last_read", "not_detected");
    }
    lastPirState = currentPirState;
    delay(30); 
  }

  // --- Disparo do Alarme de Intrusão ---
  if (armed && currentPirState == HIGH && currentDoorState == HIGH && !alarmTriggered) {
    alarmTriggered = true; 
    Serial.println("[SISTEMA] ALARME DISPAROU!!!");
    if (!fireTriggered) tone(BUZZER_PIN, 1000); 
    addLog("ALARME DISPAROU (Intrusão Detetada!)");
    
    if (signupOK) Firebase.RTDB.setBoolAsync(&fbdo, "/alarms/" + alarmUID + "/isFired", true);
    
    updateDisplay(); 
  }

  // --- Temperatura & Incêndio ---
  if (millis() - lastDhtRead > 2000) {
    lastDhtRead = millis(); 
    float temp = dht.readTemperature();
    
    if (!isnan(temp)) { 
      bool tempChanged = (abs(temp - lastTemp) >= 0.1); 
      
      if (tempChanged) {
        lastTemp = temp;
        Serial.printf("[DHT11] Nova Temperatura: %.1f °C\n", temp);
        if (signupOK) Firebase.RTDB.setStringAsync(&fbdo, "/alarms/" + alarmUID + "/sensors/Temperature/last_read", String(temp, 1) + "º");
        updateDisplay();
      }

      if (temp > 60.0 && !fireTriggered) {
        fireTriggered = true;
        Serial.println("[SISTEMA] ALERTA DE INCÊNDIO!!!");
        tone(BUZZER_PIN, 2500); 
        addLog("ALERTA DE INCÊNDIO (Temperatura Excedida!)");
        
        if (signupOK) Firebase.RTDB.setBoolAsync(&fbdo, "/alarms/" + alarmUID + "/isFired", true);
        
        updateDisplay(); 
      } 
      else if (temp < 50.0 && fireTriggered) {
        fireTriggered = false;
        Serial.println("[SISTEMA] Temperatura normalizada.");
        addLog("Alarme de Incêndio rearmado (Temp. normalizou)");
        
        if (signupOK && !alarmTriggered) Firebase.RTDB.setBoolAsync(&fbdo, "/alarms/" + alarmUID + "/isFired", false);
        
        updateDisplay(); 
      } 
    }
  }

  // --- Heartbeat (Sinal de Vida para a Firebase) ---
  if (millis() - lastHeartbeat > 30000) {  // De 30 em 30 segundos
    lastHeartbeat = millis();
    if (signupOK && isActivated) {
      Firebase.RTDB.setIntAsync(&fbdo, "/alarms/" + alarmUID + "/heartbeat", millis() / 1000);
    }
  }

}