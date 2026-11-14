package com.example

import android.app.Application
import com.example.core.ai.ModelRouter
import com.example.core.data.WafaRepository
import com.example.core.database.UserSettingsDataStore
import com.example.core.database.WafaDatabase
import com.example.core.files.OoxmlDocumentEngine
import com.example.core.network.AiGatewayClient
import com.example.core.network.LiveWebToolsClient
import com.example.core.network.NetworkMonitor
import com.example.core.security.SecureCredentialVault
import com.example.core.voice.VoiceEngine

class AppContainer(application: Application) {
    val database: WafaDatabase by lazy { WafaDatabase.getInstance(application) }
    val vault: SecureCredentialVault by lazy { SecureCredentialVault(application) }
    val settingsStore: UserSettingsDataStore by lazy { UserSettingsDataStore(application) }
    val aiGateway: AiGatewayClient by lazy { AiGatewayClient() }
    val modelRouter: ModelRouter by lazy { ModelRouter() }
    val webTools: LiveWebToolsClient by lazy { LiveWebToolsClient() }
    val docEngine: OoxmlDocumentEngine by lazy { OoxmlDocumentEngine(application) }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(application) }
    val voiceEngine: VoiceEngine by lazy { VoiceEngine(application) }

    val repository: WafaRepository by lazy {
        WafaRepository(
            context = application,
            db = database,
            vault = vault,
            settingsStore = settingsStore,
            gateway = aiGateway,
            router = modelRouter,
            webTools = webTools,
            docEngine = docEngine,
            networkMonitor = networkMonitor
        )
    }
}

class WafaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
