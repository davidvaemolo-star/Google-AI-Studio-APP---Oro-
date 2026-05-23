# Firmware-driven LED state indicator; no BLE LED control characteristic

The RGB LED in the paddle t-handle reflects device state autonomously — the firmware maps its own `DeviceState` to a color/pulse pattern without any instruction from the Training Controller. The `LED_CONTROL_CHAR` BLE characteristic (previously `12340009`) has been removed.

The alternative — app-driven LED control via BLE — was rejected because the device must show meaningful state (advertising, error) before any BLE connection exists. Firmware ownership is the only approach that covers the full device lifecycle.

## Color scheme

| State | Color | Pattern |
|---|---|---|
| Advertising | Blue | Slow pulse |
| Idle | Blue | Solid |
| Calibrating | Yellow | Pulse |
| Ready | Green | Solid |
| Training (Follower) | Green | Fast pulse |
| Training (Pacer) | White | Fast pulse |
| Paused | Yellow | Solid |
| Complete | White | Solid |
| Error | Red | Solid |

The Pacer/Follower distinction during Training is the only case where two devices in the same `DeviceState` show different colors. The device learns its role from the role byte appended to the Zone Settings BLE write (see `ZONE_SETTINGS_CHAR`).
