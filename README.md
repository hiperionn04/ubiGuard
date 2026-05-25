# TEAM

* Afonso Simões - 73204
* Francisca Inácio - 73986
* Nasha Bagasse - 60913

---

# UbiGuard - Smart IoT Security System

**UbiGuard** is an intelligent and distributed (IoT) alarm system focused on residential security. It consists of a hardware device (based on an ESP32) and an Android mobile application, connected in real-time via Google Firebase. 

The system features advanced capabilities such as **Smart Geofencing** (warns the user if they leave home without arming the alarm), **Dynamic Setup via WiFiManager** (Captive Portal), and **Heartbeat Monitoring** (detects power outages or internet drops).

---

## System Architecture

* **Hardware (ESP32):** Acts as the physical brain of the house. It reads sensors (PIR, Door, Temperature), manages the OLED screen, the 4x4 keypad, and the audible alarms. Communicates directly with Firebase via RTDB Stream.
* **Android App:** The main control panel. It features two access levels (Installer and User). It allows users to arm/disarm, view real-time status, manage permissions, and execute background tasks (GPS Radar Mode).
* **Cloud (Firebase RTDB & Auth):** The "middleware". Synchronizes the state between the mobile app and the hardware in milliseconds.

---

## Materials and Requirements

### Required Hardware
* 1x ESP32 Microcontroller
* 1x PIR Motion Sensor
* 1x Magnetic Door/Window Sensor (MC-38)
* 1x Temperature and Humidity Sensor (DHT11)
* 1x 4x4 Matrix Keypad
* 1x OLED Display (SSD1306 128x64) via I2C
* 1x Active Buzzer and LEDs (Green and Red)

### Required Software
* **Arduino IDE** (with the following libraries installed: `Firebase ESP Client`, `WiFiManager` by tzapu, `Adafruit GFX`, `Adafruit SSD1306`, `Keypad`, `DHT sensor library`).
* **Android Studio** (Koala or higher).
* A configured **Firebase Project** (Authentication (with Anonymous and Email/Password) enabled and Realtime Database with permissive read/write rules for testing purposes).

---

## How to Start (Setup)

### 1. Configuring the ESP32
1. Open the `UbiGuard.ino` file in the Arduino IDE.
2. Ensure that your Firebase credentials are correct at the top of the code (`FIREBASE_API_KEY` and `FIREBASE_DATABASE_URL`).
3. Compile and upload the code to the ESP32.
4. The OLED screen will indicate that the ESP32 has created a temporary network called `UbiGuard_XXXX`.

### 2. Configuring the Android App
1. Open the Android project folder in Android Studio.
2. Make sure your `google-services.json` file is placed inside the `app/` folder.
3. Compile and install the App on a **physical Android phone** (Emulators do not work well for background Wi-Fi and GPS testing).

---

## Step-by-Step Testing Guide

UbiGuard was designed to handle complex, real-world scenarios. Follow this guide to test all system features end-to-end.

To test this is necessary to create at least 3 accounts, one for Installer (should be changed in Firebase), one for Owner, and one for Guest/Child.

### Test 1: Initial Installer Setup (Captive Portal & GPS)
*Objective: Connect the alarm to the home internet and register its geographical location without hardcoding anything.*
1. Power up the ESP32. The OLED screen will ask you to connect to the `UbiGuard_XXXX` network.
2. On your Android phone, go to Wi-Fi settings and connect to that network.
3. A captive portal (webpage) will open automatically. Click on **Configure WiFi**, select your home router, and enter the password.
4. The ESP32 will reboot, connect to your home Wi-Fi, and enter the *"Aguardando App..."* (Waiting for App) mode, displaying the Alarm ID on the screen.
5. Open the UbiGuard App using an **Installer** account.
6. A physical PIN will be requested on the keypad, the user should insert a PIN + "#".
7. Then on App click on **Activate Alarm**, input the ID shown on the OLED, give it a name, and click Save.
8. Then click on **Associate Alarm**, input the ID of the alarm, and the email of the user that will be the owner.
9. **Success:** The OLED screen will update, and the App has silently saved your **current Latitude and Longitude** to Firebase when the Installer activate the alarm!

---

### Test 2: Geofencing & Forgetfulness Alert
*Objective: Test if the app detects when you leave the house and prompts you to arm the system based on your location.*

> **TESTING TIP (DEVELOPER MODE):**
> To test this without actually walking 1000 meters away from home:
> 1. Go to Android Studio, open the `AlarmMonitorService.kt` file.
> 2. Find the variable: `private var distanceFromHome: Int = 1000`.
> 3. Temporarily change it to: `private var distanceFromHome: Int = 1` (1 meter).
> 4. Run the App again to apply the change.

1. Ensure your phone is connected to the **same Wi-Fi network** as the ESP32. (The alarm must be "Disarmed").
2. On your phone, **turn off Wi-Fi** (to simulate leaving the house).
3. The App will immediately trigger "Radar Mode" in the background.
4. Stand up and take **2 or 3 steps** (to simulate movement and force the phone's GPS to update past the 1-meter threshold).
5. Wait between 30 to 60 seconds (the GPS polling interval).
6. **Success:** A High-Priority notification ("Left home?") will pop up on your phone!
7. Click the **"Arm Now"** button on the notification. The notification will vanish instantly, and the ESP32 OLED screen will change to "ARMED" almost in real-time.

---

### Test 3: Intrusion Trigger (Hardware)
*Objective: Test the physical security sensors.*
1. Make sure the system is in the **ARMED** state.
2. Move the magnet away from the door sensor (MC-38) **and** wave your hand in front of the motion sensor (PIR).
3. The ESP32 Buzzer will start sounding continuously.
4. Your phone will receive a Maximum Alert notification (Alarm Triggered!).
5. To silence it, type your `PIN + #` on the physical Keypad or press the Disarm button in the App.

---

### Test 4: Critical Fire Alert (Temperature)
*Objective: Test the automatic fire trigger and manual silencing.*

> **TESTING TIP (DEVELOPER MODE):**
> To test the fire alarm without a lighter or hairdryer, lower the threshold in `UbiGuard.ino`:
> 1. Find the trigger line: `if (temp > 60.0 && !fireTriggered)` and change `60.0` to `30.0`.
> 2. Find the rearm line: `else if (temp < 50.0 && fireTriggered)` and change `50.0` to `25.0`.
> 3. Upload the code. Now, your body heat/breath is enough to trigger it!

1. Leave the system in any state (Even **DISARMED**).
2. Blow warm air directly onto the DHT11 sensor.
3. As soon as the temperature on the OLED passes the threshold (e.g., 30.0ºC), the ESP32 will trigger immediately with a high-pitched sound.
4. The OLED will flash "INCÊNDIO!" (FIRE!) and your phone will receive a critical alert.
5. **To silence manually:** Type **`PIN + A`** on the physical keypad and the fire alarm stops.
6. The system will automatically rearm and return to normal once the temperature drops below the safety threshold (e.g., 25.0ºC).

---

### Test 5: Permission Management (Guest & Child Profiles)
*Objective: Validate Role-Based Access Control (RBAC) and alarm sharing.*
1. Open the App using the **Alarm Owner's** account.
2. Navigate to the alarm management/association screen and add the email address of a friend or family member.
3. On another phone (or after logging out), log in with that person's account.
4. The Alarm will appear on their Dashboard with a **Guest** or **Child** profile.
5. **Security Test:** Verify that this profile has strict permissions: they can **only** Arm and Disarm the system. They do not have the authorization or visible buttons to change the PIN, delete the alarm, or access admin settings.

---

### Test 6: Connectivity Time-Bomb (Heartbeat)
*Objective: Test fault tolerance (Power/Internet outage or sabotage attempt).*
1. Open the App and confirm your Alarm is Online.
2. Disable the WiFi (use a HotSpot for this) or disconnect the alarm from the grid.
3. Do not touch the App. Just wait for **about 65 seconds**.
4. The App's background service (which tracks the Firebase timeout) will hit its limit.
5. **Success:** You will receive a warning notification: "Alarm Offline - Warning: The alarm lost power or network connection!".
6. Plug the ESP32 back in. Once it reconnects, the App will notify you with "Alarm Online".

---

### Test 7: Local Physical Control (Keypad)
*Objective: Test manual user interaction directly on the machine.*
1. **Arm/Disarm:** Type the current PIN and press `#`. (The Green/Red LEDs should toggle).
2. **Change the PIN:** Type the PIN + `B`. Then the screen will pop-up `NOVO PIN`. Type the new PIN + `#`. The App will silently update to reflect the new PIN and an notification saying the PIN was updated is sent.

---
