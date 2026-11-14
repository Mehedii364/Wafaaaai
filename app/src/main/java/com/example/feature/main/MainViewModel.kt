package com.example.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ai.ExternalApiCatalog
import com.example.core.ai.RoutingDecision
import com.example.core.ai.TaskCategory
import com.example.core.data.RoutedStreamEvent
import com.example.core.data.WafaRepository
import com.example.core.database.*
import com.example.core.network.ChatTurn
import com.example.core.network.ConnectionTestResult
import com.example.core.network.WeatherSnapshot
import com.example.core.network.WebCitation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(val repository: WafaRepository) : ViewModel() {

    val settings: StateFlow<UserSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val providers: StateFlow<List<AIProviderEntity>> = repository.providersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models: StateFlow<List<AIModelEntity>> = repository.modelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = repository.conversationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<SavedMemoryEntity>> = repository.memoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = repository.tasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = repository.remindersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val files: StateFlow<List<FileRecordEntity>> = repository.filesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artifacts: StateFlow<List<GeneratedArtifactEntity>> = repository.artifactsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentExecutions: StateFlow<List<AgentExecutionEntity>> = repository.agentExecutionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usageRecords: StateFlow<List<UsageRecordEntity>> = repository.usageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiLogs: StateFlow<List<ApiRequestLogEntity>> = repository.apiLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val researchSources: StateFlow<List<ResearchSourceEntity>> = repository.researchSourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.db.messageDao().observeForConversation(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _activeRoutingInfo = MutableStateFlow<String>("")
    val activeRoutingInfo: StateFlow<String> = _activeRoutingInfo.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var chatJob: Job? = null

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun selectConversation(id: Long?) {
        _activeConversationId.value = id
    }

    fun createNewConversation(title: String = "New Intelligence Session", folder: String = "General") {
        viewModelScope.launch {
            val id = repository.db.conversationDao().insert(
                ConversationEntity(title = title, folder = folder)
            )
            _activeConversationId.value = id
        }
    }

    fun renameConversation(conv: ConversationEntity, newTitle: String) {
        viewModelScope.launch {
            repository.db.conversationDao().update(conv.copy(title = newTitle.trim().ifBlank { conv.title }))
        }
    }

    fun togglePinConversation(conv: ConversationEntity) {
        viewModelScope.launch {
            repository.db.conversationDao().update(conv.copy(isPinned = !conv.isPinned))
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.db.conversationDao().deleteById(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
            }
        }
    }

    fun sendChatMessage(
        prompt: String,
        taskCategory: TaskCategory = TaskCategory.CHAT,
        attachmentSummary: String? = null,
        attachmentUri: String? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
        overrideProviderId: String? = null,
        overrideModelId: String? = null
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty() && imageBase64.isNullOrBlank() && attachmentSummary.isNullOrBlank()) return

        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            val convId = _activeConversationId.value ?: repository.db.conversationDao().insert(
                ConversationEntity(
                    title = cleanPrompt.take(36).ifBlank { "Multimodal Chat" }
                )
            ).also { _activeConversationId.value = it }

            val userFullText = buildString {
                append(cleanPrompt.ifBlank { "Analyze attached content." })
                if (!attachmentSummary.isNullOrBlank()) {
                    append("\n\n[Attached Context]\n")
                    append(attachmentSummary.take(6000))
                }
            }

            repository.db.messageDao().insert(
                MessageEntity(
                    conversationId = convId,
                    role = "user",
                    content = cleanPrompt.ifBlank { "Analyze attachment" },
                    attachmentSummary = attachmentSummary?.take(300),
                    attachmentUri = attachmentUri
                )
            )

            val conv = repository.db.conversationDao().getById(convId)
            if (conv != null) {
                repository.db.conversationDao().update(conv.copy(updatedAt = System.currentTimeMillis()))
            }

            val history = repository.db.messageDao().getForConversation(convId)
            val memoriesContext = repository.db.memoryDao().getMemoriesOnce()
                .filter { it.isPinned }
                .take(5)
                .joinToString("\n") { "- ${it.title}: ${it.content}" }

            val systemPrompt = buildString {
                append("You are WAFA AI, a precise, capable personal multimodal AI assistant. Respond clearly in the user's language (English or Bengali). Never fabricate sources or claim unexecuted tests.")
                if (memoriesContext.isNotBlank()) {
                    append("\n\nUser Pinned Context:\n$memoriesContext")
                }
            }

            val turns = buildList {
                add(ChatTurn("system", systemPrompt))
                history.dropLast(1).takeLast(12).forEach { msg ->
                    add(ChatTurn(msg.role, msg.content))
                }
                add(
                    ChatTurn(
                        role = "user",
                        text = userFullText,
                        imageBase64 = imageBase64,
                        imageMimeType = imageMimeType
                    )
                )
            }

            val needsLongContext = userFullText.length > 10_000
            val needsVision = !imageBase64.isNullOrBlank()

            _isGenerating.value = true
            _streamingText.value = ""

            repository.streamRoutedCompletion(
                taskCategory = if (needsVision) TaskCategory.VISION else taskCategory,
                turns = turns,
                requiresLongContext = needsLongContext,
                requiresVision = needsVision,
                overrideProviderId = overrideProviderId,
                overrideModelId = overrideModelId
            ).collect { ev ->
                when (ev) {
                    is RoutedStreamEvent.Started -> {
                        _activeRoutingInfo.value = ev.routingReason
                    }
                    is RoutedStreamEvent.Delta -> {
                        _streamingText.value += ev.text
                    }
                    is RoutedStreamEvent.Completed -> {
                        _isGenerating.value = false
                        _streamingText.value = ""
                        repository.db.messageDao().insert(
                            MessageEntity(
                                conversationId = convId,
                                role = "assistant",
                                content = ev.fullText.ifBlank { "(Empty response)" },
                                providerName = ev.providerName,
                                modelId = ev.modelId,
                                latencyMs = ev.latencyMs,
                                tokenEstimate = ev.tokenEstimate
                            )
                        )
                    }
                    is RoutedStreamEvent.Error -> {
                        _isGenerating.value = false
                        _streamingText.value = ""
                        repository.db.messageDao().insert(
                            MessageEntity(
                                conversationId = convId,
                                role = "assistant",
                                content = ev.message,
                                isError = true
                            )
                        )
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        chatJob?.cancel()
        val partial = _streamingText.value
        val convId = _activeConversationId.value
        _isGenerating.value = false
        _streamingText.value = ""
        if (partial.isNotBlank() && convId != null) {
            viewModelScope.launch {
                repository.db.messageDao().insert(
                    MessageEntity(
                        conversationId = convId,
                        role = "assistant",
                        content = "$partial\n\n_[Generation stopped by user]_"
                    )
                )
            }
        }
    }

    fun regenerateLastAssistant() {
        val msgs = activeMessages.value
        val lastUser = msgs.lastOrNull { it.role == "user" } ?: return
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            val last = msgs.lastOrNull()
            if (last != null && last.role == "assistant") {
                repository.db.messageDao().deleteById(last.id)
            }
            sendChatMessage(lastUser.content)
        }
    }

    fun editUserMessageAndResend(message: MessageEntity, updatedPrompt: String) {
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            repository.db.messageDao().deleteFromMessageId(convId, message.id)
            sendChatMessage(updatedPrompt)
        }
    }

    // ==================== STREAM HELPER FOR STUDIOS ====================

    fun runSingleShotAiTask(
        taskCategory: TaskCategory,
        systemInstruction: String,
        userPrompt: String,
        imageBase64: String? = null,
        imageMimeType: String? = null,
        onDelta: (String) -> Unit,
        onDone: (String, String?) -> Unit
    ): Job {
        return viewModelScope.launch {
            val acc = StringBuilder()
            val turns = listOf(
                ChatTurn("system", systemInstruction),
                ChatTurn("user", userPrompt, imageBase64, imageMimeType)
            )
            repository.streamRoutedCompletion(
                taskCategory = taskCategory,
                turns = turns,
                requiresLongContext = userPrompt.length > 8000,
                requiresVision = !imageBase64.isNullOrBlank()
            ).collect { ev ->
                when (ev) {
                    is RoutedStreamEvent.Started -> _activeRoutingInfo.value = ev.routingReason
                    is RoutedStreamEvent.Delta -> {
                        acc.append(ev.text)
                        onDelta(acc.toString())
                    }
                    is RoutedStreamEvent.Completed -> onDone(ev.fullText, null)
                    is RoutedStreamEvent.Error -> onDone(acc.toString(), ev.message)
                }
            }
        }
    }

    // ==================== MEMORY / TASKS / FILES / ARTIFACTS ====================

    fun saveMemory(category: String, title: String, content: String, tags: String = "", pinned: Boolean = false, id: Long = 0L) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            repository.db.memoryDao().upsert(
                SavedMemoryEntity(
                    id = id,
                    category = category,
                    title = title.trim(),
                    content = content.trim(),
                    tagsCsv = tags.trim(),
                    isPinned = pinned,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { repository.db.memoryDao().deleteById(id) }
    }

    fun addTask(title: String, notes: String = "", priority: String = "MEDIUM", projectTag: String = "Personal") {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.db.taskDao().upsert(
                TaskEntity(title = title.trim(), notes = notes.trim(), priority = priority, projectTag = projectTag)
            )
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.db.taskDao().upsert(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch { repository.db.taskDao().deleteById(id) }
    }

    fun addReminder(title: String, triggerAtMillis: Long) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.db.reminderDao().upsert(ReminderEntity(title = title.trim(), triggerAtMillis = triggerAtMillis))
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { repository.db.reminderDao().deleteById(id) }
    }

    fun recordGeneratedArtifact(studioType: String, title: String, format: String, file: File, previewText: String) {
        viewModelScope.launch {
            repository.db.artifactDao().upsert(
                GeneratedArtifactEntity(
                    studioType = studioType,
                    title = title,
                    format = format,
                    filePath = file.absolutePath,
                    previewText = previewText,
                    byteSize = file.length()
                )
            )
        }
    }

    fun deleteArtifact(artifact: GeneratedArtifactEntity) {
        viewModelScope.launch {
            runCatching { File(artifact.filePath).delete() }
            repository.db.artifactDao().deleteById(artifact.id)
        }
    }

    fun saveUploadedFileRecord(displayName: String, mimeType: String, sizeBytes: Long, uriStr: String, summary: String) {
        viewModelScope.launch {
            repository.db.fileRecordDao().upsert(
                FileRecordEntity(
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    localUriOrPath = uriStr,
                    extractedSummary = summary
                )
            )
        }
    }

    fun deleteFileRecord(id: Long) {
        viewModelScope.launch { repository.db.fileRecordDao().deleteById(id) }
    }

    // ==================== API MANAGER & SETTINGS ====================

    fun updateSettingsLanguage(lang: String) {
        viewModelScope.launch { repository.settingsStore.updateLanguage(lang) }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch { repository.settingsStore.updateThemeMode(mode) }
    }

    fun updateRoutingMode(mode: String) {
        viewModelScope.launch { repository.settingsStore.updateRoutingMode(mode) }
    }

    fun updatePreferredProvider(providerId: String, modelId: String = "") {
        viewModelScope.launch { repository.settingsStore.updatePreferredProvider(providerId, modelId) }
    }

    fun updateFallbackEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.settingsStore.updateFallback(enabled) }
    }

    fun updateVoiceSettings(autoSpeak: Boolean, locale: String, rate: Float) {
        viewModelScope.launch { repository.settingsStore.updateVoiceSettings(autoSpeak, locale, rate) }
    }

    fun updateBackendProxy(url: String) {
        viewModelScope.launch { repository.settingsStore.updateBackendProxyUrl(url) }
    }

    fun clearAllApiKeys() {
        repository.vault.clearAllApiKeys()
        _statusBanner.value = "All locally stored API keys cleared."
    }

    fun clearLogs() {
        viewModelScope.launch { repository.db.telemetryDao().clearLogs() }
    }

    class Factory(private val repository: WafaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
