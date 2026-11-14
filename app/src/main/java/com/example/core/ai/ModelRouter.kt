package com.example.core.ai

import com.example.core.database.AIModelEntity
import com.example.core.database.AIProviderEntity

enum class TaskCategory(val label: String) {
    CHAT("General Chat"),
    CODING("Coding & Engineering"),
    WRITING("Writing & Translation"),
    DOCUMENT_ANALYSIS("Document Analysis"),
    SPREADSHEET("Excel & Data"),
    PRESENTATION("Presentation"),
    VISION("Image Understanding"),
    IMAGE_GEN("Image Generation"),
    AUDIO_SPEECH("Audio & Speech"),
    VIDEO_ANALYSIS("Video Intelligence"),
    RESEARCH("Web & Academic Research"),
    AGENT_PLANNING("Agent Orchestration")
}

data class RoutingDecision(
    val primaryProvider: AIProviderEntity,
    val primaryModel: AIModelEntity,
    val fallbackProviders: List<Pair<AIProviderEntity, AIModelEntity>>,
    val reason: String
)

class ModelRouter {

    fun selectRoute(
        taskCategory: TaskCategory,
        providers: List<AIProviderEntity>,
        models: List<AIModelEntity>,
        configuredProviderIds: Set<String>,
        routingMode: String = "AUTO",
        preferredProviderId: String? = null,
        preferredModelId: String? = null,
        requiresLongContext: Boolean = false,
        requiresVision: Boolean = false
    ): RoutingDecision? {
        val enabledConfigured = providers
            .filter { it.isEnabled && (it.id in configuredProviderIds || it.id == "local_ollama") }
            .sortedBy { it.priority }

        val candidates = if (enabledConfigured.isNotEmpty()) {
            enabledConfigured
        } else {
            // If no keys configured yet, still return a transparent preview decision from enabled list
            providers.filter { it.isEnabled }.sortedBy { it.priority }
        }
        if (candidates.isEmpty()) return null

        val modelsByProvider = models.groupBy { it.providerId }

        if (routingMode == "MANUAL" && !preferredProviderId.isNullOrBlank()) {
            val manualProvider = candidates.firstOrNull { it.id == preferredProviderId }
            if (manualProvider != null) {
                val pModels = modelsByProvider[manualProvider.id].orEmpty()
                val manualModel = pModels.firstOrNull { it.modelId == preferredModelId }
                    ?: pModels.firstOrNull { it.modelId == manualProvider.selectedModelId }
                    ?: pModels.firstOrNull()
                    ?: fallbackModelFor(manualProvider)
                val fallbacks = buildFallbacks(manualProvider.id, candidates, modelsByProvider, taskCategory, requiresLongContext, requiresVision)
                return RoutingDecision(
                    primaryProvider = manualProvider,
                    primaryModel = manualModel,
                    fallbackProviders = fallbacks,
                    reason = "Manual override -> ${manualProvider.name} (${manualModel.displayName})"
                )
            }
        }

        val scored = candidates.mapNotNull { provider ->
            val pModels = modelsByProvider[provider.id].orEmpty().ifEmpty { listOf(fallbackModelFor(provider)) }
            val bestModel = pickBestModel(provider, pModels, taskCategory, requiresLongContext, requiresVision)
            val score = scoreCandidate(provider, bestModel, taskCategory, requiresLongContext, requiresVision, provider.id in configuredProviderIds)
            Triple(provider, bestModel, score)
        }.sortedByDescending { it.third }

        val best = scored.firstOrNull() ?: return null
        val fallbacks = scored.drop(1).map { it.first to it.second }
        val reason = buildReason(taskCategory, best.first, best.second, requiresLongContext, requiresVision)

        return RoutingDecision(
            primaryProvider = best.first,
            primaryModel = best.second,
            fallbackProviders = fallbacks,
            reason = reason
        )
    }

    private fun pickBestModel(
        provider: AIProviderEntity,
        models: List<AIModelEntity>,
        task: TaskCategory,
        requiresLongContext: Boolean,
        requiresVision: Boolean
    ): AIModelEntity {
        val filtered = when {
            task == TaskCategory.IMAGE_GEN -> models.filter { it.supportsImageGen }.ifEmpty { models }
            requiresVision || task == TaskCategory.VISION -> models.filter { it.supportsVision }.ifEmpty { models }
            task == TaskCategory.AUDIO_SPEECH -> models.filter { it.supportsAudio }.ifEmpty { models }
            task == TaskCategory.CODING -> models.filter { it.supportsCoding }.ifEmpty { models }
            requiresLongContext || task == TaskCategory.DOCUMENT_ANALYSIS -> models.sortedByDescending { it.contextWindow }
            else -> models
        }

        val preferred = filtered.firstOrNull { it.modelId == provider.selectedModelId }
        if (preferred != null && task != TaskCategory.IMAGE_GEN && !requiresVision) {
            return preferred
        }

        return when (task) {
            TaskCategory.CODING -> filtered.firstOrNull {
                it.modelId.contains("coder", ignoreCase = true) ||
                    it.modelId.contains("codestral", ignoreCase = true) ||
                    it.modelId.contains("sonnet", ignoreCase = true) ||
                    it.modelId.contains("pro", ignoreCase = true)
            } ?: filtered.first()
            TaskCategory.DOCUMENT_ANALYSIS -> filtered.maxByOrNull { it.contextWindow } ?: filtered.first()
            TaskCategory.IMAGE_GEN -> filtered.firstOrNull { it.supportsImageGen } ?: filtered.first()
            TaskCategory.VISION -> filtered.firstOrNull { it.supportsVision } ?: filtered.first()
            TaskCategory.AUDIO_SPEECH -> filtered.firstOrNull { it.supportsAudio } ?: filtered.first()
            else -> preferred ?: filtered.first()
        }
    }

    private fun scoreCandidate(
        provider: AIProviderEntity,
        model: AIModelEntity,
        task: TaskCategory,
        requiresLongContext: Boolean,
        requiresVision: Boolean,
        hasKey: Boolean
    ): Int {
        var score = 100 - (provider.priority * 3)
        if (hasKey) score += 120
        if (provider.connectionStatus == "CONNECTED") score += 40
        if (provider.connectionStatus == "ERROR" || provider.connectionStatus == "RATE_LIMITED") score -= 60

        val caps = provider.capabilitiesCsv
        when (task) {
            TaskCategory.CODING -> {
                if (model.supportsCoding) score += 35
                if (model.modelId.contains("coder", true) || model.modelId.contains("codestral", true)) score += 30
            }
            TaskCategory.VISION -> if (model.supportsVision) score += 80 else score -= 80
            TaskCategory.IMAGE_GEN -> if (model.supportsImageGen || caps.contains("IMAGE_GEN")) score += 90 else score -= 90
            TaskCategory.DOCUMENT_ANALYSIS -> {
                if (model.contextWindow >= 128000) score += 45
                if (model.contextWindow >= 500000) score += 35
            }
            TaskCategory.AUDIO_SPEECH -> if (model.supportsAudio) score += 80 else score -= 50
            TaskCategory.VIDEO_ANALYSIS -> if (caps.contains("VIDEO") || model.contextWindow >= 500000) score += 80 else score -= 60
            TaskCategory.RESEARCH -> if (model.supportsSearch || caps.contains("RESEARCH")) score += 35
            else -> if (caps.contains("STREAMING")) score += 15
        }

        if (requiresVision && !model.supportsVision) score -= 100
        if (requiresLongContext && model.contextWindow < 64000) score -= 40
        if (provider.lastLatencyMs in 1..1500) score += 15
        return score
    }

    private fun buildFallbacks(
        excludeProviderId: String,
        candidates: List<AIProviderEntity>,
        modelsByProvider: Map<String, List<AIModelEntity>>,
        task: TaskCategory,
        requiresLongContext: Boolean,
        requiresVision: Boolean
    ): List<Pair<AIProviderEntity, AIModelEntity>> {
        return candidates.filter { it.id != excludeProviderId }.map { p ->
            val pModels = modelsByProvider[p.id].orEmpty().ifEmpty { listOf(fallbackModelFor(p)) }
            p to pickBestModel(p, pModels, task, requiresLongContext, requiresVision)
        }
    }

    private fun fallbackModelFor(provider: AIProviderEntity): AIModelEntity = AIModelEntity(
        providerId = provider.id,
        modelId = provider.selectedModelId.ifBlank { "default-model" },
        displayName = provider.selectedModelId.ifBlank { "${provider.name} Default" }
    )

    private fun buildReason(
        task: TaskCategory,
        provider: AIProviderEntity,
        model: AIModelEntity,
        requiresLongContext: Boolean,
        requiresVision: Boolean
    ): String {
        val capabilityNote = when {
            task == TaskCategory.IMAGE_GEN -> "Image generation capability"
            requiresVision || task == TaskCategory.VISION -> "Vision multimodal support"
            requiresLongContext || task == TaskCategory.DOCUMENT_ANALYSIS -> "Long context (${model.contextWindow / 1000}K tokens)"
            task == TaskCategory.CODING -> "Code generation capability"
            task == TaskCategory.AUDIO_SPEECH -> "Audio/speech capability"
            else -> "Best priority & latency match"
        }
        return "${task.label}: ${provider.name} / ${model.modelId} ($capabilityNote)"
    }
}
