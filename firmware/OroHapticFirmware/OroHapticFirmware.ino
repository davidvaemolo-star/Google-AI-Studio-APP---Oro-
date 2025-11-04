/*
 * Oro Haptic Paddle Firmware
 * Hardware: Seeed XIAO nRF52840 Sense
 * Haptic Driver: DRV2605L (I2C address 0x5A)
 * IMU Sensor: LSM6DS3 (built-in on XIAO Sense)
 *
 * BLE Services:
 * - Oro Haptic Service: Custom service for training control
 * - Battery Service: Standard 0x180F for battery monitoring
 *
 * Pin Map (IMMUTABLE):
 * - I2C: SDA=D4 (pin 4), SCL=D5 (pin 5)
 * - Battery Monitor: A0 (analog pin for voltage divider)
 *
 * Author: Oro Development Team
 * Date: 2025-11-02
 */

#include <bluefruit.h>
#include <Wire.h>
#include <Adafruit_DRV2605.h>
#include "LSM6DS3.h"  // Use Seeed_Arduino_LSM6DS3 library

// ============================================================================
// HARDWARE CONFIGURATION
// ============================================================================

// I2C Pins (XIAO nRF52840)
#define SDA_PIN 4  // D4
#define SCL_PIN 5  // D5

// Battery Monitoring
#define BATTERY_PIN A0
#define BATTERY_READ_INTERVAL 30000  // Read every 30 seconds

// DRV2605L Configuration
Adafruit_DRV2605 drv;

// LSM6DS3 IMU Configuration (built-in on XIAO Sense, I2C address 0x6A)
LSM6DS3 imu(I2C_MODE, 0x6A);

// IMU Stroke Detection Settings
#define IMU_SAMPLE_RATE_HZ 104       // 104 Hz sampling rate
#define STROKE_DETECT_THRESHOLD 1.0  // Acceleration threshold in g (based on real paddle data: peak ~1.83g, using 55%)
#define STROKE_MIN_INTERVAL_MS 200   // Minimum time between strokes (prevents double-counting)
#define CALIBRATION_SAMPLES 10      // Number of samples for calibration

// ============================================================================
// BLE SERVICE AND CHARACTERISTIC UUIDs
// ============================================================================

// Oro Haptic Service UUID: 12340000-1234-5678-1234-56789abcdef0
#define ORO_HAPTIC_SERVICE_UUID "12340000-1234-5678-1234-56789abcdef0"

// Oro Characteristic UUIDs:
#define HAPTIC_CONTROL_CHAR_UUID    "12340001-1234-5678-1234-56789abcdef0"  // Write - trigger haptic patterns
#define ZONE_SETTINGS_CHAR_UUID     "12340002-1234-5678-1234-56789abcdef0"  // Write - training zone config
#define DEVICE_STATUS_CHAR_UUID     "12340003-1234-5678-1234-56789abcdef0"  // Read/Notify - device state
#define CONNECTION_STATUS_CHAR_UUID "12340004-1234-5678-1234-56789abcdef0"  // Read/Notify - connection state
#define STROKE_EVENT_CHAR_UUID      "12340005-1234-5678-1234-56789abcdef0"  // Notify - stroke detection events
#define CALIBRATION_CHAR_UUID       "12340006-1234-5678-1234-56789abcdef0"  // Write/Notify - calibration control

// Standard Battery Service
#define BATTERY_SERVICE_UUID        "180F"
#define BATTERY_LEVEL_CHAR_UUID     "2A19"

// ============================================================================
// BLE SERVICES AND CHARACTERISTICS
// ============================================================================

BLEService oroHapticService = BLEService(ORO_HAPTIC_SERVICE_UUID);
BLEService batteryService = BLEService(BATTERY_SERVICE_UUID);

// Haptic Control: Write only
// Format: [command(1 byte)][intensity(1 byte)][duration_ms(2 bytes)][pattern(1 byte)]
BLECharacteristic hapticControlChar = BLECharacteristic(HAPTIC_CONTROL_CHAR_UUID);

// Zone Settings: Write only
// Format: [strokes(2 bytes)][sets(1 byte)][spm(2 bytes)][zone_color(1 byte)]
BLECharacteristic zoneSettingsChar = BLECharacteristic(ZONE_SETTINGS_CHAR_UUID);

// Device Status: Read + Notify
// Format: [state(1 byte)][current_stroke(2 bytes)][current_set(1 byte)][battery(1 byte)]
BLECharacteristic deviceStatusChar = BLECharacteristic(DEVICE_STATUS_CHAR_UUID);

// Connection Status: Read + Notify
// Format: [connected(1 byte)][rssi(1 byte signed)]
BLECharacteristic connectionStatusChar = BLECharacteristic(CONNECTION_STATUS_CHAR_UUID);

// Battery Level: Read + Notify (standard format: 0-100%)
BLECharacteristic batteryLevelChar = BLECharacteristic(BATTERY_LEVEL_CHAR_UUID);

// Stroke Event: Notify only
// Format: [stroke_phase(1 byte)][timestamp_ms(4 bytes)][accel_magnitude(2 bytes float16)]
BLECharacteristic strokeEventChar = BLECharacteristic(STROKE_EVENT_CHAR_UUID);

// Calibration: Write + Notify
// Format: [command(1 byte)][threshold(2 bytes float16)][status(1 byte)]
BLECharacteristic calibrationChar = BLECharacteristic(CALIBRATION_CHAR_UUID);

// ============================================================================
// DEVICE STATE MANAGEMENT
// ============================================================================

// Device States
enum DeviceState {
  STATE_IDLE = 0x00,
  STATE_READY = 0x01,
  STATE_TRAINING = 0x02,
  STATE_PAUSED = 0x03,
  STATE_COMPLETE = 0x04,
  STATE_CALIBRATING = 0x05,
  STATE_ERROR = 0xFF
};

// Haptic Commands
enum HapticCommand {
  CMD_STOP = 0x00,
  CMD_SINGLE_PULSE = 0x01,
  CMD_START_TRAINING = 0x02,
  CMD_PAUSE_TRAINING = 0x03,
  CMD_RESUME_TRAINING = 0x04,
  CMD_COMPLETE_TRAINING = 0x05,
  CMD_TEST_PATTERN = 0x06
};

// Calibration Commands
enum CalibrationCommand {
  CAL_CMD_START = 0x01,
  CAL_CMD_STOP = 0x02,
  CAL_CMD_SET_THRESHOLD = 0x03,
  CAL_CMD_GET_STATUS = 0x04
};

// Stroke Phases
enum StrokePhase {
  STROKE_PHASE_CATCH = 0x01,    // Start of stroke (paddle entry/catch)
  STROKE_PHASE_DRIVE = 0x02,    // Power phase (drive/pull)
  STROKE_PHASE_FINISH = 0x03,   // End of stroke (finish/extraction)
  STROKE_PHASE_RECOVERY = 0x04  // Return to catch position
};

// Haptic Patterns (DRV2605L effect library)
enum HapticPattern {
  PATTERN_STRONG_CLICK = 1,      // Sharp single click
  PATTERN_SHARP_CLICK = 2,       // Medium click
  PATTERN_SOFT_CLICK = 3,        // Gentle click
  PATTERN_DOUBLE_CLICK = 12,     // Two quick clicks
  PATTERN_TRIPLE_CLICK = 13,     // Three quick clicks
  PATTERN_PULSING = 47,          // Continuous pulse
  PATTERN_TRANSITION = 51,       // Smooth transition
  PATTERN_ALERT_750MS = 24       // Long alert
};

// Training Configuration
struct TrainingConfig {
  uint16_t totalStrokes;
  uint8_t totalSets;
  uint16_t strokesPerMinute;
  uint8_t zoneColor;
  bool isActive;
};

// Current Training State
struct TrainingState {
  DeviceState deviceState;
  uint16_t currentStroke;
  uint8_t currentSet;
  uint8_t batteryLevel;
  unsigned long lastStrokeTime;
  unsigned long strokeInterval;  // Calculated from SPM
};

TrainingConfig trainingConfig = {0, 0, 0, 0, false};
TrainingState trainingState = {STATE_IDLE, 0, 0, 100, 0, 0};

// Stroke Detection State
struct StrokeDetectionState {
  bool enabled;
  float threshold;               // Acceleration threshold in g
  StrokePhase currentPhase;
  unsigned long lastStrokeTime;
  float maxAccel;                // Peak acceleration during current stroke
  float minAccel;                // Minimum (most negative) during recovery
  bool inStroke;                 // Currently in a stroke cycle
};

StrokeDetectionState strokeDetection = {
  false,                         // disabled by default
  STROKE_DETECT_THRESHOLD,       // default threshold
  STROKE_PHASE_RECOVERY,         // start in recovery phase
  0,                             // no strokes yet
  0.0,                           // no peak yet
  0.0,                           // no minimum yet
  false                          // not in stroke
};

// Calibration State
struct CalibrationState {
  bool active;
  uint8_t sampleCount;
  float maxAccelSeen;
  float minAccelSeen;
};

CalibrationState calibrationState = {false, 0, 0.0, 0.0};

// Battery monitoring
unsigned long lastBatteryRead = 0;
uint8_t lastBatteryLevel = 100;

// Device name with BLE address suffix
String deviceName = "Oro-0000";

// ============================================================================
// SETUP FUNCTIONS
// ============================================================================

void setup() {
  Serial.begin(115200);
  delay(2000);  // Wait for serial monitor

  Serial.println("=== Oro Haptic Paddle Firmware ===");
  Serial.println("Hardware: XIAO nRF52840 Sense + DRV2605L");
  Serial.println();

  // Initialize I2C with custom pins
  Wire.begin();
  Wire.setClock(400000);  // 400kHz I2C

  // Initialize DRV2605L
  if (!initializeDRV2605L()) {
    Serial.println("ERROR: Failed to initialize DRV2605L");
    trainingState.deviceState = STATE_ERROR;
    while(1) { delay(1000); }  // Halt on critical error
  }

  // Initialize LSM6DS3 IMU
  if (!initializeIMU()) {
    Serial.println("ERROR: Failed to initialize IMU");
    trainingState.deviceState = STATE_ERROR;
    while(1) { delay(1000); }  // Halt on critical error
  }

  // Initialize BLE
  if (!initializeBLE()) {
    Serial.println("ERROR: Failed to initialize BLE");
    trainingState.deviceState = STATE_ERROR;
    while(1) { delay(1000); }
  }

  // Initialize battery monitoring
  pinMode(BATTERY_PIN, INPUT);
  updateBatteryLevel();

  // System ready
  trainingState.deviceState = STATE_READY;
  Serial.println("System initialized successfully");
  Serial.println("Device name: " + deviceName);
  Serial.println("Ready for BLE connections");
  Serial.println();

  // Enable stroke detection by default for testing
  strokeDetection.enabled = true;
  Serial.println("Stroke detection ENABLED");
  Serial.print("Current threshold: ");
  Serial.print(strokeDetection.threshold, 2);
  Serial.println("g");

  // Play startup haptic
  playHapticEffect(PATTERN_DOUBLE_CLICK, 100);
}

bool initializeDRV2605L() {
  Serial.println("Initializing DRV2605L haptic driver...");

  // Scan I2C bus
  Serial.print("Scanning I2C bus... ");
  Wire.beginTransmission(0x5A);
  if (Wire.endTransmission() != 0) {
    Serial.println("NOT FOUND at 0x5A");
    return false;
  }
  Serial.println("FOUND at 0x5A");

  // Initialize driver
  if (!drv.begin()) {
    Serial.println("Failed to initialize DRV2605L driver");
    return false;
  }

  // Configure for ERM motor
  drv.selectLibrary(1);  // ERM library
  drv.setMode(DRV2605_MODE_INTTRIG);  // Internal trigger mode

  Serial.println("DRV2605L initialized successfully");
  return true;
}

bool initializeIMU() {
  Serial.println("Initializing LSM6DS3 IMU...");

  // Initialize IMU with default settings
  uint8_t result = imu.begin();
  Serial.print("IMU begin() returned: ");
  Serial.println(result);

  if (result != 0) {
    Serial.println("Failed to initialize LSM6DS3");
    Serial.println("Check I2C connections and address (0x6A)");
    return false;
  }

  Serial.println("LSM6DS3 initialized successfully");

  // Test read to verify IMU is working
  float testX = imu.readFloatAccelX();
  float testY = imu.readFloatAccelY();
  float testZ = imu.readFloatAccelZ();

  Serial.print("Test reading - X: ");
  Serial.print(testX, 3);
  Serial.print("g, Y: ");
  Serial.print(testY, 3);
  Serial.print("g, Z: ");
  Serial.print(testZ, 3);
  Serial.println("g");

  return true;
}

bool initializeBLE() {
  Serial.println("Initializing BLE...");

  // Initialize Bluefruit
  Bluefruit.begin();

  // Generate device name with last 4 chars of BLE address
  char addressStr[18];
  uint8_t mac[6];
  Bluefruit.getAddr(mac);
  sprintf(addressStr, "%02X%02X%02X%02X%02X%02X", mac[5], mac[4], mac[3], mac[2], mac[1], mac[0]);
  String address = String(addressStr);
  deviceName = "Oro-" + address.substring(address.length() - 4);

  Bluefruit.setName(deviceName.c_str());

  // Set max power for better range
  Bluefruit.setTxPower(4);  // Max +4dBm

  // Set connection parameters for low latency (7.5ms - 20ms)
  Bluefruit.Periph.setConnInterval(6, 16);  // Units of 1.25ms

  // Configure Oro Haptic Service
  oroHapticService.begin();

  // Haptic Control Characteristic (Write)
  hapticControlChar.setProperties(CHR_PROPS_WRITE);
  hapticControlChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  hapticControlChar.setFixedLen(5);
  hapticControlChar.setWriteCallback(onHapticControlWrite);
  hapticControlChar.begin();

  // Zone Settings Characteristic (Write)
  zoneSettingsChar.setProperties(CHR_PROPS_WRITE);
  zoneSettingsChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  zoneSettingsChar.setFixedLen(6);
  zoneSettingsChar.setWriteCallback(onZoneSettingsWrite);
  zoneSettingsChar.begin();

  // Device Status Characteristic (Read + Notify)
  deviceStatusChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  deviceStatusChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  deviceStatusChar.setFixedLen(5);
  deviceStatusChar.begin();

  // Connection Status Characteristic (Read + Notify)
  connectionStatusChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  connectionStatusChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  connectionStatusChar.setFixedLen(2);
  connectionStatusChar.begin();

  // Stroke Event Characteristic (Notify only)
  strokeEventChar.setProperties(CHR_PROPS_NOTIFY);
  strokeEventChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  strokeEventChar.setFixedLen(7);  // 1 byte phase + 4 bytes timestamp + 2 bytes accel
  strokeEventChar.begin();

  // Calibration Characteristic (Write + Notify)
  calibrationChar.setProperties(CHR_PROPS_WRITE | CHR_PROPS_NOTIFY);
  calibrationChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  calibrationChar.setFixedLen(4);  // 1 byte command + 2 bytes threshold + 1 byte status
  calibrationChar.setWriteCallback(onCalibrationWrite);
  calibrationChar.begin();

  // Configure Battery Service
  batteryService.begin();

  // Battery Level Characteristic (Read + Notify)
  batteryLevelChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  batteryLevelChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  batteryLevelChar.setFixedLen(1);
  batteryLevelChar.begin();

  // Set initial characteristic values
  updateDeviceStatus();
  updateConnectionStatus();
  batteryLevelChar.write8(trainingState.batteryLevel);

  // Set connection callbacks
  Bluefruit.Periph.setConnectCallback(onBLEConnected);
  Bluefruit.Periph.setDisconnectCallback(onBLEDisconnected);

  // Start advertising
  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();
  Bluefruit.Advertising.addService(oroHapticService);
  Bluefruit.Advertising.addName();

  // Set advertising interval (fast mode: 20ms, slow mode: 152.5ms)
  Bluefruit.Advertising.restartOnDisconnect(true);
  Bluefruit.Advertising.setInterval(32, 244);  // Units of 0.625ms
  Bluefruit.Advertising.setFastTimeout(30);    // Fast mode for 30 seconds
  Bluefruit.Advertising.start(0);  // 0 = Don't stop advertising

  Serial.println("BLE initialized successfully");
  Serial.println("Advertising as: " + deviceName);

  return true;
}

// ============================================================================
// MAIN LOOP
// ============================================================================

void loop() {
  // Bluefruit handles BLE automatically, no need to poll

  // Update battery level periodically
  if (millis() - lastBatteryRead >= BATTERY_READ_INTERVAL) {
    updateBatteryLevel();
    lastBatteryRead = millis();
  }

  // Handle stroke detection (if enabled)
  if (strokeDetection.enabled || calibrationState.active) {
    handleStrokeDetection();
  }

  // Handle training loop (time-based mode - deprecated in favor of IMU)
  if (trainingState.deviceState == STATE_TRAINING && trainingConfig.isActive && !strokeDetection.enabled) {
    handleTrainingLoop();
  }

  // Small delay to prevent tight loop
  delay(1);
}

// ============================================================================
// TRAINING LOGIC
// ============================================================================

void handleTrainingLoop() {
  unsigned long currentTime = millis();

  // Check if it's time for next stroke
  if (currentTime - trainingState.lastStrokeTime >= trainingState.strokeInterval) {
    // Trigger haptic pulse
    playHapticEffect(PATTERN_STRONG_CLICK, 100);

    // Update stroke count
    trainingState.currentStroke++;
    trainingState.lastStrokeTime = currentTime;

    // Update device status
    updateDeviceStatus();

    // Check if set is complete
    if (trainingState.currentStroke >= trainingConfig.totalStrokes) {
      trainingState.currentStroke = 0;
      trainingState.currentSet++;

      // Check if all sets complete
      if (trainingState.currentSet >= trainingConfig.totalSets) {
        completeTraining();
      } else {
        // Play transition pattern between sets
        delay(50);
        playHapticEffect(PATTERN_DOUBLE_CLICK, 80);
      }
    }

    // Print progress
    Serial.print("Set: ");
    Serial.print(trainingState.currentSet + 1);
    Serial.print("/");
    Serial.print(trainingConfig.totalSets);
    Serial.print(" | Stroke: ");
    Serial.print(trainingState.currentStroke);
    Serial.print("/");
    Serial.print(trainingConfig.totalStrokes);
    Serial.print(" | SPM: ");
    Serial.println(trainingConfig.strokesPerMinute);
  }
}

void startTraining() {
  if (!trainingConfig.isActive) {
    Serial.println("ERROR: Cannot start training - no zone configured");
    return;
  }

  Serial.println("=== Starting Training ===");
  Serial.print("Strokes: ");
  Serial.print(trainingConfig.totalStrokes);
  Serial.print(" | Sets: ");
  Serial.print(trainingConfig.totalSets);
  Serial.print(" | SPM: ");
  Serial.println(trainingConfig.strokesPerMinute);

  // Enable stroke detection (IMU mode)
  strokeDetection.enabled = true;
  Serial.println("Stroke detection ENABLED");

  // Calculate stroke interval from SPM (for time-based fallback)
  // SPM = strokes per minute, so interval = 60000ms / SPM
  trainingState.strokeInterval = (60000UL / trainingConfig.strokesPerMinute);

  // Reset training state
  trainingState.currentStroke = 0;
  trainingState.currentSet = 0;
  trainingState.lastStrokeTime = millis();
  trainingState.deviceState = STATE_TRAINING;

  // Play start pattern
  playHapticEffect(PATTERN_TRIPLE_CLICK, 100);

  updateDeviceStatus();
}

void pauseTraining() {
  Serial.println("Training paused");
  trainingState.deviceState = STATE_PAUSED;
  playHapticEffect(PATTERN_SOFT_CLICK, 80);
  updateDeviceStatus();
}

void resumeTraining() {
  Serial.println("Training resumed");
  trainingState.deviceState = STATE_TRAINING;
  trainingState.lastStrokeTime = millis();  // Reset timing
  playHapticEffect(PATTERN_DOUBLE_CLICK, 80);
  updateDeviceStatus();
}

void completeTraining() {
  Serial.println("=== Training Complete ===");
  trainingState.deviceState = STATE_COMPLETE;
  trainingConfig.isActive = false;

  // Play completion pattern
  playHapticEffect(PATTERN_ALERT_750MS, 100);
  delay(800);
  playHapticEffect(PATTERN_TRIPLE_CLICK, 80);

  updateDeviceStatus();
}

void stopTraining() {
  Serial.println("Training stopped");
  trainingState.deviceState = STATE_READY;
  trainingState.currentStroke = 0;
  trainingState.currentSet = 0;
  trainingConfig.isActive = false;

  playHapticEffect(PATTERN_SOFT_CLICK, 60);
  updateDeviceStatus();
}

// ============================================================================
// HAPTIC CONTROL
// ============================================================================

void playHapticEffect(uint8_t effect, uint8_t intensity) {
  // Map intensity (0-100) to DRV2605L range (0-255)
  uint8_t drvIntensity = map(intensity, 0, 100, 0, 255);

  // Set waveform
  drv.setWaveform(0, effect);
  drv.setWaveform(1, 0);  // End of waveform

  // Play effect
  drv.go();
}

void testHapticPattern(uint8_t pattern, uint8_t intensity) {
  Serial.print("Testing haptic pattern: ");
  Serial.print(pattern);
  Serial.print(" at intensity: ");
  Serial.println(intensity);

  playHapticEffect(pattern, intensity);
}

// ============================================================================
// BLE EVENT HANDLERS
// ============================================================================

void onBLEConnected(uint16_t conn_handle) {
  BLEConnection* connection = Bluefruit.Connection(conn_handle);

  // Get peer address
  ble_gap_addr_t peer_addr = connection->getPeerAddr();

  // Format address as string
  char addr_str[18];
  sprintf(addr_str, "%02X:%02X:%02X:%02X:%02X:%02X",
          peer_addr.addr[5], peer_addr.addr[4], peer_addr.addr[3],
          peer_addr.addr[2], peer_addr.addr[1], peer_addr.addr[0]);

  Serial.println("BLE device connected: " + String(addr_str));
  updateConnectionStatus();

  // Play connection haptic
  playHapticEffect(PATTERN_SOFT_CLICK, 60);
}

void onBLEDisconnected(uint16_t conn_handle, uint8_t reason) {
  Serial.println("BLE device disconnected, reason: 0x");
  Serial.println(reason, HEX);

  // Stop training if active
  if (trainingState.deviceState == STATE_TRAINING) {
    stopTraining();
  }

  updateConnectionStatus();

  // Play disconnection haptic
  playHapticEffect(PATTERN_SOFT_CLICK, 40);
}

void onHapticControlWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  // Format: [command(1)][intensity(1)][duration_ms(2)][pattern(1)]
  if (len < 1) {
    Serial.println("ERROR: Invalid haptic control data");
    return;
  }

  uint8_t command = data[0];
  uint8_t intensity = (len > 1) ? data[1] : 100;
  uint16_t duration = (len > 3) ? (data[2] | (data[3] << 8)) : 0;
  uint8_t pattern = (len > 4) ? data[4] : PATTERN_STRONG_CLICK;

  Serial.print("Haptic command: 0x");
  Serial.print(command, HEX);
  Serial.print(" | Intensity: ");
  Serial.print(intensity);
  Serial.print(" | Pattern: ");
  Serial.println(pattern);

  switch (command) {
    case CMD_STOP:
      stopTraining();
      break;

    case CMD_SINGLE_PULSE:
      playHapticEffect(pattern, intensity);
      break;

    case CMD_START_TRAINING:
      startTraining();
      break;

    case CMD_PAUSE_TRAINING:
      pauseTraining();
      break;

    case CMD_RESUME_TRAINING:
      resumeTraining();
      break;

    case CMD_COMPLETE_TRAINING:
      completeTraining();
      break;

    case CMD_TEST_PATTERN:
      testHapticPattern(pattern, intensity);
      break;

    default:
      Serial.println("ERROR: Unknown haptic command");
      break;
  }
}

void onZoneSettingsWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  // Format: [strokes(2)][sets(1)][spm(2)][zone_color(1)]
  if (len < 6) {
    Serial.println("ERROR: Invalid zone settings data");
    return;
  }

  trainingConfig.totalStrokes = data[0] | (data[1] << 8);
  trainingConfig.totalSets = data[2];
  trainingConfig.strokesPerMinute = data[3] | (data[4] << 8);
  trainingConfig.zoneColor = data[5];
  trainingConfig.isActive = true;

  Serial.println("=== Zone Settings Received ===");
  Serial.print("Strokes: ");
  Serial.println(trainingConfig.totalStrokes);
  Serial.print("Sets: ");
  Serial.println(trainingConfig.totalSets);
  Serial.print("SPM: ");
  Serial.println(trainingConfig.strokesPerMinute);
  Serial.print("Zone Color: 0x");
  Serial.println(trainingConfig.zoneColor, HEX);

  // Reset training state
  trainingState.currentStroke = 0;
  trainingState.currentSet = 0;
  trainingState.deviceState = STATE_READY;

  // Acknowledge with haptic
  playHapticEffect(PATTERN_DOUBLE_CLICK, 80);

  updateDeviceStatus();
}

// ============================================================================
// BLE STATUS UPDATES
// ============================================================================

void updateDeviceStatus() {
  // Format: [state(1)][current_stroke(2)][current_set(1)][battery(1)]
  uint8_t status[5];
  status[0] = trainingState.deviceState;
  status[1] = trainingState.currentStroke & 0xFF;
  status[2] = (trainingState.currentStroke >> 8) & 0xFF;
  status[3] = trainingState.currentSet;
  status[4] = trainingState.batteryLevel;

  deviceStatusChar.write(status, 5);
  if (Bluefruit.connected()) {
    deviceStatusChar.notify(status, 5);
  }
}

void updateConnectionStatus() {
  // Format: [connected(1)][rssi(1 signed)]
  uint8_t status[2];
  status[0] = Bluefruit.connected() ? 0x01 : 0x00;
  status[1] = 0;  // RSSI not easily accessible on nRF52, set to 0

  connectionStatusChar.write(status, 2);
  if (Bluefruit.connected()) {
    connectionStatusChar.notify(status, 2);
  }
}

// ============================================================================
// STROKE DETECTION AND CALIBRATION
// ============================================================================

void handleStrokeDetection() {
  // Read accelerometer data
  float accelX = imu.readFloatAccelX();
  float accelY = imu.readFloatAccelY();
  float accelZ = imu.readFloatAccelZ();

  // Calculate total acceleration magnitude (forward/backward axis - typically Y for rowing)
  // Using Y-axis as primary stroke direction
  float strokeAccel = accelY;

  // Debug: Print raw values every 100ms (roughly every 10 samples at 104Hz)
  static unsigned long lastDebugPrint = 0;
  if (!calibrationState.active && millis() - lastDebugPrint > 100) {
    Serial.print("Accel X=");
    Serial.print(accelX, 2);
    Serial.print("g, Y=");
    Serial.print(accelY, 2);
    Serial.print("g, Z=");
    Serial.print(accelZ, 2);
    Serial.print("g | Threshold=");
    Serial.print(strokeDetection.threshold, 2);
    Serial.println("g");
    lastDebugPrint = millis();
  }

  // Handle calibration mode
  if (calibrationState.active) {
    calibrationState.sampleCount++;

    if (strokeAccel > calibrationState.maxAccelSeen) {
      calibrationState.maxAccelSeen = strokeAccel;
    }
    if (strokeAccel < calibrationState.minAccelSeen) {
      calibrationState.minAccelSeen = strokeAccel;
    }

    // Show progress every 10 samples
    if (calibrationState.sampleCount % 10 == 0) {
      Serial.print("Calibration samples: ");
      Serial.print(calibrationState.sampleCount);
      Serial.print(" | Max: ");
      Serial.print(calibrationState.maxAccelSeen, 2);
      Serial.print("g | Min: ");
      Serial.print(calibrationState.minAccelSeen, 2);
      Serial.println("g");
    }

    // Auto-complete after enough samples
    if (calibrationState.sampleCount >= CALIBRATION_SAMPLES) {
      completeCalibration();
    }
    return;
  }

  // Stroke detection state machine
  unsigned long currentTime = millis();

  switch (strokeDetection.currentPhase) {
    case STROKE_PHASE_RECOVERY:
      // Waiting for catch - detect forward acceleration threshold
      if (strokeAccel > strokeDetection.threshold) {
        // Stroke catch detected!
        strokeDetection.currentPhase = STROKE_PHASE_CATCH;
        strokeDetection.maxAccel = strokeAccel;
        strokeDetection.inStroke = true;

        // Send stroke event
        sendStrokeEvent(STROKE_PHASE_CATCH, currentTime, strokeAccel);

        Serial.println("CATCH detected");
      }
      break;

    case STROKE_PHASE_CATCH:
      // Track peak acceleration during drive
      if (strokeAccel > strokeDetection.maxAccel) {
        strokeDetection.maxAccel = strokeAccel;
      }

      // Transition to drive when acceleration starts decreasing (from peak ~1.8g to ~1.2g)
      if (strokeAccel < strokeDetection.maxAccel * 0.65) {
        strokeDetection.currentPhase = STROKE_PHASE_DRIVE;
        sendStrokeEvent(STROKE_PHASE_DRIVE, currentTime, strokeAccel);
        Serial.println("DRIVE phase");
      }
      break;

    case STROKE_PHASE_DRIVE:
      // Detect finish - when acceleration crosses near zero (based on paddle data: 0 to -0.5g range)
      if (strokeAccel < 0.0) {
        strokeDetection.currentPhase = STROKE_PHASE_FINISH;
        strokeDetection.minAccel = strokeAccel;

        // Count this as a completed stroke
        trainingState.currentStroke++;
        updateDeviceStatus();

        // Send stroke event
        sendStrokeEvent(STROKE_PHASE_FINISH, currentTime, strokeAccel);

        Serial.print("FINISH - Stroke #");
        Serial.println(trainingState.currentStroke);

        // Update last stroke time
        strokeDetection.lastStrokeTime = currentTime;
      }
      break;

    case STROKE_PHASE_FINISH:
      // Track minimum (most negative) acceleration during recovery (expected ~-2.4g)
      if (strokeAccel < strokeDetection.minAccel) {
        strokeDetection.minAccel = strokeAccel;
      }

      // Return to recovery phase when acceleration returns toward positive (recovery ends around -0.5g to 0g)
      if (strokeAccel > -0.5) {
        strokeDetection.currentPhase = STROKE_PHASE_RECOVERY;
        strokeDetection.inStroke = false;
        sendStrokeEvent(STROKE_PHASE_RECOVERY, currentTime, strokeAccel);
        Serial.println("RECOVERY phase");
      }
      break;
  }
}

void sendStrokeEvent(StrokePhase phase, unsigned long timestamp, float accelMagnitude) {
  if (!Bluefruit.connected()) return;

  // Format: [phase(1)][timestamp_ms(4)][accel_magnitude(2 bytes as int16)]
  uint8_t data[7];
  data[0] = (uint8_t)phase;
  data[1] = (timestamp >> 0) & 0xFF;
  data[2] = (timestamp >> 8) & 0xFF;
  data[3] = (timestamp >> 16) & 0xFF;
  data[4] = (timestamp >> 24) & 0xFF;

  // Convert float to int16 (multiply by 100 to preserve 2 decimal places)
  int16_t accelInt = (int16_t)(accelMagnitude * 100.0);
  data[5] = (accelInt >> 0) & 0xFF;
  data[6] = (accelInt >> 8) & 0xFF;

  strokeEventChar.notify(data, 7);
}

void onCalibrationWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  if (len < 1) return;

  uint8_t command = data[0];

  switch (command) {
    case CAL_CMD_START:
      startCalibration();
      break;

    case CAL_CMD_STOP:
      stopCalibration();
      break;

    case CAL_CMD_SET_THRESHOLD:
      if (len >= 3) {
        // Read threshold from bytes 1-2 (int16 * 100)
        int16_t thresholdInt = data[1] | (data[2] << 8);
        strokeDetection.threshold = thresholdInt / 100.0;
        Serial.print("Threshold set to: ");
        Serial.print(strokeDetection.threshold, 2);
        Serial.println("g");

        // Acknowledge
        uint8_t response[4] = {CAL_CMD_SET_THRESHOLD, data[1], data[2], 0x01};
        calibrationChar.notify(response, 4);
      }
      break;

    case CAL_CMD_GET_STATUS:
      sendCalibrationStatus();
      break;
  }
}

void startCalibration() {
  Serial.println("=== Starting Calibration ===");
  Serial.println("Perform 50 strokes at various intensities...");

  calibrationState.active = true;
  calibrationState.sampleCount = 0;
  calibrationState.maxAccelSeen = -999.0;
  calibrationState.minAccelSeen = 999.0;

  trainingState.deviceState = STATE_CALIBRATING;
  updateDeviceStatus();

  // Play start haptic
  playHapticEffect(PATTERN_TRIPLE_CLICK, 100);

  sendCalibrationStatus();
}

void stopCalibration() {
  Serial.println("Calibration stopped");
  calibrationState.active = false;
  trainingState.deviceState = STATE_READY;
  updateDeviceStatus();

  playHapticEffect(PATTERN_SOFT_CLICK, 60);
  sendCalibrationStatus();
}

void completeCalibration() {
  Serial.println("=== Calibration Complete ===");

  // Calculate optimal threshold as 55% of max acceleration seen (based on real paddle data analysis)
  float suggestedThreshold = calibrationState.maxAccelSeen * 0.55;
  strokeDetection.threshold = suggestedThreshold;

  Serial.print("Max acceleration seen: ");
  Serial.print(calibrationState.maxAccelSeen, 2);
  Serial.println("g");
  Serial.print("Min acceleration seen: ");
  Serial.print(calibrationState.minAccelSeen, 2);
  Serial.println("g");
  Serial.print("Suggested threshold: ");
  Serial.print(suggestedThreshold, 2);
  Serial.println("g");

  calibrationState.active = false;
  trainingState.deviceState = STATE_READY;
  updateDeviceStatus();

  // Play completion haptic
  playHapticEffect(PATTERN_ALERT_750MS, 100);
  delay(800);
  playHapticEffect(PATTERN_DOUBLE_CLICK, 80);

  sendCalibrationStatus();
}

void sendCalibrationStatus() {
  if (!Bluefruit.connected()) return;

  // Format: [command=GET_STATUS][threshold(2)][status(1)]
  uint8_t data[4];
  data[0] = CAL_CMD_GET_STATUS;

  int16_t thresholdInt = (int16_t)(strokeDetection.threshold * 100.0);
  data[1] = (thresholdInt >> 0) & 0xFF;
  data[2] = (thresholdInt >> 8) & 0xFF;
  data[3] = calibrationState.active ? 0x01 : 0x00;

  calibrationChar.notify(data, 4);
}

// ============================================================================
// BATTERY MONITORING
// ============================================================================

void updateBatteryLevel() {
  // Read battery voltage via ADC
  // XIAO nRF52840 battery voltage divider: VBAT -> 1M -> ADC -> 510K -> GND
  // ADC reference: 3.6V (nRF52840 internal reference with gain 1/6)
  // Full scale ADC (1023) = 3.6V * 6 = 21.6V (theoretical max)
  // LiPo range: 4.2V (100%) to 3.0V (0%)

  int rawADC = analogRead(BATTERY_PIN);

  // Convert ADC to voltage
  // With internal 0.6V reference and 1/6 gain: Vmax = 3.6V
  // Battery divider: (1M + 510K) / 510K = 2.96:1
  float voltage = (rawADC / 1023.0) * 3.6 * 2.96;

  // Convert voltage to percentage (4.2V = 100%, 3.0V = 0%)
  float percentage = ((voltage - 3.0) / (4.2 - 3.0)) * 100.0;

  // Clamp to 0-100%
  if (percentage < 0) percentage = 0;
  if (percentage > 100) percentage = 100;

  uint8_t batteryLevel = (uint8_t)percentage;

  // Update only if changed by more than 1%
  if (abs(batteryLevel - lastBatteryLevel) > 1) {
    trainingState.batteryLevel = batteryLevel;
    lastBatteryLevel = batteryLevel;

    batteryLevelChar.write8(batteryLevel);
    if (Bluefruit.connected()) {
      batteryLevelChar.notify8(batteryLevel);
    }
    updateDeviceStatus();

    Serial.print("Battery: ");
    Serial.print(batteryLevel);
    Serial.print("% (");
    Serial.print(voltage, 2);
    Serial.println("V)");
  }
}
