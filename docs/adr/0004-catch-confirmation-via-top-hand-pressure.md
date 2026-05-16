# Catch detection uses IMU trigger + Top Hand Pressure confirmation

The IMU detects a candidate Catch (acceleration above threshold for 3 consecutive samples). Top Hand Pressure rising within a short window after the IMU trigger confirms the Catch as valid. If pressure does not rise within the window, the candidate is discarded as a false positive.

This matches the biomechanics: the paddle blade enters the water (IMU spike) then the paddler loads their top hand (pressure rise). The AND-gate and OR-gate alternatives were rejected — AND-gate is too brittle for variable technique; OR-gate increases false positives. The confirmation model also degrades gracefully: if the FSR malfunctions, the system can fall back to IMU-only detection without a code change, by treating all IMU candidates as confirmed.
