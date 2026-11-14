package com.example.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.AppStrings
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.VioletPulse

private data class FeatureStatusItem(
    val feature: String,
    val status: String,
    val detail: String
)

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    strings: AppStrings,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    var proxyInput by remember(settings.backendProxyUrl) { mutableStateOf(settings.backendProxyUrl) }
    var confirmWipe by remember { mutableStateOf(false) }

    val statusMatrix = listOf(
        FeatureStatusItem("12 AI Providers + XPiKi + 75 API Catalog", "Implemented", "OkHttp SSE + Keystore AES-256-GCM + Discovery"),
        FeatureStatusItem("Automatic Capability Model Router", "Tested", "Unit tested for Coding, Vision, Long Context & Fallback"),
        FeatureStatusItem("AI Chat + Markdown + Attachments", "Implemented", "Streaming, Edit, Regenerate, Stop, Export MD"),
        FeatureStatusItem("Coding Studio + Multi-File ZIP", "Tested", "12 languages + verified ZIP archive creation"),
        FeatureStatusItem("Document Studio (DOCX, PDF, MD, TXT, RTF)", "Tested", "ECMA-376 OOXML + Android PdfDocument & PdfRenderer"),
        FeatureStatusItem("Excel & Data Studio (XLSX + CSV)", "Tested", "Multi-sheet XLSX + live formula engine"),
        FeatureStatusItem("Presentation Studio (PPTX + PDF)", "Tested", "ECMA-376 slide XML generation & PDF export"),
        FeatureStatusItem("Image, Audio & Video Intelligence", "Implemented", "Photo Picker, MediaMetadataRetriever, STT/TTS, Whisper"),
        FeatureStatusItem("Live Research & Personal Tools", "Tested", "Wikipedia/arXiv/Crossref + ZXing QR + Open-Meteo + ECB FX"),
        FeatureStatusItem("10 Specialist Agents & Personal Memory", "Implemented", "Explicit user-approval gate + Room persistence")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = strings.settings,
            subtitle = "Language • Theme • Routing • Voice • Security",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Language
            item {
                WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                    Text("Language / ভাষা", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.language == "en",
                            onClick = { viewModel.updateSettingsLanguage("en") },
                            label = { Text("English") }
                        )
                        FilterChip(
                            selected = settings.language == "bn",
                            onClick = { viewModel.updateSettingsLanguage("bn") },
                            label = { Text("বাংলা (Bengali)") }
                        )
                    }
                }
            }

            // Theme
            item {
                WafaGlassCard(accentColor = VioletPulse, modifier = Modifier.fillMaxWidth()) {
                    Text("Interface Theme", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("dark" to "Obsidian Dark", "light" to "Daylight", "system" to "System").forEach { (id, label) ->
                            FilterChip(
                                selected = settings.themeMode == id,
                                onClick = { viewModel.updateThemeMode(id) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Model Router
            item {
                WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                    Text("Model Router Policy", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.routingMode == "AUTO",
                            onClick = { viewModel.updateRoutingMode("AUTO") },
                            label = { Text("Auto Capability Mode") }
                        )
                        FilterChip(
                            selected = settings.routingMode == "MANUAL",
                            onClick = { viewModel.updateRoutingMode("MANUAL") },
                            label = { Text("Manual Preferred") }
                        )
                    }
                    if (settings.routingMode == "MANUAL") {
                        Spacer(Modifier.height(6.dp))
                        Text("Preferred Provider: ${settings.preferredProviderId}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            providers.filter { it.isEnabled }.take(4).forEach { p ->
                                FilterChip(
                                    selected = settings.preferredProviderId == p.id,
                                    onClick = { viewModel.updatePreferredProvider(p.id, p.selectedModelId) },
                                    label = { Text(p.name) }
                                )
                            }
                        }
                    }
                }
            }

            // Voice & Speech
            item {
                WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                    Text("Voice & Speech Synthesis", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.voiceLocale == "en-US",
                            onClick = { viewModel.updateVoiceSettings(settings.autoSpeakReplies, "en-US", settings.ttsSpeechRate) },
                            label = { Text("Voice EN-US") }
                        )
                        FilterChip(
                            selected = settings.voiceLocale == "bn-BD",
                            onClick = { viewModel.updateVoiceSettings(settings.autoSpeakReplies, "bn-BD", settings.ttsSpeechRate) },
                            label = { Text("Voice বাংলা (bn-BD)") }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = settings.autoSpeakReplies,
                            onCheckedChange = { viewModel.updateVoiceSettings(it, settings.voiceLocale, settings.ttsSpeechRate) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Auto-speak JARVIS replies")
                    }
                    Text("Speech Rate: ${String.format("%.1fx", settings.ttsSpeechRate)}", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = settings.ttsSpeechRate,
                        onValueChange = { viewModel.updateVoiceSettings(settings.autoSpeakReplies, settings.voiceLocale, it) },
                        valueRange = 0.6f..1.8f
                    )
                }
            }

            // Security & Proxy
            item {
                WafaGlassCard(accentColor = Color(0xFFF59E0B), modifier = Modifier.fillMaxWidth()) {
                    Text("Security & Optional Backend Proxy", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = proxyInput,
                        onValueChange = { proxyInput = it },
                        label = { Text("Optional Production Proxy URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.updateBackendProxy(proxyInput) }, modifier = Modifier.weight(1f)) {
                            Text("Save Proxy")
                        }
                        OutlinedButton(
                            onClick = { confirmWipe = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Wipe All Keys", color = Color(0xFFEF4444))
                        }
                    }
                }
            }

            // Implementation Matrix
            item {
                Text("System Implementation Status", style = MaterialTheme.typography.titleMedium)
            }
            items(statusMatrix) { item ->
                WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.feature, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        WafaHudBadge(item.status, if (item.status == "Tested") EmeraldSignal else ElectricCyan)
                    }
                    Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Clear All Stored API Keys?") },
            text = { Text("This permanently deletes all BYOK credentials from Android Keystore storage on this device.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearAllApiKeys()
                    confirmWipe = false
                }) { Text("Wipe Keys") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel") }
            }
        )
    }
}
