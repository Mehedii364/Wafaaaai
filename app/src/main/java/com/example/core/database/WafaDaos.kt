package com.example.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ConversationEntity): Long

    @Update
    suspend fun update(item: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getForConversation(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id >= :fromId")
    suspend fun deleteFromMessageId(conversationId: Long, fromId: Long)
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY priority ASC, name ASC")
    fun observeProviders(): Flow<List<AIProviderEntity>>

    @Query("SELECT * FROM ai_providers ORDER BY priority ASC, name ASC")
    suspend fun getProvidersOnce(): List<AIProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE id = :id")
    suspend fun getProvider(id: String): AIProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(providers: List<AIProviderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: AIProviderEntity)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM ai_models ORDER BY providerId ASC, displayName ASC")
    fun observeAllModels(): Flow<List<AIModelEntity>>

    @Query("SELECT * FROM ai_models WHERE providerId = :providerId ORDER BY displayName ASC")
    suspend fun getModelsForProvider(providerId: String): List<AIModelEntity>

    @Query("SELECT * FROM ai_models")
    suspend fun getAllModelsOnce(): List<AIModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(models: List<AIModelEntity>)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM saved_memories ORDER BY isPinned DESC, updatedAt DESC")
    fun observeMemories(): Flow<List<SavedMemoryEntity>>

    @Query("SELECT * FROM saved_memories ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getMemoriesOnce(): List<SavedMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: SavedMemoryEntity): Long

    @Query("DELETE FROM saved_memories WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerAtMillis ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FileRecordDao {
    @Query("SELECT * FROM file_records ORDER BY createdAt DESC")
    fun observeFiles(): Flow<List<FileRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: FileRecordEntity): Long

    @Query("DELETE FROM file_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM generated_artifacts ORDER BY createdAt DESC")
    fun observeArtifacts(): Flow<List<GeneratedArtifactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artifact: GeneratedArtifactEntity): Long

    @Query("DELETE FROM generated_artifacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AgentExecutionDao {
    @Query("SELECT * FROM agent_executions ORDER BY createdAt DESC")
    fun observeExecutions(): Flow<List<AgentExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(execution: AgentExecutionEntity): Long
}

@Dao
interface TelemetryDao {
    @Query("SELECT * FROM usage_records ORDER BY timestamp DESC LIMIT 100")
    fun observeUsage(): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM api_request_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeLogs(): Flow<List<ApiRequestLogEntity>>

    @Insert
    suspend fun insertUsage(usage: UsageRecordEntity)

    @Insert
    suspend fun insertLog(log: ApiRequestLogEntity)

    @Query("DELETE FROM api_request_logs")
    suspend fun clearLogs()
}

@Dao
interface ResearchSourceDao {
    @Query("SELECT * FROM research_sources ORDER BY savedAt DESC")
    fun observeSources(): Flow<List<ResearchSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<ResearchSourceEntity>)

    @Query("DELETE FROM research_sources WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface UserPreferenceDao {
    @Query("SELECT * FROM user_preferences")
    fun observeAll(): Flow<List<UserPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: UserPreferenceEntity)
}
