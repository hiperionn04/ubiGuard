#define DOOR_SENSOR_PIN 35   // GPIO4 - MC-38 (sensor magnético)
#define PIR_SENSOR_PIN  34   // GPIO5 - HC-SR501 (sensor de movimento)

int lastDoorState = HIGH;   // MC-38: HIGH = fechado, LOW = aberto
int lastPirState = LOW;     // PIR: HIGH = movimento detetado, LOW = sem movimento

void setup() {
  Serial.begin(115200);
  pinMode(DOOR_SENSOR_PIN, INPUT_PULLUP);
  pinMode(PIR_SENSOR_PIN, INPUT);  // PIR já tem saída digital estabilizada
  
  Serial.println("Sensores inicializados");
  Serial.println("- MC-38 no GPIO4");
  Serial.println("- HC-SR501 no GPIO5");
  Serial.println("Aguardar ~30s para o PIR calibrar...");
  delay(30000);  // tempo de calibração inicial recomendado pelo datasheet
  Serial.println("PIR pronto.");
}

void loop() {
  // --- Sensor magnético (porta/janela) ---
  int currentDoorState = digitalRead(DOOR_SENSOR_PIN);
  if (currentDoorState != lastDoorState) {
    if (currentDoorState == LOW) {
      Serial.println("[MC-38] Porta/janela FECHADA");
    } else {
      Serial.println("[MC-38] Porta/janela ABERTA");
    }
    lastDoorState = currentDoorState;
    delay(50);  // debounce
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

  
}