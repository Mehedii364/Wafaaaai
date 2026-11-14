package com.example.feature.audio

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.TaskCategory
import com.example.core.ui.*
import com.example.core.voice.VoiceEngine
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun AudioStudioScreen(
    viewModel: MainViewModel,
    voiceEngine: VoiceEngine,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()

    var transcript by remember { mutableStateOf("") }
    var aiSummary by remember { mutableStateOf("") }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceEngine.startListening(settings.voiceLocale) { transcript = it }
        } else {
            statusMsg = "Microphone permission is required for speech input."
        }
    }

    val audioFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val tmp = File(context.cacheDir, "uploaded_audio_${System.currentTimeMillis()}.m4a")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            recordedFile = tmp
            statusMsg = "Audio file loaded (${tmp.length() / 1024} KB)"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Audio & Voice Studio",
            subtitle = "Speech-to-Text • TTS (EN/BN) • Whisper Transcription",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        WafaWaveformOrb(
                            isActive = voiceState.isListening || voiceState.isRecordingClip || voiceState.isSpeaking,
                            rmsLevel = voiceState.rmsLevel,
                            modifier = Modifier.size(110.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (voiceState.isListening) {
                                    voiceEngine.stopListening()
                                } else {
                                    voiceEngine.startListening(settings.voiceLocale) { transcript = it }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(if (voiceState.isListening) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (voiceState.isListening) "Stop STT" else "Live Dictate")
                        }

                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (voiceState.isRecordingClip) {
                                    recordedFile = voiceEngine.stopRecordingClip()
                                    statusMsg = "Recorded clip saved"
                                } else {
                                    recordedFile = voiceEngine.startRecordingClip()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (voiceState.isRecordingClip) "Stop Rec" else "Record Clip")
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { audioFilePicker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Upload Audio")
                    }
                    Button(
                        enabled = recordedFile != null && !isBusy,
                        onClick = {
                            val file = recordedFile ?: return@Button
                            scope.launch {
                                isBusy = true
                                val groq = providers.firstOrNull { it.id == "groq" }
                                    ?: providers.firstOrNull { it.id == "openai_compat" }
                                val key = groq?.let { viewModel.repository.vault.getApiKey(it.id) }
                                if (groq == null || key.isNullOrBlank()) {
                                    isBusy = false
                                    statusMsg = "Configure a Groq (Whisper) or OpenAI-compatible key in API Manager for file transcription."
                                    return@launch
                                }
                                val lang = if (settings.voiceLocale.startsWith("bn")) "bn" else "en"
                                val res = viewModel.repository.gateway.transcribeAudioFile(groq, key, file, lang)
                                isBusy = false
                                res.onSuccess { transcript = it; statusMsg = "Transcribed via ${groq.name}" }
                                    .onFailure { statusMsg = it.message }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isBusy) "Transcribing..." else "Transcribe File") }
                }
            }

            item {
                OutlinedTextField(
                    value = transcript,
                    onValueChange = { transcript = it },
                    label = { Text("Transcript / Text for TTS") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    maxLines = 10
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = transcript.isNotBlank(),
                        onClick = { voiceEngine.speak(transcript, settings.voiceLocale, settings.ttsSpeechRate) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Speak (TTS)") }

                    OutlinedButton(
                        onClick = { voiceEngine.stopSpeaking() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Stop TTS") }

                    FilledTonalButton(
                        enabled = transcript.isNotBlank() && !isBusy,
                        onClick = {
                            isBusy = true
                            aiSummary = ""
                            viewModel.runSingleShotAiTask(
                                taskCategory = TaskCategory.AUDIO_SPEECH,
                                systemInstruction = "Summarize this audio transcript and translate key points if helpful.",
                                userPrompt = transcript,
                                onDelta = { aiSummary = it },
                                onDone = { final, err -> isBusy = false; if (err != null) statusMsg = err else aiSummary = final }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Summarize") }
                }
            }

            if (statusMsg != null) {
                item { WafaHudBadge(statusMsg!!, ElectricCyan) }
            }

            if (aiSummary.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        WafaRichTextContent(text = aiSummary)
                    }
                }
            }
        }
    }
}
