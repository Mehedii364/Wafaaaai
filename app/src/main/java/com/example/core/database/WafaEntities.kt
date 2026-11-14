package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val folder: String = "General",
    val isPinned: Boolean = false,
    val preferredProviderId: String? = null,
    val preferredModelId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val attachmentSummary: String? = null,
    val attachmentUri: String? = null,
    val providerName: String? = null,
    val modelId: String? = null,
    val latencyMs: Long = 0L,
    val tokenEstimate: Int = 0,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_providers")
data class AIProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val baseUrl: String,
    val authMethod: String,
    val isEnabled: Boolean = true,
    val priority: Int = 10,
    val isFreeTier: Boolean = false,
    val freeTierNotes: String = "",
    val docsUrl: String = "",
    val capabilitiesCsv: String = "CHAT",
    val selectedModelId: String = "",
    val connectionStatus: String = "UNCONFIGURED", // UNCONFIGURED, CONNECTED, ERROR, RATE_LIMITED
    val lastLatencyMs: Long = 0L,
    val lastTestedAt: Long = 0L,
    val isCustomProxy: Boolean = false
)

@Entity(
    tableName = "ai_models",
    primaryKeys = ["providerId", "modelId"],
    indices = [Index("providerId")]
)
data class AIModelEntity(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val contextWindow: Int = 32768,
    val supportsVision: Boolean = false,
    val supportsCoding: Boolean = true,
    val supportsImageGen: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsSearch: Boolean = false,
    val isCustomDiscovered: Boolean = false
)

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_memories")
data class SavedMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String, // Preference, Project, Note, Context
    val title: String,
    val content: String,
    val tagsCsv: String = "",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val notes: String = "",
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val dueDateMillis: Long? = null,
    val isCompleted: Boolean = false,
    val projectTag: String = "Personal",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val triggerAtMillis: Long,
    val repeatRule: String = "NONE",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "file_records")
data class FileRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localUriOrPath: String,
    val extractedSummary: String = "",
    val folderTag: String = "Uploads",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "generated_artifacts")
data class GeneratedArtifactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val studioType: String, // CODING, WRITING, DOCUMENT, SPREADSHEET, PRESENTATION, IMAGE, AUDIO
    val title: String,
    val format: String, // DOCX, XLSX, PPTX, PDF, ZIP, PNG, MD, CSV, TXT
    val filePath: String,
    val previewText: String = "",
    val byteSize: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_executions")
data class AgentExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val specialistName: String,
    val userGoal: String,
    val planStepsJson: String,
    val status: String, // AWAITING_APPROVAL, EXECUTING, COMPLETED, CANCELLED, FAILED
    val resultSummary: String = "",
    val approvedByUser: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "usage_records")
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val providerId: String,
    val modelId: String,
    val taskCategory: String,
    val promptTokensEst: Int,
    val completionTokensEst: Int,
    val latencyMs: Long,
    val succeeded: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "api_request_logs")
data class ApiRequestLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val providerId: String,
    val endpoint: String,
    val statusCode: Int,
    val latencyMs: Long,
    val redactedError: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "research_sources")
data class ResearchSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val query: String,
    val title: String,
    val url: String,
    val snippet: String,
    val factType: String, // VERIFIED_SOURCE, AI_SYNTHESIS
    val savedAt: Long = System.currentTimeMillis()
)
