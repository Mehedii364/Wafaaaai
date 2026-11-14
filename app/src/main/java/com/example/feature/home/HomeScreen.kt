package com.example.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.i18n.AppStrings
import com.example.core.ui.WafaGlassCard
import com.example.core.ui.WafaHudBadge
import com.example.core.voice.VoiceEngine
import com.example.feature.main.MainViewModel
import com.example.feature.navigation.WafaRoutes
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.VioletPulse

private data class QuickModuleItem(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    voiceEngine: VoiceEngine,
    strings: AppStrings,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()

    val configuredCount = remember(providers) {
        viewModel.repository.getConfiguredProviderIds(providers).size
    }

    var commandText by remember { mutableStateOf("") }
    var attachedImageB64 by remember { mutableStateOf<String?>(null) }
    var attachedSummary by remember { mutableStateOf<String?>(null) }
    var attachedLabel by remember { mutableStateOf<String?>(null) }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceEngine.startListening(settings.voiceLocale) { transcript ->
                commandText = transcript
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                attachedImageB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                attachedLabel = "Image attached (${bytes.size / 1024} KB)"
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, size, preview) = viewModel.repository.docEngine.readFileMetaAndPreview(uri)
            attachedSummary = preview
            attachedLabel = "$name (${size / 1024} KB)"
            viewModel.saveUploadedFileRecord(name, context.contentResolver.getType(uri) ?: "application/octet-stream", size, uri.toString(), preview.take(300))
        }
    }

    val primaryModules = remember(strings) {
        listOf(
            QuickModuleItem(strings.jarvisAssistant, "Voice + Vision HUD", WafaRoutes.JARVIS, Icons.Default.GraphicEq, ElectricCyan),
            QuickModuleItem(strings.aiChatLabel(), "Streaming + Code", WafaRoutes.CHAT, Icons.Default.Forum, ElectricBlue),
            QuickModuleItem(strings.codingStudio, "Multi-file + ZIP", WafaRoutes.CODING, Icons.Default.Terminal, VioletPulse),
            QuickModuleItem(strings.writingStudio, "EN / বাংলা Writer", WafaRoutes.WRITING, Icons.Default.EditNote, EmeraldSignal),
            QuickModuleItem(strings.documentStudio, "DOCX • PDF • MD", WafaRoutes.DOCUMENTS, Icons.Default.Description, ElectricCyan),
            QuickModuleItem(strings.spreadsheetStudio, "XLSX • Formulas", WafaRoutes.SPREADSHEET, Icons.Default.TableChart, EmeraldSignal),
            QuickModuleItem(strings.presentationStudio, "PPTX Slide Decks", WafaRoutes.PRESENTATIONS, Icons.Default.Slideshow, ElectricBlue),
            QuickModuleItem(strings.imageStudio, "Gen • Vision • OCR", WafaRoutes.IMAGE, Icons.Default.Image, VioletPulse),
            QuickModuleItem(strings.audioStudio, "STT • TTS • Whisper", WafaRoutes.AUDIO, Icons.Default.Mic, ElectricCyan),
            QuickModuleItem(strings.videoStudio, "Scenes • Subtitles", WafaRoutes.VIDEO, Icons.Default.VideoLibrary, ElectricBlue),
            QuickModuleItem(strings.researchEngine, "Verified Citations", WafaRoutes.RESEARCH, Icons.Default.TravelExplore, EmeraldSignal),
            QuickModuleItem(strings.fileWorkspace, "SAF Unified Hub", WafaRoutes.FILES, Icons.Default.FolderOpen, VioletPulse),
            QuickModuleItem(strings.agentOrchestrator, "10 AI Specialists", WafaRoutes.AGENTS, Icons.Default.Hub, ElectricCyan),
            QuickModuleItem(strings.personalMemory, "Notes & Context", WafaRoutes.MEMORY, Icons.Default.Psychology, ElectricBlue),
            QuickModuleItem(strings.personalTools, "QR • FX • Weather", WafaRoutes.TOOLS, Icons.Default.BuildCircle, EmeraldSignal),
            QuickModuleItem(strings.apiManager, "12 LLM + 75 APIs", WafaRoutes.API_MANAGER, Icons.Default.Key, VioletPulse)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Brand Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_wafa_avatar),
                        contentDescription = "WAFA AI Core Emblem",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Column {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = strings.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val next = if (settings.language == "en") "bn" else "en"
                            viewModel.updateSettingsLanguage(next)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("language_toggle_button")
                    ) {
                        Text(if (settings.language == "bn") "EN" else "বাংলা")
                    }
                    IconButton(
                        onClick = { onNavigate(WafaRoutes.SETTINGS) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = strings.settings)
                    }
                }
            }
        }

        // Hero Banner Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_jarvis_core_banner),
                    contentDescription = "WAFA AI Neural Command Center",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xE6050914),
                                    Color(0x99050914),
                                    Color(0x33050914)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WafaHudBadge(
                                label = if (isOnline) "ONLINE" else "OFFLINE",
                                color = if (isOnline) EmeraldSignal else Color(0xFFF59E0B),
                                icon = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff
                            )
                            WafaHudBadge(
                                label = "${strings.activeRouter}: ${settings.routingMode}",
                                color = ElectricCyan,
                                icon = Icons.Default.AutoAwesome
                            )
                        }
                        Text(
                            text = if (configuredCount > 0) {
                                "$configuredCount AI Provider(s) Ready"
                            } else {
                                "Personal Multimodal AI OS"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onNavigate(WafaRoutes.JARVIS) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("launch_jarvis_button")
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.jarvisAssistant)
                            }
                            OutlinedButton(
                                onClick = { onNavigate(WafaRoutes.API_MANAGER) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("hero_api_button")
                            ) {
                                Text(strings.configureNow, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // First-Run API Key Banner if unconfigured
        if (configuredCount == 0) {
            item {
                WafaGlassCard(
                    accentColor = Color(0xFFF59E0B),
                    onClick = { onNavigate(WafaRoutes.API_MANAGER) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_api_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BYOK Setup Required for Live AI",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.configureKeyBanner,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        // Unified Multimodal Command Bar
        item {
            WafaGlassCard(
                accentColor = ElectricCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unified_command_card")
            ) {
                Text(
                    text = "Unified Multimodal Command",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                if (attachedLabel != null) {
                    AssistChip(
                        onClick = {
                            attachedLabel = null
                            attachedImageB64 = null
                            attachedSummary = null
                        },
                        label = { Text(attachedLabel!!) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove attachment") }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    placeholder = { Text(strings.commandHint) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_command_input"),
                    maxLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    if (voiceState.isListening) voiceEngine.stopListening()
                                    else voiceEngine.startListening(settings.voiceLocale) { commandText = it }
                                } else {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                if (voiceState.isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = if (voiceState.isListening) Color(0xFFEF4444) else ElectricCyan
                            )
                        }
                        IconButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach Image")
                        }
                        IconButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach File")
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.createNewConversation(commandText.take(36).ifBlank { "Command Session" })
                            viewModel.sendChatMessage(
                                prompt = commandText,
                                attachmentSummary = attachedSummary,
                                imageBase64 = attachedImageB64,
                                imageMimeType = if (attachedImageB64 != null) "image/jpeg" else null
                            )
                            commandText = ""
                            attachedImageB64 = null
                            attachedSummary = null
                            attachedLabel = null
                            onNavigate(WafaRoutes.CHAT)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_command_send")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.send)
                    }
                }
            }
        }

        // Daily Briefing Summary Strip
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    WafaGlassCard(
                        accentColor = ElectricBlue,
                        onClick = { onNavigate(WafaRoutes.AGENTS) },
                        modifier = Modifier.width(172.dp)
                    ) {
                        Text("Open Tasks", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                        Text(
                            text = "${tasks.count { !it.isCompleted }} pending",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Tap to manage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    WafaGlassCard(
                        accentColor = VioletPulse,
                        onClick = { onNavigate(WafaRoutes.MEMORY) },
                        modifier = Modifier.width(172.dp)
                    ) {
                        Text("Saved Memory", style = MaterialTheme.typography.labelMedium, color = VioletPulse)
                        Text(
                            text = "${memories.size} entries",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("User-controlled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    WafaGlassCard(
                        accentColor = EmeraldSignal,
                        onClick = { onNavigate(WafaRoutes.TOOLS) },
                        modifier = Modifier.width(172.dp)
                    ) {
                        Text("Personal Tools", style = MaterialTheme.typography.labelMedium, color = EmeraldSignal)
                        Text("QR • FX • Weather", style = MaterialTheme.typography.titleMedium)
                        Text("Offline & Live APIs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 16 Workspaces Grid (2 columns inside LazyColumn)
        item {
            Text(
                text = strings.studiosTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(primaryModules.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { mod ->
                    WafaGlassCard(
                        accentColor = mod.accent,
                        onClick = { onNavigate(mod.route) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("module_card_${mod.route}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(mod.accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = mod.icon,
                                    contentDescription = mod.title,
                                    tint = mod.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mod.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = mod.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun AppStrings.aiChatLabel(): String = navChat
