#include <Keypad.h>

// === Sensores ===
#define DOOR_SENSOR_PIN 18   // MC-38 reed switch (precisa de GPIO com pull-up interno)
#define PIR_SENSOR_PIN  34   // HC-SR501 (input-only, OK porque PIR tem saída digital ativa)

// === Atuadores ===
#define LED_GREEN_PIN   4    // LED verde — ARMADO
#define LED_RED_PIN     2   // LED vermelho — DESARMADO
#define BUZZER_PIN      13   // Buzzer ativo

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

int lastDoorState = HIGH;
int lastPirState  = LOW;

void updateLeds() {
  digitalWrite(LED_GREEN_PIN, armed ? HIGH : LOW);
  digitalWrite(LED_RED_PIN,   armed ? LOW  : HIGH);
}

void beep(int durationMs) {
  tone(BUZZER_PIN, 150, durationMs);
  //delay(durationMs);
  //noTone(BUZZER_PIN);
}

void setup() {
  Serial.begin(115200);

  pinMode(DOOR_SENSOR_PIN, INPUT_PULLUP);
  pinMode(PIR_SENSOR_PIN,  INPUT);

  pinMode(LED_GREEN_PIN, OUTPUT);
  pinMode(LED_RED_PIN,   OUTPUT);
  //pinMode(BUZZER_PIN,    OUTPUT);
  beep(500);
  //noTone(BUZZER_PIN);
  updateLeds();  // arranca desarmado -> vermelho aceso

  Serial.println("Sensores inicializados");
  Serial.println("Aguardar ~30s para o PIR calibrar...");
  delay(30000);
  Serial.println("PIR pronto. Sistema DESARMADO.");
  Serial.println("Introduza PIN seguido de '#' para alternar estado. '*' limpa.");
}

void loop() {

  tone(BUZZER_PIN, 300, 1000);
  // --- Keypad ---
  char key = keypad.getKey();
  if (key) {
    Serial.print("[KEYPAD] Tecla: ");
    Serial.println(key);

    if (key == '#') {
      // Confirmar PIN
      if (inputBuffer == CORRECT_PIN) {
        armed = !armed;
        Serial.print("[SISTEMA] Estado alterado -> ");
        Serial.println(armed ? "ARMADO" : "DESARMADO");
        updateLeds();
        beep(150);  // confirmação curta
      } else {
        Serial.println("[SISTEMA] PIN INCORRETO");
        beep(80); delay(100); beep(80);  // dois beeps curtos
      }
      inputBuffer = "";
    } else if (key == '*') {
      // Limpar buffer
      inputBuffer = "";
      Serial.println("[SISTEMA] Buffer limpo");
    } else if (key >= '0' && key <= '9') {
      if (inputBuffer.length() < 8) {  // limite de segurança
        inputBuffer += key;
      }
    }
    // teclas A,B,C,D ignoradas por agora — podem servir para funções extra
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
    delay(200);  // debounce
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
  }
  delay(150);  // debounce

  // --- Lógica do alarme: movimento + porta fechada + sistema armado -> buzzer ---
  if (armed && currentPirState == HIGH && currentDoorState == LOW) {
    tone(BUZZER_PIN, 1000);
  } else {
    noTone(BUZZER_PIN);
  }
}