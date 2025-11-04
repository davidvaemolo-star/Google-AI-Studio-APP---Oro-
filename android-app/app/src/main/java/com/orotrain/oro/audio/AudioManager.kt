package com.orotrain.oro.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.orotrain.oro.model.Zone
import com.orotrain.oro.model.ZoneLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class AudioManager(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var soundPool: SoundPool? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Sound effect IDs (placeholder - actual sound files need to be added to res/raw/)
    private var strokeCatchSoundId: Int? = null
    private var strokeFinishSoundId: Int? = null
    private var setCompleteSoundId: Int? = null
    private var zoneTransitionSoundId: Int? = null
    private var sessionCompleteSoundId: Int? = null

    init {
        initializeTTS()
        initializeSoundPool()
    }

    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setSpeechRate(1.1f) // Slightly faster for training

                @Suppress("DEPRECATION")
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        Log.e(TAG, "TTS error for utterance: $utteranceId")
                    }
                })

                _isReady.value = true
                Log.d(TAG, "Text-to-Speech initialized successfully")
            } else {
                Log.e(TAG, "Text-to-Speech initialization failed")
            }
        }
    }

    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        // TODO: Load actual sound files when added to res/raw/
        // strokeCatchSoundId = soundPool?.load(context, R.raw.stroke_catch, 1)
        // strokeFinishSoundId = soundPool?.load(context, R.raw.stroke_finish, 1)
        // setCompleteSoundId = soundPool?.load(context, R.raw.set_complete, 1)
        // zoneTransitionSoundId = soundPool?.load(context, R.raw.zone_transition, 1)
        // sessionCompleteSoundId = soundPool?.load(context, R.raw.session_complete, 1)

        Log.d(TAG, "SoundPool initialized (sound files not loaded)")
    }

    // Training session announcements

    fun announceTrainingStart(totalZones: Int) {
        speak("Starting training session with $totalZones zones. Ready. Go!")
    }

    fun announceSetComplete(completedSet: Int, totalSets: Int) {
        speak("Set $completedSet complete. ${totalSets - completedSet} sets remaining.")
    }

    @Suppress("UNUSED_PARAMETER")
    fun announceZoneTransition(
        nextZone: Zone,
        zoneNumber: Int,
        totalZones: Int,
        strokesUntilTransition: Int = 5
    ) {
        val zoneName = when (nextZone.level) {
            ZoneLevel.Low -> "low intensity"
            ZoneLevel.Medium -> "medium intensity"
            ZoneLevel.High -> "high intensity"
        }

        if (strokesUntilTransition > 0) {
            speak("$strokesUntilTransition strokes to $zoneName zone")
        } else {
            speak("Zone $zoneNumber. $zoneName. ${nextZone.strokes} strokes. ${nextZone.sets} sets.")
        }
    }

    fun announceHalfway(currentStroke: Int, totalStrokes: Int) {
        speak("Halfway through the set. $currentStroke of $totalStrokes strokes.")
    }

    fun announceLastSet() {
        speak("Last set. Give it everything!")
    }

    fun announceCurrentPace(currentSpm: Int, targetSpm: Int) {
        val comparison = when {
            currentSpm < targetSpm - 5 -> "too slow"
            currentSpm > targetSpm + 5 -> "too fast"
            else -> "on pace"
        }
        speak("Current pace $currentSpm. Target $targetSpm. You're $comparison.")
    }

    fun announceSyncQuality(syncPercentage: Int) {
        val quality = when {
            syncPercentage >= 95 -> "Excellent"
            syncPercentage >= 85 -> "Good"
            syncPercentage >= 75 -> "Fair"
            else -> "Poor"
        }
        speak("$quality synchronization. $syncPercentage percent in sync.")
    }

    fun announceSessionComplete(totalStrokes: Int, avgSpm: Int) {
        speak("Training complete! Total strokes: $totalStrokes. Average pace: $avgSpm strokes per minute. Great work!")
    }

    fun announceTrainingPaused() {
        speak("Training paused")
    }

    fun announceTrainingResumed() {
        speak("Resuming")
    }

    // Sound effects

    fun playStrokeCatchSound() {
        strokeCatchSoundId?.let { soundId ->
            soundPool?.play(soundId, 0.5f, 0.5f, 1, 0, 1.0f)
        }
    }

    fun playStrokeFinishSound() {
        strokeFinishSoundId?.let { soundId ->
            soundPool?.play(soundId, 0.7f, 0.7f, 1, 0, 1.0f)
        }
    }

    fun playSetCompleteSound() {
        setCompleteSoundId?.let { soundId ->
            soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    fun playZoneTransitionSound() {
        zoneTransitionSoundId?.let { soundId ->
            soundPool?.play(soundId, 0.8f, 0.8f, 1, 0, 1.0f)
        }
    }

    fun playSessionCompleteSound() {
        sessionCompleteSoundId?.let { soundId ->
            soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    // Core TTS function

    private fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        textToSpeech?.speak(text, queueMode, null, "oro_tts_${System.currentTimeMillis()}")
        Log.d(TAG, "Speaking: $text")
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
    }

    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    @Suppress("UNUSED_PARAMETER")
    fun setVolume(volume: Float) {
        // Note: TTS volume is controlled by system media volume
        // SoundPool volume can be adjusted per-sound in play() calls
    }

    fun cleanup() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        soundPool?.release()
        soundPool = null

        _isReady.value = false
        Log.d(TAG, "AudioManager cleaned up")
    }

    companion object {
        private const val TAG = "AudioManager"
    }
}
