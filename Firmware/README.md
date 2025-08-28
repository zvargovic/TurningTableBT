# ESP32 Firmware (Turning Table)

Firmware for ESP32 controlling the rotating table and speaking a simple text protocol over **Bluetooth Classic (SPP)**.

## Build
- **PlatformIO** (VS Code) is recommended.
- Board: `esp32dev` (change to your exact board if needed)
- Baudrate: `115200`

## Bluetooth
- Device name: **TurnTable** (example)
- Transport: **Bluetooth Serial (SPP)**
- Line endings: newline `\n` (CRLF also ok)

Commands
	•	SetSpeedX.X  — speed 1.0..5.0 RPM (e.g. SetSpeed2.5)
	•	SetDirectionCW / SetDirectionCCW or SetDirectionCW
	•	ModeRunC (continuous) / ModeRunT (1 full turn)
	•	Play — start motion
	•	Stop — stop gracefully (honor mode)
	•	GetStatus — query current state
    •   SelfTest — internal test routine; during test, app disables controls.

esponse format:
< STATUS Speed:3.00 Dir:CCW Mode:T Running:0

Status fields
	•	Speed  — float (1...5)
	•	Dir    — CW or CCW
	•	Mode   — C or T
	•	Running— 1 (moving) / 0 (stopped)

## 🧠 Firmware Summary

The firmware uses:

- `U8g2` for OLED rendering (status icons, popup windows, animations)
- `EEPROM` to persist speed, direction, and mode
- `BluetoothSerial` for wireless communication
- `FreeRTOS` task to manage stepper motor timing
- `animated_icon.h` to display rotation animation matching speed and direction

All motor control, display updates, and Bluetooth input are handled asynchronously for smooth performance.

© 2025. Built with ❤️ by [Žarko Vargović, Dubrava232, Zagreb, Croatia]
