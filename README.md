# TurningTableBT / OkretniStolBT

Android application for controlling a **Bluetooth-connected rotating table (ESP32 based)**.  
The app allows setting rotation direction, mode, and speed, as well as performing a self-test.  
Includes a log window for debugging commands sent/received over Bluetooth.

---

## English 🇬🇧

### Features
- Connects to ESP32 over Bluetooth (auto-reconnects to last device).
- Control rotation:
  - **CW / CCW** (clockwise / counter-clockwise).
  - **Continuous** or **Target angle** mode.
  - Speed adjustment (1–5 RPM).
- Self-test mode (disables controls until test finishes).
- Log viewer (shows sent/received serial commands).
- Intro screen with setup instructions.
- Multi-language support (English & Croatian).
- Optimized for both phones and foldables.

### Requirements
- Android 8.0 (API 26) or higher.
- ESP32 device flashed with the compatible firmware.

### Installation
1. Pair your ESP32 device with your phone via Bluetooth settings.
2. Install the APK on your Android device.
3. Launch the app and select your paired ESP32.
4. Use on-screen buttons and slider to control the turntable.

### Permissions
The app will request the following runtime permissions:
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- (Optionally) Location access if required by Android version.

---

## Hrvatski 🇭🇷

### Značajke
- Povezuje se s ESP32 putem Bluetootha (automatski se spaja na zadnji uređaj).
- Upravljanje rotacijom:
  - **CW / CCW** (u smjeru kazaljke na satu / suprotno).
  - Način rada **kontinuirano** ili **1 krug**.
  - Podešavanje brzine (1–5 RPM).
- Self-test način (onemogućava kontrole dok test traje).
- Prozor s logovima (prikazuje poslane/primljene serijske naredbe).
- Uvodni ekran s uputama za korištenje.
- Višejezična podrška (engleski i hrvatski).
- Optimizirano za mobitele i preklopne uređaje.

### Zahtjevi
- Android 8.0 (API 26) ili noviji.
- ESP32 uređaj s kompatibilnim firmwareom.

### Instalacija
1. Uparite ESP32 uređaj s telefonom u Bluetooth postavkama.
2. Instalirajte APK na svoj Android uređaj.
3. Pokrenite aplikaciju i odaberite upareni ESP32.
4. Koristite gumbe i klizač na ekranu za upravljanje stolom.

### Dozvole
Aplikacija traži sljedeće dozvole pri prvom pokretanju:
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- (Opcionalno) lokacija, ovisno o Android verziji.

---
## Author: Žarko Vargović, Dubrava 232, Zagreb, Croatia
## License
This project is private and intended for personal use.