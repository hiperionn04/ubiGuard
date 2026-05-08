#include <Keypad.h>
#include <DHT.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Preferences.h> 

// === Sensores ===
#define DOOR_SENSOR_PIN 16   
#define PIR_SENSOR_PIN  34   
#define DHT_PIN         15 
#define DHT_TYPE        DHT11

DHT dht(DHT_PIN, DHT_TYPE);
unsigned long lastDhtRead = 0;
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
String inputBuffer = "";
bool armed = false;
bool alarmTriggered = false; 
bool fireTriggered = false;  
bool changingPin = false;    
bool setupMode = false;      

int lastDoorState = HIGH;
int lastPirState  = LOW;

// === Relógio Interno (Simulado) ===
unsigned long lastMinuteTick = 0;
int currentHour = 0;
int currentMinute = 0;

void updateLeds() {
  digitalWrite(LED_GREEN_PIN, armed ? LOW : HIGH);
  digitalWrite(LED_RED_PIN,   armed ? HIGH : LOW);
}

// === Função que desenha tudo no ecrã ===
void updateDisplay() {
  display.clearDisplay();

  // 1. BARRA SUPERIOR (Horas e Temperatura)
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

  // 2. MENSAGEM CENTRAL (Estado do Alarme)
  display.setTextSize(1);
  display.setCursor(0, 22);

  if (setupMode) {
    // Modo de Primeira Utilização
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

  // 3. BARRA INFERIOR (Máscara de PIN)
  display.setTextSize(1);
  display.setCursor(0, 50);
  display.print("PIN: ");
  for (int i = 0; i < inputBuffer.length(); i++) {
    display.print("*");
  }

  display.display();
}

void setup() {
  Serial.begin(115200);
  delay(1000); 

  Serial.println("\n[SISTEMA] A iniciar...");

  // Inicializa OLED logo ao início para podermos mostrar as mensagens
  if(!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) { 
    Serial.println("ERRO CRITICO: Ecra OLED não detetado!");
    for(;;); 
  }
  display.clearDisplay();
  display.display();

  // Inicializa Memória Permanente
  preferences.begin("alarme", false); 
  CORRECT_PIN = preferences.getString("pin", ""); 
  
  if (CORRECT_PIN == "") {
    setupMode = true; 
    Serial.println("[SISTEMA] Primeira utilizacao! Aguardar definicao de PIN...");
    updateDisplay(); // Mostra o ecrã de BEM-VINDO

    // CICLO DE BLOQUEIO: O ESP32 fica preso aqui até um PIN ser criado!
    while (setupMode) {
      char key = keypad.getKey();
      if (key) {
        tone(BUZZER_PIN, 2000, 50); // Beep da tecla
        
        if (key == '#') {
          if (inputBuffer.length() >= 4) {
            CORRECT_PIN = inputBuffer;
            preferences.putString("pin", CORRECT_PIN); // Guarda o PIN
            setupMode = false; // Isto quebra o ciclo while!
            Serial.println("[SISTEMA] PIN inicial configurado com sucesso!");
            tone(BUZZER_PIN, 1500, 400); 
          } else {
            Serial.println("[ERRO] O novo PIN deve ter pelo menos 4 digitos!");
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
      delay(10); // Pequena pausa para evitar que o processador do ESP32 faça crash (Watchdog Timer)
    }
  } else {
    Serial.print("[SISTEMA] PIN carregado da memoria. (Atual: ");
    Serial.print(CORRECT_PIN);
    Serial.println(")");
  }

  // ==========================================
  // DAQUI PARA BAIXO SÓ EXECUTA QUANDO HÁ PIN
  // ==========================================

  dht.begin();

  pinMode(DOOR_SENSOR_PIN, INPUT_PULLUP); 
  pinMode(PIR_SENSOR_PIN,  INPUT);
  pinMode(LED_GREEN_PIN, OUTPUT);
  pinMode(LED_RED_PIN,   OUTPUT);
  
  tone(BUZZER_PIN, 1500, 500); 
  updateLeds();  

  Serial.println("Sensores inicializados");
  Serial.println("Aguardar ~30s para o PIR calibrar...");
  
  // --- MENSAGEM ESTÁTICA NO OLED DURANTE A ESPERA ---
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 25);
  display.print("Aguardar ~30s");
  display.setCursor(0, 35);
  display.print("para calibrar PIR...");
  display.display();
  
  delay(30000); // Mantém o delay simples
  // --------------------------------------------------
  
  Serial.println("PIR pronto.");
  Serial.println("Sistema DESARMADO. Pronto a usar.");

  lastDoorState = digitalRead(DOOR_SENSOR_PIN);
  lastTemp = dht.readTemperature(); 
  
  // Como o delay acabou, esta função vai limpar a mensagem 
  // e desenhar a interface normal com o Estado, Horas e PIN
  updateDisplay(); 
}

void loop() {
  // --- Atualização do Relógio Interno ---
  if (millis() - lastMinuteTick >= 60000) {
    lastMinuteTick = millis();
    currentMinute++;
    if (currentMinute >= 60) {
      currentMinute = 0;
      currentHour++;
      if (currentHour >= 24) currentHour = 0;
    }
    updateDisplay(); 
  }

  // --- Keypad ---
  char key = keypad.getKey();
  if (key) {
    if (!alarmTriggered && !fireTriggered) {
      tone(BUZZER_PIN, 2000, 50); 
    }
    
    Serial.print("[KEYPAD] Tecla: ");
    Serial.println(key);

    if (key == '#') {
      if (changingPin) {
        // --- GUARDAR O NOVO PIN (Modo Alteração) ---
        if (inputBuffer.length() >= 4) { 
          CORRECT_PIN = inputBuffer;
          preferences.putString("pin", CORRECT_PIN); 
          changingPin = false;
          Serial.println("[SISTEMA] PIN alterado com sucesso!");
          if (!alarmTriggered && !fireTriggered) tone(BUZZER_PIN, 2000, 500); 
        } else {
          Serial.println("[ERRO] O novo PIN deve ter pelo menos 4 digitos!");
          if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 500, 150); delay(200); tone(BUZZER_PIN, 500, 150); }
        }
      } else {
        // --- ARMAR/DESARMAR ---
        if (inputBuffer == CORRECT_PIN) {
          armed = !armed;
          if (!armed) {
            alarmTriggered = false;
            if (!fireTriggered) noTone(BUZZER_PIN); 
            Serial.println("[SISTEMA] Estado alterado -> DESARMADO");
          } else {
            Serial.println("[SISTEMA] Estado alterado -> ARMADO");
          }
          updateLeds();
          delay(60); 
          if (!alarmTriggered && !fireTriggered) tone(BUZZER_PIN, 1500, 200);  
        } else {
          Serial.println("[SISTEMA] PIN INCORRETO");
          delay(60);
          if (!alarmTriggered && !fireTriggered) {
            tone(BUZZER_PIN, 500, 100); delay(150); tone(BUZZER_PIN, 500, 100);
          }
        }
      }
      inputBuffer = "";
      
    } else if (key == 'B' && !armed) {
      // --- INICIAR ALTERAÇÃO DE PIN ---
      if (inputBuffer == CORRECT_PIN) {
        changingPin = true;
        Serial.println("[SISTEMA] Modo de alteracao ativado. Introduza NOVO PIN e prima '#'.");
        if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 1800, 100); delay(150); tone(BUZZER_PIN, 1800, 100); }
      } else {
        Serial.println("[SISTEMA] PIN INCORRETO PARA ALTERAR");
        delay(60);
        if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 500, 100); delay(150); tone(BUZZER_PIN, 500, 100); }
      }
      inputBuffer = "";

    } else if (key == 'A') {
      // --- DESATIVAR INCÊNDIO ---
      if (inputBuffer == CORRECT_PIN) {
        if (fireTriggered) {
          Serial.println("[SISTEMA] Alarme de INCÊNDIO silenciado.");
          if (alarmTriggered) {
            tone(BUZZER_PIN, 1000); 
          } else {
            noTone(BUZZER_PIN); delay(60); tone(BUZZER_PIN, 1500, 200); 
          }
        } 
      } else {
        Serial.println("[SISTEMA] PIN INCORRETO");
        delay(60);
        if (!alarmTriggered && !fireTriggered) { tone(BUZZER_PIN, 500, 100); delay(150); tone(BUZZER_PIN, 500, 100); }
      }
      inputBuffer = "";
      
    } else if (key == '*') {
      inputBuffer = "";
      changingPin = false; 
      Serial.println("[SISTEMA] Buffer limpo / Acao cancelada");
      delay(60);
      if (!alarmTriggered && !fireTriggered) tone(BUZZER_PIN, 1000, 100);
      
    } else if (key >= '0' && key <= '9') {
      if (inputBuffer.length() < 8) {  
        inputBuffer += key;
      }
    }
    
    updateDisplay();
  }

  // --- Sensor magnético (porta) ---
  int currentDoorState = digitalRead(DOOR_SENSOR_PIN);
  if (currentDoorState != lastDoorState) {
    if (currentDoorState == LOW) Serial.println("[MC-38] Porta FECHADA");
    else Serial.println("[MC-38] Porta ABERTA");
    lastDoorState = currentDoorState;
    delay(30); 
  }

  // --- Sensor PIR (movimento) ---
  int currentPirState = digitalRead(PIR_SENSOR_PIN);
  if (currentPirState != lastPirState) {
    if (currentPirState == HIGH) Serial.println("[PIR] Movimento DETETADO");
    else Serial.println("[PIR] Movimento terminou");
    lastPirState = currentPirState;
    delay(30); 
  }

  // --- Lógica do alarme (Disparo Contínuo - Roubo) ---
  if (armed && currentPirState == HIGH && currentDoorState == HIGH && !alarmTriggered) {
    alarmTriggered = true; 
    Serial.println("[SISTEMA] ALARME DISPAROU!!!");
    if (!fireTriggered) tone(BUZZER_PIN, 1000); 
    updateDisplay(); 
  }

  // --- Lógica de Leitura DHT e Incêndio ---
  if (millis() - lastDhtRead > 2000) {
    lastDhtRead = millis(); 
    float temp = dht.readTemperature();
    
    if (!isnan(temp)) { 
      bool tempChanged = (abs(temp - lastTemp) >= 0.1); 
      lastTemp = temp;

      if (temp > 60.0 && !fireTriggered) {
        fireTriggered = true;
        Serial.println("[SISTEMA] ALERTA DE INCÊNDIO!!!");
        tone(BUZZER_PIN, 2500); 
        updateDisplay(); 
      } 
      else if (temp < 50.0 && fireTriggered) {
        fireTriggered = false;
        Serial.println("[SISTEMA] Temperatura normalizada. Alarme rearmado.");
        updateDisplay(); 
      } 
      else if (tempChanged) {
        updateDisplay();
      }
    }
  }
} 