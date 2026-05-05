#include <Keypad.h>

// === Sensores ===
#define DOOR_SENSOR_PIN 16   // MC-38 reed switch (PIN RX2)
#define PIR_SENSOR_PIN  34   // HC-SR501 (input-only, OK porque PIR tem saída digital ativa)

// === Atuadores ===
#define LED_GREEN_PIN   4    // LED verde — DESARMADO
#define LED_RED_PIN     2    // LED vermelho — ARMADO
#define BUZZER_PIN      17   // Buzzer ativo (TX2)

// === Keypad 4x4 ===
const byte ROWS = 4;
const byte COLS = 4;
char keys[COLS][ROWS] = {
  {'1','2','3','A'},
  {'4','5','6','B'},
  {'7','8','9','C'},
  {'*','0','#','D'}
};
byte colPins[COLS] = {27, 14, 12, 13};  // C1..C4
byte rowPins[ROWS] = {32, 33, 25, 26};  // R1..R4  
Keypad keypad = Keypad(makeKeymap(keys), rowPins, colPins, ROWS, COLS);

// === Estado do sistema ===
const String CORRECT_PIN = "1234";
String inputBuffer = "";
bool armed = false;
bool alarmTriggered = false; // Memoriza se o alarme está a tocar

int lastDoorState = HIGH;
int lastPirState  = LOW;

void updateLeds() {
  // Desarmado = Verde | Armado = Vermelho
  digitalWrite(LED_GREEN_PIN, armed ? LOW : HIGH);
  digitalWrite(LED_RED_PIN,   armed ? HIGH : LOW);
}

void setup() {
  Serial.begin(115200);

  // --- ALTERAÇÃO: Apenas INPUT porque já temos o pull-up físico ---
  pinMode(DOOR_SENSOR_PIN, INPUT_PULLUP); 
  pinMode(PIR_SENSOR_PIN,  INPUT);

  pinMode(LED_GREEN_PIN, OUTPUT);
  pinMode(LED_RED_PIN,   OUTPUT);
  
  tone(BUZZER_PIN, 1500, 500); // Beep de arranque
  updateLeds();  // Arranca desarmado -> Verde aceso

  Serial.println("Sensores inicializados");
  Serial.println("Aguardar ~30s para o PIR calibrar...");
  delay(30000);
  Serial.println("PIR pronto. Sistema DESARMADO.");
  Serial.println("Introduza PIN seguido de '#' para alternar estado. '*' limpa.");

  lastDoorState = digitalRead(DOOR_SENSOR_PIN);
  Serial.print("Estado inicial da porta: ");
  Serial.println(lastDoorState == LOW ? "FECHADA" : "ABERTA");
}

void loop() {
  // --- Keypad ---
  char key = keypad.getKey();
  if (key) {
    // Barulhinho rápido APENAS se o alarme não estiver já a gritar
    if (!alarmTriggered) {
      tone(BUZZER_PIN, 2000, 50); 
    }
    
    Serial.print("[KEYPAD] Tecla: ");
    Serial.println(key);

    if (key == '#') {
      // Confirmar PIN
      if (inputBuffer == CORRECT_PIN) {
        armed = !armed;
        
        if (!armed) {
          // Se desarmou, desligamos o alarme contínuo
          alarmTriggered = false;
          noTone(BUZZER_PIN); 
          Serial.print("[SISTEMA] Estado alterado -> DESARMADO\n");
        } else {
          Serial.print("[SISTEMA] Estado alterado -> ARMADO\n");
        }
        
        updateLeds();
        delay(60); // Pausa muito breve para não encavalar o som da tecla
        tone(BUZZER_PIN, 1500, 200);  // Beep de sucesso (longo)
      } else {
        Serial.println("[SISTEMA] PIN INCORRETO");
        delay(60);
        tone(BUZZER_PIN, 500, 100); delay(150); tone(BUZZER_PIN, 500, 100);  // Erro (dois beeps)
      }
      inputBuffer = "";
    } else if (key == '*') {
      // Limpar buffer
      inputBuffer = "";
      Serial.println("[SISTEMA] Buffer limpo");
      delay(60);
      tone(BUZZER_PIN, 1000, 100);
    } else if (key >= '0' && key <= '9') {
      if (inputBuffer.length() < 8) {  // limite de segurança
        inputBuffer += key;
      }
    }
  }

  // --- Sensor magnético (porta) ---
  int currentDoorState = digitalRead(DOOR_SENSOR_PIN);
  if (currentDoorState != lastDoorState) {
    if (currentDoorState == LOW) {
      Serial.println("[MC-38] Porta FECHADA");
    } else {
      Serial.println("[MC-38] Porta ABERTA");
    }
    lastDoorState = currentDoorState;
    delay(30);  // Debounce rápido
  }

  // --- Sensor PIR (movimento) ---
  int currentPirState = digitalRead(PIR_SENSOR_PIN);
  if (currentPirState != lastPirState) {
    if (currentPirState == HIGH) {
      Serial.println("[PIR] Movimento DETETADO");
    } else {
      Serial.println("[PIR] Movimento terminou");
    }
    lastPirState = currentPirState;
    delay(30);  // Debounce rápido
  }

  // --- Lógica do alarme (Disparo Contínuo) ---
  if (armed && currentPirState == HIGH && currentDoorState == HIGH && !alarmTriggered) {
    alarmTriggered = true; // Guarda a memória de que foi disparado
    Serial.println("[SISTEMA] ALARME DISPAROU!!!");
    tone(BUZZER_PIN, 1000); // Liga o buzzer indefinidamente 
  }
}