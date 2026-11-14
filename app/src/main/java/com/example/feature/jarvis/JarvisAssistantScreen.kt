package com.example.feature.jarvis

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.TaskCategory
import com.example.core.tools.UtilityToolsEngine
import com.example.core.ui.*
import com.example.core.voice.VoiceEngine
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.VioletPulse
import kotlinx.coroutines.launch

@Composable
fun JarvisAssistantScreen(
    viewModel: MainViewModel,
    voiceEngine: VoiceEngine,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()

    var command by remember { mutableStateOf("") }
    var jarvisReply by remember { mutableStateOf("Systems online. Ready for voice, vision, weather, or contextual tasks.") }
    var weatherCard by remember { mutableStateOf<String?>(null) }
    var qrDecoded by remember { mutableStateOf<String?>(null) }
    var isThinking by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceEngine.startListening(settings.voiceLocale) { spoken ->
                command = spoken
                isThinking = true
                jarvisReply = ""
                viewModel.runSingleShotAiTask(
                    taskCategory = TaskCategory.CHAT,
                    systemInstruction = "You are JARVIS inside WAFA AI. Give concise, actionable spoken-friendly responses.",
                    userPrompt = spoken,
                    onDelta = { jarvisReply = it },
                    onDone = { final, err ->
                        isThinking = false
                        val out = err ?: final
                        jarvisReply = out
                        if (err == null) voiceEngine.speak(out, settings.voiceLocale, settings.ttsSpeechRate)
                    }
                )
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val coords = readLastKnownCoarseLocation(context)
                if (coords != null) {
                    viewModel.repository.webTools.fetchWeatherByCoords(coords.first, coords.second)
                        .onSuccess { weatherCard = "${it.cityName}: ${it.temperatureC}°C • ${it.conditionLabel} • Wind ${it.windSpeedKmh} km/h" }
                        .onFailure { weatherCard = it.message }
                } else {
                    weatherCard = "Location unavailable on device. Use city weather in Tools."
                }
            }
        }
    }

    val visionOrQrPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val qr = bmp?.let { UtilityToolsEngine.decodeBarcodeOrQr(it) }
                if (qr != null) {
                    qrDecoded = "Decoded QR/Barcode: $qr"
                }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                isThinking = true
                jarvisReply = ""
                viewModel.runSingleShotAiTask(
                    taskCategory = TaskCategory.VISION,
                    systemInstruction = "You are JARVIS Camera Intelligence. Describe the scene concisely and highlight actionable details.",
                    userPrompt = command.ifBlank { "Analyze what is shown in this photo." },
                    imageBase64 = b64,
                    imageMimeType = "image/jpeg",
                    onDelta = { jarvisReply = it },
                    onDone = { final, err -> isThinking = false; jarvisReply = err ?: final }
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "JARVIS Core",
            subtitle = "Voice HUD • Camera Vision • QR • Location Weather",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WafaWaveformOrb(
                            isActive = voiceState.isListening || voiceState.isSpeaking || isThinking,
                            rmsLevel = voiceState.rmsLevel,
                            modifier = Modifier.size(190.dp)
                        )
                        if (voiceState.partialTranscript.isNotBlank()) {
                            Text(
                                text = "\"${voiceState.partialTranscript}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricCyan
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else if (voiceState.isListening) {
                                        voiceEngine.stopListening()
                                    } else {
                                        voiceEngine.startListening(settings.voiceLocale) { spoken ->
                                            command = spoken
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("jarvis_listen_button")
                            ) {
                                Icon(if (voiceState.isListening) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (voiceState.isListening) "Listening..." else "Tap to Speak")
                            }

                            OutlinedButton(onClick = {
                                if (voiceState.isSpeaking) voiceEngine.stopSpeaking()
                                else voiceEngine.speak(jarvisReply, settings.voiceLocale, settings.ttsSpeechRate)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (voiceState.isSpeaking) "Mute" else "Read")
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            visionOrQrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Vision / QR")
                    }

                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                scope.launch {
                                    val coords = readLastKnownCoarseLocation(context)
                                    if (coords != null) {
                                        viewModel.repository.webTools.fetchWeatherByCoords(coords.first, coords.second)
                                            .onSuccess { weatherCard = "${it.cityName}: ${it.temperatureC}°C • ${it.conditionLabel}" }
                                            .onFailure { weatherCard = it.message }
                                    } else {
                                        weatherCard = "No cached GPS fix on device. Open Personal Tools -> Weather to query any city."
                                    }
                                }
                            } else {
                                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Local Weather")
                    }
                }
            }

            if (weatherCard != null) {
                item { WafaHudBadge(weatherCard!!, EmeraldSignal) }
            }
            if (qrDecoded != null) {
                item { WafaHudBadge(qrDecoded!!, VioletPulse) }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Contextual JARVIS Command") },
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        enabled = command.isNotBlank() && !isThinking,
                        onClick = {
                            isThinking = true
                            jarvisReply = ""
                            viewModel.runSingleShotAiTask(
                                taskCategory = TaskCategory.CHAT,
                                systemInstruction = "You are JARVIS in WAFA AI. Execute or answer the command clearly. Note: wake-word detection is foreground-only when activated.",
                                userPrompt = command,
                                onDelta = { jarvisReply = it },
                                onDone = { final, err ->
                                    isThinking = false
                                    jarvisReply = err ?: final
                                }
                            )
                        }
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send command") }
                }
            }

            item {
                WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                    Text("JARVIS Response", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                    Spacer(Modifier.height(6.dp))
                    WafaRichTextContent(text = jarvisReply)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun readLastKnownCoarseLocation(context: Context): Pair<Double, Double>? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: return null
        loc.latitude to loc.longitude
    } catch (_: Throwable) {
        null
    }
}
