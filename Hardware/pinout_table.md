# 🔌 ESP32 Pinout — Turning Table Firmware

| **Function**              | **ESP32 GPIO** | **Description**                                         |
|---------------------------|----------------|---------------------------------------------------------|
| OLED SDA (I2C)            | GPIO 16        | I2C data line for SSD1306 OLED display                 |
| OLED SCL (I2C)            | GPIO 17        | I2C clock line for SSD1306 OLED                        |
| Stepper Motor IN1         | GPIO 27        | Connected to IN1 on ULN2003 driver                     |
| Stepper Motor IN2         | GPIO 26        | Connected to IN2 on ULN2003 driver                     |
| Stepper Motor IN3         | GPIO 25        | Connected to IN3 on ULN2003 driver                     |
| Stepper Motor IN4         | GPIO 33        | Connected to IN4 on ULN2003 driver                     |
| Bluetooth (SerialBT)      | Internal       | Uses built-in BluetoothSerial (SPP profile)            |
| GND                       | GND            | Shared ground for all components                       |
| Power Supply (OLED)       | 3.3V or 5V     | Depending on OLED module specs                         |
| Power Supply (Stepper)    | 5V             | External power recommended for stable motor operation  |

## 🧠 Notes

- The stepper motor used is **28BYJ-48** with **ULN2003 driver**.
- The display is **SSD1306 128x64 I2C**, handled via the **U8g2** library.
- EEPROM stores:
    - Rotation mode: `C` (Continuous) or `T` (1 full turn)
    - Direction: `CW` or `CCW`
    - Speed: from `1.0` to `5.0` RPM (float)
- Gear ratio: `63.68 / 17.0` → ≈ **15340 steps** per platform rotation.
- OLED animation:
    - Speed varies with `targetRPM`
    - Direction matches `CW` or `CCW`
- SelfTest shows icons, popups, and tests rotation with different speeds/directions.
- Commands are received via **Bluetooth SPP** from the Android app.
  
- 
- © 2025. Built with ❤️ by [Žarko Vargović, Dubrava232, Zagreb, Croatia]
