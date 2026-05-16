# Session summary delivered as pre-recorded voice prompts, not dynamic TTS

At the end of a session, the Android app sends a crew-wide summary to all devices as a pre-recorded audio prompt via the Audio Control BLE characteristic. The prompt is selected based on which Sync Score and Power Range bracket the crew achieved.

Dynamic TTS was rejected for MVP: the device audio system plays pre-compiled PCM arrays baked into firmware — streaming synthesised audio to 6 devices simultaneously over BLE is not feasible at this stage. The Android phone's TTS engine could generate dynamic text, but transmitting it as audio in real time is out of scope. Pre-recorded prompts keyed to outcome brackets are sufficient for MVP and leverage the existing audio infrastructure with no firmware changes.
