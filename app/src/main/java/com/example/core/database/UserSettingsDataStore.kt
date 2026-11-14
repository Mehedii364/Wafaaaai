package com.example.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wafaSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wafa_user_settings"
)

data class UserSettings(
    val language: String = "en", // "en" or "bn"
    val themeMode: String = "dark", // "dark", "light", "system"
    val routingMode: String = "AUTO", // "AUTO" or "MANUAL"
    val preferredProviderId: String = "xpiki",
    val preferredModelId: String = "",
    val enableFallback: Boolean = true,
    val autoSpeakReplies: Boolean = false,
    val voiceLocale: String = "en-US", // "en-US" or "bn-BD"
    val ttsSpeechRate: Float = 1.0f,
    val backendProxyUrl: String = ""
)

class UserSettingsDataStore(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ROUTING_MODE = stringPreferencesKey("routing_mode")
        val PREFERRED_PROVIDER = stringPreferencesKey("preferred_provider")
        val PREFERRED_MODEL = stringPreferencesKey("preferred_model")
        val ENABLE_FALLBACK = booleanPreferencesKey("enable_fallback")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val VOICE_LOCALE = stringPreferencesKey("voice_locale")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val BACKEND_PROXY_URL = stringPreferencesKey("backend_proxy_url")
    }

    val settingsFlow: Flow<UserSettings> = context.wafaSettingsDataStore.data.map { prefs ->
        UserSettings(
            language = prefs[Keys.LANGUAGE] ?: "en",
            themeMode = prefs[Keys.THEME_MODE] ?: "dark",
            routingMode = prefs[Keys.ROUTING_MODE] ?: "AUTO",
            preferredProviderId = prefs[Keys.PREFERRED_PROVIDER] ?: "xpiki",
            preferredModelId = prefs[Keys.PREFERRED_MODEL] ?: "",
            enableFallback = prefs[Keys.ENABLE_FALLBACK] ?: true,
            autoSpeakReplies = prefs[Keys.AUTO_SPEAK] ?: false,
            voiceLocale = prefs[Keys.VOICE_LOCALE] ?: "en-US",
            ttsSpeechRate = prefs[Keys.TTS_RATE] ?: 1.0f,
            backendProxyUrl = prefs[Keys.BACKEND_PROXY_URL] ?: ""
        )
    }

    suspend fun updateLanguage(lang: String) {
        context.wafaSettingsDataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun updateThemeMode(mode: String) {
        context.wafaSettingsDataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun updateRoutingMode(mode: String) {
        context.wafaSettingsDataStore.edit { it[Keys.ROUTING_MODE] = mode }
    }

    suspend fun updatePreferredProvider(providerId: String, modelId: String = "") {
        context.wafaSettingsDataStore.edit {
            it[Keys.PREFERRED_PROVIDER] = providerId
            if (modelId.isNotBlank()) it[Keys.PREFERRED_MODEL] = modelId
        }
    }

    suspend fun updateFallback(enabled: Boolean) {
        context.wafaSettingsDataStore.edit { it[Keys.ENABLE_FALLBACK] = enabled }
    }

    suspend fun updateVoiceSettings(autoSpeak: Boolean, locale: String, rate: Float) {
        context.wafaSettingsDataStore.edit {
            it[Keys.AUTO_SPEAK] = autoSpeak
            it[Keys.VOICE_LOCALE] = locale
            it[Keys.TTS_RATE] = rate.coerceIn(0.6f, 1.8f)
        }
    }

    suspend fun updateBackendProxyUrl(url: String) {
        context.wafaSettingsDataStore.edit { it[Keys.BACKEND_PROXY_URL] = url.trim() }
    }
}
