# Follower haptics are mediated by the Android phone

When the pacer's device detects a Catch, it notifies the Android phone via BLE. The phone then writes `CMD_SINGLE_PULSE` to each follower device. Devices do not signal each other directly.

Direct device-to-device signalling (BLE mesh) would reduce latency but would require each nRF52840 to act as both BLE peripheral and central simultaneously — a capability the hardware does not support in this configuration. The phone-mediated path is a fixed architectural constraint, not a performance trade-off to revisit. The 50ms "perfect sync" threshold in Sync Score is calibrated to this round-trip budget.
