package com.example.core.voice

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

data class VoiceUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isRecordingClip: Boolean = false,
    val rmsLevel: Float = 0f,
    val partialTranscript: String = "",
    val lastError: String? = null,
    val speechAvailable: Boolean = true
)

class VoiceEngine(private val context: Context) {

    private val _state = MutableStateFlow(
        VoiceUiState(speechAvailable = SpeechRecognizer.isRecognitionAvailable(context))
    )
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
            }
        } catch (_: Throwable) {
            ttsReady = false
        }
    }

    fun startListening(localeTag: String = "en-US", onFinalResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                speechAvailable = false,
                lastError = "Speech recognition service is unavailable on this device."
            )
            return
        }

        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(isListening = true, lastError = null, partialTranscript = "")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                _state.value = _state.value.copy(rmsLevel = normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(isListening = false, rmsLevel = 0f)
            }

            override fun onError(error: Int) {
                _state.value = _state.value.copy(
                    isListening = false,
                    rmsLevel = 0f,
                    lastError = "Speech error code $error"
                )
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                _state.value = _state.value.copy(
                    isListening = false,
                    rmsLevel = 0f,
                    partialTranscript = text
                )
                if (text.isNotBlank()) onFinalResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                    _state.value = _state.value.copy(partialTranscript = partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Throwable) {
        }
        speechRecognizer = null
        _state.value = _state.value.copy(isListening = false, rmsLevel = 0f)
    }

    fun speak(text: String, localeTag: String = "en-US", speechRate: Float = 1.0f) {
        if (!ttsReady || text.isBlank()) return
        val locale = if (localeTag.startsWith("bn")) Locale.forLanguageTag("bn-BD") else Locale.US
        tts?.language = locale
        tts?.setSpeechRate(speechRate.coerceIn(0.6f, 1.8f))
        _state.value = _state.value.copy(isSpeaking = true)
        tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "wafa_tts_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        _state.value = _state.value.copy(isSpeaking = false)
    }

    fun startRecordingClip(): File? {
        return try {
            val outDir = File(context.filesDir, "audio").apply { mkdirs() }
            val outFile = File(outDir, "wafa_rec_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96000)
            recorder.setAudioSamplingRate(16000)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            currentRecordingFile = outFile
            _state.value = _state.value.copy(isRecordingClip = true, lastError = null)
            outFile
        } catch (t: Throwable) {
            _state.value = _state.value.copy(isRecordingClip = false, lastError = "Audio recorder failed: ${t.message}")
            null
        }
    }

    fun stopRecordingClip(): File? {
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            _state.value = _state.value.copy(isRecordingClip = false)
            currentRecordingFile
        } catch (_: Throwable) {
            mediaRecorder = null
            _state.value = _state.value.copy(isRecordingClip = false)
            null
        }
    }

    fun release() {
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        mediaRecorder?.release()
    }
}
