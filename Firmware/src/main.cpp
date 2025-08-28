#include <Arduino.h>
#include <Wire.h>
#include <U8g2lib.h>
#include <EEPROM.h>
#include <BluetoothSerial.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "animated_icon.h"
#include "icons.h"

BluetoothSerial SerialBT;

#define EEPROM_SIZE 16
#define EEPROM_ADDR_SPEED 0
#define EEPROM_ADDR_DIR 4
#define EEPROM_ADDR_MODE 8

float targetRPM = 3.0;
int stepDelay = 1000;

const unsigned char* popupIcon = nullptr;
unsigned long popupUntil = 0;
void showPopupIcon(const unsigned char* icon);
void selfTest();

void showPopupIcon(const unsigned char* icon) {
    popupIcon = icon;
    popupUntil = millis() + 2000;  // 2 sekunde
}

U8G2_SSD1306_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, U8X8_PIN_NONE);

#define IN1 27
#define IN2 26
#define IN3 25
#define IN4 33

const int stepsPerRevolution = 4096;
const float gearRatio = 63.68 / 17.0;
const int oneTurnSteps = round(stepsPerRevolution * gearRatio);
const int contTurns = 5000; // effectively "very long" continuous run

const int stepSequence[8][4] = {
        {1, 0, 0, 0}, {1, 1, 0, 0}, {0, 1, 0, 0}, {0, 1, 1, 0},
        {0, 0, 1, 0}, {0, 0, 1, 1}, {0, 0, 0, 1}, {1, 0, 0, 1}
};

int currentStep = 0;
int currentDirection = 1;
char rotationMode = 'T';
bool isRunning = false;
bool stopRequested = false;
long remainingSteps = 0;
bool flashState = false;
unsigned long lastFlash = 0;
bool btPreviouslyConnected = false;
bool inSelfTest = false;
int animFrame = 0;
unsigned long lastAnimUpdate = 0;

void drawPersistentStatusIcons() {
    u8g2.clearBuffer();
    u8g2.drawFrame(4, 2, 38, 60);
    u8g2.drawXBMP(8, 8, 32, 32, (rotationMode == 'C') ? iconC32 : iconTC32);
    u8g2.setFont(u8g2_font_7x14B_tf);
    const char* modeText = (rotationMode == 'C') ? "C" : "T";
    u8g2.setCursor(4 + (38 - u8g2.getUTF8Width(modeText)) / 2, 56);
    u8g2.print(modeText);

    u8g2.drawFrame(44, 2, 38, 60);
    u8g2.drawXBMP(48, 8, 32, 32, (currentDirection == 1) ? iconCW32 : iconCCW32);
    u8g2.setFont(u8g2_font_7x14B_tf);
    const char* dirText = (currentDirection == 1) ? "CW" : "CCW";
    u8g2.setCursor(44 + (38 - u8g2.getUTF8Width(dirText)) / 2, 56);
    u8g2.print(dirText);

    u8g2.drawFrame(84, 2, 38, 60);
    u8g2.drawXBMP(88, 8, 32, 32, iconS32);
    u8g2.setFont(u8g2_font_7x14B_tf);
    char speedStr[6];
    dtostrf(targetRPM, 2, 1, speedStr);
    strcat(speedStr, "X");
    u8g2.setCursor(84 + (38 - u8g2.getUTF8Width(speedStr)) / 2, 56);
    u8g2.print(speedStr);

    u8g2.sendBuffer();
}

void applyStep(int step) {
    digitalWrite(IN1, stepSequence[step][0]);
    digitalWrite(IN2, stepSequence[step][1]);
    digitalWrite(IN3, stepSequence[step][2]);
    digitalWrite(IN4, stepSequence[step][3]);
}

void turnOffCoils() {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, LOW);
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, LOW);
}

void showReadyScreen() {
    u8g2.begin();
    Serial.println("Ready - waiting for commands");
    SerialBT.println("Ready - waiting for commands");
}

void sendStatus() {
    String status = "STATUS Speed:" + String(targetRPM, 2) +
                    " Dir:" + (currentDirection == 1 ? "CW" : "CCW") +
                    " Mode:" + String(rotationMode) +
                    " Running:" + (isRunning ? "1" : "0");
    SerialBT.println(status);
    Serial.println(status);
}

void handleInput(String input) {
    input.trim();
    if (input.startsWith("SetDirection")) {
        String dir = input.substring(12); dir.trim(); dir.toUpperCase();
        if (dir == "CW") currentDirection = 1;
        else if (dir == "CCW") currentDirection = -1;
        EEPROM.put(EEPROM_ADDR_DIR, currentDirection); EEPROM.commit();
        showPopupIcon((currentDirection == 1) ? iconCW : iconCCW);
        showReadyScreen(); sendStatus(); drawPersistentStatusIcons();

    } else if (input.startsWith("ModeRun")) {
        char mode = toupper(input.charAt(7));
        if (mode == 'C' || mode == 'T') {
            rotationMode = mode;
            EEPROM.put(EEPROM_ADDR_MODE, rotationMode); EEPROM.commit();
            showPopupIcon((rotationMode == 'C') ? iconC : iconT);
            showReadyScreen(); sendStatus(); drawPersistentStatusIcons();
        }

    } else if (input.startsWith("SetSpeed")) {
        String spd = input.substring(8); spd.trim();
        float rpm = spd.toFloat();
        if (rpm >= 1.0 && rpm <= 5.0) {
            targetRPM = rpm;
            EEPROM.put(EEPROM_ADDR_SPEED, targetRPM); EEPROM.commit();
            showPopupIcon(iconSPEED);
            showReadyScreen(); sendStatus(); drawPersistentStatusIcons();
        }

    } else if (input == "RotateStart" || input == "Play") {
        stopRequested = false;
        if (!isRunning) {
            stepDelay = 60L * 1000000L / (oneTurnSteps * targetRPM);
            if (stepDelay < 100) stepDelay = 100;
            remainingSteps = (rotationMode == 'T') ? oneTurnSteps : oneTurnSteps * contTurns;
            isRunning = true;
            sendStatus();
        }

    } else if (input == "Stop") {
        stopRequested = true;

    } else if (input == "ShowScreen") {
        delay(10000); showReadyScreen();

    } else if (input == "SelfTest") {
        Serial.println("Pokrećem SelfTest...");
        selfTest();

    } else if (input == "GetStatus" || input == "SendStatus" || input == "STATUS?") {
        sendStatus();
    }
}

void motorTask(void *pvParameters) {
    while (true) {
        if (isRunning && remainingSteps > 0) {
            currentStep = (currentStep - currentDirection + 8) % 8;
            applyStep(currentStep);
            remainingSteps--;
            if (stopRequested || remainingSteps == 0) {
                isRunning = false;
                stopRequested = false;
                turnOffCoils();
                showReadyScreen();
                drawPersistentStatusIcons();
                sendStatus();
            }
            delayMicroseconds(stepDelay);
        } else {
            vTaskDelay(1);
        }
    }
}

void setup() {
    EEPROM.begin(EEPROM_SIZE);
    EEPROM.get(EEPROM_ADDR_SPEED, targetRPM);
    if (isnan(targetRPM) || targetRPM < 1.0 || targetRPM > 5.0) {
        targetRPM = 3.0;
        EEPROM.put(EEPROM_ADDR_SPEED, targetRPM);
    }
    EEPROM.get(EEPROM_ADDR_DIR, currentDirection);
    if (currentDirection != 1 && currentDirection != -1) {
        currentDirection = 1;
        EEPROM.put(EEPROM_ADDR_DIR, currentDirection);
    }
    EEPROM.get(EEPROM_ADDR_MODE, rotationMode);
    if (rotationMode != 'C' && rotationMode != 'T') {
        rotationMode = 'C'; // default to Continuous so motor keeps running unless told otherwise
        EEPROM.put(EEPROM_ADDR_MODE, rotationMode);
    }
    EEPROM.commit();

    Wire.begin(16, 17);
    Serial.begin(115200);
    SerialBT.begin("TurningTable");
    u8g2.begin();
    pinMode(IN1, OUTPUT); pinMode(IN2, OUTPUT);
    pinMode(IN3, OUTPUT); pinMode(IN4, OUTPUT);
    stepDelay = 1000;
    xTaskCreatePinnedToCore(motorTask, "motorTask", 2048, NULL, 1, NULL, 1);
    showReadyScreen();
}

void loop() {
    // Edge-detect BT connection state and always emit STATUS on (re)connect
    bool has = SerialBT.hasClient();
    if (has && !btPreviouslyConnected) {
        Serial.println("BT connected — sending status");
        btPreviouslyConnected = true;
        sendStatus();
        drawPersistentStatusIcons();
    } else if (!has && btPreviouslyConnected) {
        btPreviouslyConnected = false;
    }

    if (inSelfTest) return;

    if (popupIcon != nullptr) {
        if (millis() < popupUntil) {
            u8g2.clearBuffer();
            u8g2.drawFrame(3, 3, 122, 58);
            u8g2.drawXBMP(12, 8, 48, 48, popupIcon);

            u8g2.setFont(u8g2_font_fur17_tf);
            const char* label = "";

            if (popupIcon == iconCW) label = "CW";
            else if (popupIcon == iconCCW) label = "CCW";
            else if (popupIcon == iconC) label = "C";
            else if (popupIcon == iconT) label = "T";
            else if (popupIcon == iconSPEED) {
                static char speedStr[6];
                dtostrf(targetRPM, 2, 1, speedStr);
                strcat(speedStr, "X");
                label = speedStr;
            }

            int textWidth = u8g2.getUTF8Width(label);
            int textX = 12 + 48 + (59 - textWidth) / 2;
            u8g2.setCursor(textX, 36);
            u8g2.print(label);
            u8g2.sendBuffer();
            return;
        } else {
            popupIcon = nullptr;
            drawPersistentStatusIcons();
        }
    }

    static String inputSerial = "";
    static String inputBT = "";

    while (Serial.available()) {
        char c = Serial.read();
        if (c == '\n' || c == '\r') {
            Serial.print("SERIAL received: "); Serial.println(inputSerial);
            handleInput(inputSerial); inputSerial = "";
        } else inputSerial += c;
    }

    while (SerialBT.available()) {
        char c = SerialBT.read();
        if (c == '\n' || c == '\r') {
            Serial.print("BT received: "); Serial.println(inputBT);
            handleInput(inputBT); inputBT = "";
        } else inputBT += c;
    }

    if (isRunning) {
        int animDelay = map((int)(targetRPM * 10), 10, 50, 300, 60);
        if (millis() - lastAnimUpdate > animDelay) {
            u8g2.clearBuffer();
            u8g2.drawFrame(14, 14, 100, 48);
            u8g2.drawXBMP(48, 20, 32, 32, epd_bitmap_allArray[animFrame]);
            u8g2.sendBuffer();

            // Advance or reverse animation frame depending on direction
            if (currentDirection == 1) {
                animFrame = (animFrame + 1) % epd_bitmap_allArray_LEN;
            } else {
                animFrame = (animFrame - 1 + epd_bitmap_allArray_LEN) % epd_bitmap_allArray_LEN;
            }

            lastAnimUpdate = millis();
        }
        return;
    } else {
        if (!SerialBT.hasClient()) {
            // Disconnected: flash BT icon while idle
            if (millis() - lastFlash > 500) {
                flashState = !flashState;
                popupIcon = nullptr;
                u8g2.clearBuffer();
                if (flashState) {
                    u8g2.drawXBMP(48, 12, 32, 32, epd_bitmap_bluetooth32x32);
                }
                u8g2.sendBuffer();
                lastFlash = millis();
            }
        } else {
            // Connected: nothing to do here; connect edge handled at top of loop()
        }
    }
}

void selfTest() {
    inSelfTest = true;

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_7x14B_tf);
    const char* msg = "Test started";
    int textX = (128 - u8g2.getUTF8Width(msg)) / 2;
    u8g2.setCursor(textX, 36);
    u8g2.print(msg);
    u8g2.sendBuffer();
    delay(2000);

    float speeds[] = {5.0, 4.0, 3.0, 2.0, 1.0};
    int dirs[] = {1, -1};

    for (int i = 0; i < 5; i++) {
        targetRPM = speeds[i];
        currentDirection = (i % 2 == 0) ? 1 : -1;

        drawPersistentStatusIcons();  // Show updated status icons
        showPopupIcon(iconSPEED);
        delay(2000);

        long segmentSteps = oneTurnSteps / 8;
        for (int s = 0; s < speeds[i]; s++) {
            for (long j = 0; j < segmentSteps; j++) {
                currentStep = (currentStep + currentDirection + 8) % 8;
                applyStep(currentStep);
                delayMicroseconds(60L * 1000000L / (oneTurnSteps * speeds[i]));
            }
        }
    }

    const unsigned char* icons[] = { iconC, iconT, iconCW, iconCCW, iconSPEED };
    for (int i = 0; i < 5; i++) {
        u8g2.clearBuffer();
        u8g2.drawFrame(3, 3, 122, 58);
        u8g2.drawXBMP(12, 8, 48, 48, icons[i]);
        u8g2.sendBuffer();
        delay(2000);
    }

    for (int i = 0; i < 5; i++) {
        showPopupIcon(icons[i]);
        delay(2000);
    }

    unsigned long startFlash = millis();
    while (millis() - startFlash < 5000) {
        u8g2.clearBuffer();
        if ((millis() / 500) % 2 == 0)
            u8g2.drawXBMP(48, 12, 32, 32, iconBT);
        u8g2.sendBuffer();
        delay(200);
    }

    u8g2.clearBuffer();
    msg = "SelfTest finished";
    textX = (128 - u8g2.getUTF8Width(msg)) / 2;
    u8g2.setCursor(textX, 36);
    u8g2.print(msg);
    u8g2.sendBuffer();
    delay(2000);

    popupIcon = nullptr;
    popupUntil = 0;
    inSelfTest = false;
    drawPersistentStatusIcons();
}