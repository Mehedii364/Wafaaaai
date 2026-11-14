package com.example.core.data

import android.content.Context
import com.example.core.ai.ModelRouter
import com.example.core.ai.ProviderSeeds
import com.example.core.ai.RoutingDecision
import com.example.core.ai.TaskCategory
import com.example.core.database.*
import com.example.core.files.OoxmlDocumentEngine
import com.example.core.network.*
import com.example.core.security.SecureCredentialVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class WafaRepository(
    private val context: Context,
    val db: WafaDatabase,
    val vault: SecureCredentialVault,
    val settingsStore: UserSettingsDataStore,
    val gateway: AiGatewayClient,
    val router: ModelRouter,
    val webTools: LiveWebToolsClient,
    val docEngine: OoxmlDocumentEngine,
    val networkMonitor: NetworkMonitor
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val providersFlow: Flow<List<AIProviderEntity>> = db.providerDao().observeProviders()
    val modelsFlow: Flow<List<AIModelEntity>> = db.modelDao().observeAllModels()
    val conversationsFlow: Flow<List<ConversationEntity>> = db.conversationDao().observeAll()
    val memoriesFlow: Flow<List<SavedMemoryEntity>> = db.memoryDao().observeMemories()
    val tasksFlow: Flow<List<TaskEntity>> = db.taskDao().observeTasks()
    val remindersFlow: Flow<List<ReminderEntity>> = db.reminderDao().observeReminders()
    val filesFlow: Flow<List<FileRecordEntity>> = db.fileRecordDao().observeFiles()
    val artifactsFlow: Flow<List<GeneratedArtifactEntity>> = db.artifactDao().observeArtifacts()
    val agentExecutionsFlow: Flow<List<AgentExecutionEntity>> = db.agentExecutionDao().observeExecutions()
    val usageFlow: Flow<List<UsageRecordEntity>> = db.telemetryDao().observeUsage()
    val apiLogsFlow: Flow<List<ApiRequestLogEntity>> = db.telemetryDao().observeLogs()
    val researchSourcesFlow: Flow<List<ResearchSourceEntity>> = db.researchSourceDao().observeSources()
    val settingsFlow: Flow<UserSettings> = settingsStore.settingsFlow
    val isOnlineFlow: Flow<Boolean> = networkMonitor.isOnlineFlow

    init {
        repoScope.launch { ensureSeeded() }
    }

    suspend fun ensureSeeded() {
        val existingProviders = db.providerDao().getProvidersOnce()
        if (existingProviders.isEmpty()) {
            db.providerDao().upsertAll(ProviderSeeds.defaultProviders())
        }
        val existingModels = db.modelDao().getAllModelsOnce()
        if (existingModels.isEmpty()) {
            db.modelDao().upsertAll(ProviderSeeds.defaultModels())
        }
    }

    fun getConfiguredProviderIds(providers: List<AIProviderEntity>): Set<String> =
        providers.filter { vault.hasApiKey(it.id) || it.id == "local_ollama" }.map { it.id }.toSet()

    suspend fun resolveRoute(
        taskCategory: TaskCategory,
        requiresLongContext: Boolean = false,
        requiresVision: Boolean = false,
        overrideProviderId: String? = null,
        overrideModelId: String? = null
    ): RoutingDecision? {
        val providers = db.providerDao().getProvidersOnce()
        val models = db.modelDao().getAllModelsOnce()
        val settings = settingsFlow.first()
        val configured = getConfiguredProviderIds(providers)

        val mode = if (!overrideProviderId.isNullOrBlank()) "MANUAL" else settings.routingMode
        return router.selectRoute(
            taskCategory = taskCategory,
            providers = providers,
            models = models,
            configuredProviderIds = configured,
            routingMode = mode,
            preferredProviderId = overrideProviderId ?: settings.preferredProviderId,
            preferredModelId = overrideModelId ?: settings.preferredModelId,
            requiresLongContext = requiresLongContext,
            requiresVision = requiresVision
        )
    }

    suspend fun testAndDiscoverProvider(provider: AIProviderEntity): ConnectionTestResult {
        val key = vault.getApiKey(provider.id)
        val acct = vault.getAccountIdentifier(provider.id)
        val result = gateway.testConnectionAndDiscoverModels(provider, key, acct)
        val newStatus = when {
            result.success -> "CONNECTED"
            result.statusCode == 429 -> "RATE_LIMITED"
            key.isNullOrBlank() && provider.id != "local_ollama" -> "UNCONFIGURED"
            else -> "ERROR"
        }
        db.providerDao().upsert(
            provider.copy(
                connectionStatus = newStatus,
                lastLatencyMs = result.latencyMs,
                lastTestedAt = System.currentTimeMillis()
            )
        )
        if (result.discoveredModels.isNotEmpty()) {
            db.modelDao().upsertAll(result.discoveredModels)
        }
        db.telemetryDao().insertLog(
            ApiRequestLogEntity(
                providerId = provider.id,
                endpoint = "/models",
                statusCode = result.statusCode,
                latencyMs = result.latencyMs,
                redactedError = if (result.success) "" else SecureCredentialVault.redactSecretsFromText(result.message)
            )
        )
        return result
    }

    /**
     * Streams AI response using automatic capability routing + provider fallback.
     */
    fun streamRoutedCompletion(
        taskCategory: TaskCategory,
        turns: List<ChatTurn>,
        requiresLongContext: Boolean = false,
        requiresVision: Boolean = false,
        overrideProviderId: String? = null,
        overrideModelId: String? = null
    ): Flow<RoutedStreamEvent> = flow {
        val decision = resolveRoute(
            taskCategory = taskCategory,
            requiresLongContext = requiresLongContext,
            requiresVision = requiresVision,
            overrideProviderId = overrideProviderId,
            overrideModelId = overrideModelId
        )
        if (decision == null) {
            emit(RoutedStreamEvent.Error("No enabled AI providers available."))
            return@flow
        }

        val settings = settingsFlow.first()
        val configured = getConfiguredProviderIds(db.providerDao().getProvidersOnce())
        val chain = buildList {
            add(decision.primaryProvider to decision.primaryModel)
            if (settings.enableFallback) {
                addAll(decision.fallbackProviders.filter { it.first.id in configured })
            }
        }

        val primaryKey = vault.getApiKey(decision.primaryProvider.id)
        if (primaryKey.isNullOrBlank() && decision.primaryProvider.id != "local_ollama" &&
            chain.none { it.first.id in configured }
        ) {
            emit(
                RoutedStreamEvent.Error(
                    "No API key configured for ${decision.primaryProvider.name}. Open API Manager to add your personal API key."
                )
            )
            return@flow
        }

        var lastError = ""
        for ((index, pair) in chain.withIndex()) {
            val (provider, model) = pair
            val key = vault.getApiKey(provider.id)
            if (key.isNullOrBlank() && provider.id != "local_ollama") continue

            emit(
                RoutedStreamEvent.Started(
                    provider = provider,
                    model = model,
                    routingReason = if (index == 0) decision.reason else "Fallback -> ${provider.name} / ${model.modelId}"
                )
            )

            var succeeded = false
            gateway.streamChat(
                provider = provider,
                modelId = model.modelId,
                apiKey = key,
                turns = turns,
                accountId = vault.getAccountIdentifier(provider.id)
            ).collect { ev ->
                when (ev) {
                    is StreamEvent.Delta -> emit(RoutedStreamEvent.Delta(ev.text))
                    is StreamEvent.Completed -> {
                        succeeded = true
                        db.telemetryDao().insertUsage(
                            UsageRecordEntity(
                                providerId = provider.id,
                                modelId = model.modelId,
                                taskCategory = taskCategory.name,
                                promptTokensEst = ev.promptTokensEst,
                                completionTokensEst = ev.completionTokensEst,
                                latencyMs = ev.latencyMs,
                                succeeded = true
                            )
                        )
                        db.telemetryDao().insertLog(
                            ApiRequestLogEntity(
                                providerId = provider.id,
                                endpoint = "/chat/completions",
                                statusCode = 200,
                                latencyMs = ev.latencyMs
                            )
                        )
                        emit(
                            RoutedStreamEvent.Completed(
                                fullText = ev.fullText,
                                providerName = provider.name,
                                modelId = model.modelId,
                                latencyMs = ev.latencyMs,
                                tokenEstimate = ev.promptTokensEst + ev.completionTokensEst
                            )
                        )
                    }
                    is StreamEvent.Failure -> {
                        lastError = ev.message
                        db.telemetryDao().insertUsage(
                            UsageRecordEntity(
                                providerId = provider.id,
                                modelId = model.modelId,
                                taskCategory = taskCategory.name,
                                promptTokensEst = 0,
                                completionTokensEst = 0,
                                latencyMs = 0L,
                                succeeded = false
                            )
                        )
                        db.telemetryDao().insertLog(
                            ApiRequestLogEntity(
                                providerId = provider.id,
                                endpoint = "/chat/completions",
                                statusCode = ev.statusCode,
                                latencyMs = 0L,
                                redactedError = SecureCredentialVault.redactSecretsFromText(ev.message)
                            )
                        )
                    }
                }
            }
            if (succeeded) return@flow
        }

        emit(RoutedStreamEvent.Error(lastError.ifBlank { "All configured AI providers failed." }))
    }

    suspend fun saveProviderConfig(
        provider: AIProviderEntity,
        newApiKey: String?,
        accountId: String?
    ) {
        db.providerDao().upsert(provider)
        if (newApiKey != null) {
            if (newApiKey.isBlank()) vault.clearApiKey(provider.id)
            else vault.saveApiKey(provider.id, newApiKey)
        }
        if (accountId != null) {
            vault.saveAccountIdentifier(provider.id, accountId)
        }
    }

    suspend fun exportNonSecretProvidersJson(): String {
        val providers = db.providerDao().getProvidersOnce()
        val arr = JSONArray()
        providers.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("category", p.category)
                    put("baseUrl", p.baseUrl)
                    put("authMethod", p.authMethod)
                    put("isEnabled", p.isEnabled)
                    put("priority", p.priority)
                    put("selectedModelId", p.selectedModelId)
                    put("capabilitiesCsv", p.capabilitiesCsv)
                }
            )
        }
        return JSONObject()
            .put("wafa_export_version", 1)
            .put("note", "API secrets are intentionally excluded for security.")
            .put("providers", arr)
            .toString(2)
    }

    suspend fun importNonSecretProvidersJson(jsonStr: String): Result<Int> {
        return try {
            val arr = JSONObject(jsonStr).getJSONArray("providers")
            var count = 0
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val existing = db.providerDao().getProvider(id)
                val updated = (existing ?: AIProviderEntity(
                    id = id,
                    name = obj.optString("name", id),
                    category = obj.optString("category", "Custom"),
                    baseUrl = obj.optString("baseUrl", "https://api.openai.com/v1"),
                    authMethod = obj.optString("authMethod", "Bearer API Key")
                )).copy(
                    baseUrl = obj.optString("baseUrl"),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    priority = obj.optInt("priority", 10),
                    selectedModelId = obj.optString("selectedModelId"),
                    capabilitiesCsv = obj.optString("capabilitiesCsv", "CHAT")
                )
                db.providerDao().upsert(updated)
                count++
            }
            Result.success(count)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

sealed class RoutedStreamEvent {
    data class Started(val provider: AIProviderEntity, val model: AIModelEntity, val routingReason: String) : RoutedStreamEvent()
    data class Delta(val text: String) : RoutedStreamEvent()
    data class Completed(
        val fullText: String,
        val providerName: String,
        val modelId: String,
        val latencyMs: Long,
        val tokenEstimate: Int
    ) : RoutedStreamEvent()
    data class Error(val message: String) : RoutedStreamEvent()
}
