package com.example.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.ConversationEntity
import com.example.core.database.MessageEntity
import com.example.core.i18n.AppStrings
import com.example.core.ui.*
import com.example.core.voice.VoiceEngine
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    voiceEngine: VoiceEngine,
    strings: AppStrings,
    onOpenApiManager: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val routingInfo by viewModel.activeRoutingInfo.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var attachedImageB64 by remember { mutableStateOf<String?>(null) }
    var attachedSummary by remember { mutableStateOf<String?>(null) }
    var attachedLabel by remember { mutableStateOf<String?>(null) }
    var overrideProviderId by remember { mutableStateOf<String?>(null) }

    var showHistorySheet by remember { mutableStateOf(false) }
    var historySearch by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var editMessageTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var editMessageText by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streamingText.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceEngine.startListening(settings.voiceLocale) { inputText = it }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                attachedImageB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                attachedLabel = "Image (${bytes.size / 1024} KB)"
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
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { showHistorySheet = true },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("chat_history_button")
            ) {
                Icon(Icons.Default.History, contentDescription = "Conversation History")
            }

            Column(modifier = Modifier.weight(1f)) {
                val activeTitle = conversations.firstOrNull { it.id == activeConvId }?.title ?: strings.navChat
                Text(
                    text = activeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = routingInfo.ifBlank {
                        overrideProviderId?.let { "Manual: $it" } ?: "Router: ${settings.routingMode}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                AssistChip(
                    onClick = { showModelMenu = true },
                    label = {
                        Text(
                            text = overrideProviderId ?: settings.routingMode,
                            maxLines = 1
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("chat_model_switcher")
                )
                DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Auto Capability Router") },
                        onClick = {
                            overrideProviderId = null
                            showModelMenu = false
                        }
                    )
                    providers.filter { it.isEnabled }.forEach { p ->
                        val hasKey = viewModel.repository.vault.hasApiKey(p.id) || p.id == "local_ollama"
                        DropdownMenuItem(
                            text = { Text("${p.name} (${p.selectedModelId})${if (hasKey) "" else " [No Key]"}") },
                            onClick = {
                                overrideProviderId = p.id
                                showModelMenu = false
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    val md = buildString {
                        append("# ${conversations.firstOrNull { it.id == activeConvId }?.title ?: "WAFA AI Chat"}\n\n")
                        messages.forEach { m ->
                            append("## ${m.role.uppercase()}\n${m.content}\n\n")
                        }
                    }
                    val info = viewModel.repository.docEngine.writePlainFile(md, "chat_export_${System.currentTimeMillis()}", "md")
                    shareGeneratedFile(context, info.file, "text/markdown")
                },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.IosShare, contentDescription = "Export Chat")
            }

            IconButton(
                onClick = { viewModel.createNewConversation() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("new_chat_button")
            ) {
                Icon(Icons.Default.AddComment, contentDescription = strings.newChat)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        // Message Stream
        if (messages.isEmpty() && !isGenerating) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                WafaEmptyState(
                    icon = Icons.Default.AutoAwesome,
                    title = "WAFA AI Conversational Core",
                    subtitle = "Supports streaming answers, code blocks, image/file analysis, Bengali & English, and automatic capability routing.",
                    actionLabel = strings.configureNow,
                    onAction = onOpenApiManager
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                        onSpeak = { voiceEngine.speak(msg.content, settings.voiceLocale, settings.ttsSpeechRate) },
                        onEdit = if (msg.role == "user") {
                            {
                                editMessageTarget = msg
                                editMessageText = msg.content
                            }
                        } else null,
                        onRegenerate = if (msg.role == "assistant" && msg.id == messages.lastOrNull()?.id) {
                            { viewModel.regenerateLastAssistant() }
                        } else null
                    )
                }

                if (isGenerating) {
                    item {
                        WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Streaming...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan
                                )
                            }
                            if (streamingText.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                WafaRichTextContent(text = streamingText)
                            }
                        }
                    }
                }
            }
        }

        // Composer
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (attachedLabel != null) {
                    AssistChip(
                        onClick = {
                            attachedLabel = null
                            attachedImageB64 = null
                            attachedSummary = null
                        },
                        label = { Text(attachedLabel!!) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove") }
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Attach Image")
                    }
                    IconButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach Document")
                    }
                    IconButton(
                        onClick = {
                            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                if (voiceState.isListening) voiceEngine.stopListening()
                                else voiceEngine.startListening(settings.voiceLocale) { inputText = it }
                            } else {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            if (voiceState.isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Voice Dictation",
                            tint = if (voiceState.isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message WAFA AI (EN / বাংলা)...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        maxLines = 4
                    )

                    if (isGenerating) {
                        FilledIconButton(
                            onClick = { viewModel.stopGeneration() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_stop_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = strings.stop)
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                val text = inputText
                                inputText = ""
                                val img = attachedImageB64
                                val sum = attachedSummary
                                attachedImageB64 = null
                                attachedSummary = null
                                attachedLabel = null
                                viewModel.sendChatMessage(
                                    prompt = text,
                                    attachmentSummary = sum,
                                    imageBase64 = img,
                                    imageMimeType = if (img != null) "image/jpeg" else null,
                                    overrideProviderId = overrideProviderId
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send)
                        }
                    }
                }
            }
        }
    }

    // Conversation History Modal Sheet
    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conversations", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = {
                        viewModel.createNewConversation()
                        showHistorySheet = false
                    }) {
                        Text(strings.newChat)
                    }
                }
                OutlinedTextField(
                    value = historySearch,
                    onValueChange = { historySearch = it },
                    placeholder = { Text("Search conversations...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                val filtered = conversations.filter {
                    historySearch.isBlank() || it.title.contains(historySearch, ignoreCase = true) ||
                        it.folder.contains(historySearch, ignoreCase = true)
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        WafaGlassCard(
                            onClick = {
                                viewModel.selectConversation(conv.id)
                                showHistorySheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conv.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Folder: ${conv.folder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.togglePinConversation(conv) }) {
                                    Icon(
                                        if (conv.isPinned) Icons.Default.PushPin else Icons.Default.OutlinedFlag,
                                        contentDescription = "Pin",
                                        tint = if (conv.isPinned) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    renameTarget = conv
                                    renameText = conv.title
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                                }
                                IconButton(onClick = { viewModel.deleteConversation(conv.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget?.let { viewModel.renameConversation(it, renameText) }
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (editMessageTarget != null) {
        AlertDialog(
            onDismissRequest = { editMessageTarget = null },
            title = { Text("Edit Prompt & Regenerate") },
            text = {
                OutlinedTextField(value = editMessageText, onValueChange = { editMessageText = it }, maxLines = 5)
            },
            confirmButton = {
                TextButton(onClick = {
                    editMessageTarget?.let { viewModel.editUserMessageAndResend(it, editMessageText) }
                    editMessageTarget = null
                }) { Text("Resend") }
            },
            dismissButton = {
                TextButton(onClick = { editMessageTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: MessageEntity,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onEdit: (() -> Unit)?,
    onRegenerate: (() -> Unit)?
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = when {
                message.isError -> Color(0x33EF4444)
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isUser && !message.providerName.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WafaHudBadge("${message.providerName} • ${message.modelId}", ElectricCyan)
                        if (message.latencyMs > 0) {
                            WafaHudBadge("${message.latencyMs}ms", EmeraldSignal)
                        }
                    }
                }
                if (!message.attachmentSummary.isNullOrBlank()) {
                    Text(
                        text = "[Attachment Context Included]",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                WafaRichTextContent(text = message.content)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    }
                    if (!isUser) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (onEdit != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit prompt", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (onRegenerate != null) {
                        IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
