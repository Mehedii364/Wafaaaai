package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AIProviderEntity::class,
        AIModelEntity::class,
        UserPreferenceEntity::class,
        SavedMemoryEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        FileRecordEntity::class,
        GeneratedArtifactEntity::class,
        AgentExecutionEntity::class,
        UsageRecordEntity::class,
        ApiRequestLogEntity::class,
        ResearchSourceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WafaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun fileRecordDao(): FileRecordDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun agentExecutionDao(): AgentExecutionDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun researchSourceDao(): ResearchSourceDao
    abstract fun userPreferenceDao(): UserPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: WafaDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Prepared migration path for future schema upgrades
            }
        }

        fun getInstance(context: Context): WafaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WafaDatabase::class.java,
                    "wafa_ai_os.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
