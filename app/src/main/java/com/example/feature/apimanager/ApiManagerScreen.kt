package com.example.feature.apimanager

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.ExternalApiCatalog
import com.example.core.database.AIModelEntity
import com.example.core.database.AIProviderEntity
import com.example.core.i18n.AppStrings
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.VioletPulse
import kotlinx.coroutines.launch

@Composable
fun ApiManagerScreen(
    viewModel: MainViewModel,
    strings: AppStrings,
    onBack: (() -> Unit)? = null
) {
    var activeTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = strings.apiManager,
            subtitle = "12 AI Providers (XPiKi, Gemini...) • 75-API Registry • Keystore Vault",
            onBack = onBack
        )

        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Providers (12)") })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("75 APIs") })
            Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Logs & Vault") })
        }

        when (activeTab) {
            0 -> ProvidersTab(viewModel, strings)
            1 -> ExternalRegistryTab()
            else -> TelemetryAndVaultTab(viewModel)
        }
    }
}

@Composable
private fun ProvidersTab(viewModel: MainViewModel, strings: AppStrings) {
    val scope = rememberCoroutineScope()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var editingProvider by remember { mutableStateOf<AIProviderEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var testBanner by remember { mutableStateOf<String?>(null) }
    var testingProviderId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("api_providers_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = settings.enableFallback,
                        onCheckedChange = { viewModel.updateFallbackEnabled(it) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Auto Fallback Chain", style = MaterialTheme.typography.titleSmall)
                }
                Button(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_provider_button")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Provider")
                }
            }
        }

        if (testBanner != null) {
            item { WafaHudBadge(testBanner!!, ElectricCyan) }
        }

        items(providers, key = { it.id }) { provider ->
            val hasKey = viewModel.repository.vault.hasApiKey(provider.id)
            val masked = viewModel.repository.vault.getMaskedApiKey(provider.id)
            val pModels = models.filter { it.providerId == provider.id }
            val statusColor = when (provider.connectionStatus) {
                "CONNECTED" -> EmeraldSignal
                "ERROR", "RATE_LIMITED" -> Color(0xFFEF4444)
                else -> if (hasKey) ElectricCyan else Color(0xFFF59E0B)
            }

            WafaGlassCard(
                accentColor = statusColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provider_card_${provider.id}")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(provider.name, style = MaterialTheme.typography.titleMedium)
                            WafaHudBadge("#${provider.priority}", ElectricCyan)
                            WafaHudBadge(provider.connectionStatus, statusColor)
                        }
                        Text("${provider.category} • ${provider.authMethod}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Model: ${provider.selectedModelId} (${pModels.size} available)", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        Text("Key: $masked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = provider.isEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.repository.db.providerDao().upsert(provider.copy(isEnabled = enabled)) }
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { editingProvider = provider },
                        modifier = Modifier.testTag("edit_provider_${provider.id}")
                    ) { Text("Configure Key & Model") }

                    OutlinedButton(
                        enabled = testingProviderId != provider.id,
                        onClick = {
                            scope.launch {
                                testingProviderId = provider.id
                                val res = viewModel.repository.testAndDiscoverProvider(provider)
                                testingProviderId = null
                                testBanner = "${provider.name}: ${res.message}"
                            }
                        },
                        modifier = Modifier.testTag("test_provider_${provider.id}")
                    ) {
                        Text(if (testingProviderId == provider.id) "Testing..." else strings.testConnection)
                    }

                    if (provider.isCustomProxy && provider.id != "openai_compat" && provider.id != "local_ollama") {
                        OutlinedButton(onClick = {
                            scope.launch {
                                viewModel.repository.vault.clearApiKey(provider.id)
                                viewModel.repository.db.providerDao().deleteById(provider.id)
                            }
                        }) { Text("Remove") }
                    }
                }
            }
        }
    }

    if (editingProvider != null) {
        ProviderConfigDialog(
            provider = editingProvider!!,
            models = models.filter { it.providerId == editingProvider!!.id },
            existingMasked = viewModel.repository.vault.getMaskedApiKey(editingProvider!!.id),
            existingAccountId = viewModel.repository.vault.getAccountIdentifier(editingProvider!!.id).orEmpty(),
            onDismiss = { editingProvider = null },
            onSave = { updatedProvider, newKey, acctId ->
                scope.launch {
                    viewModel.repository.saveProviderConfig(updatedProvider, newKey, acctId)
                    editingProvider = null
                    testBanner = "${updatedProvider.name} configuration encrypted & saved."
                }
            },
            onClearKey = {
                viewModel.repository.vault.clearApiKey(editingProvider!!.id)
                scope.launch {
                    viewModel.repository.db.providerDao().upsert(editingProvider!!.copy(connectionStatus = "UNCONFIGURED"))
                }
                editingProvider = null
                testBanner = "API key wiped from Android Keystore vault."
            }
        )
    }

    if (showAddDialog) {
        AddCustomProviderDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name, baseUrl, modelId ->
                scope.launch {
                    val id = "custom_${System.currentTimeMillis()}"
                    val entity = AIProviderEntity(
                        id = id,
                        name = name,
                        category = "Custom OpenAI-Compatible",
                        baseUrl = baseUrl.trimEnd('/'),
                        authMethod = "Bearer API Key",
                        isEnabled = true,
                        priority = 15,
                        selectedModelId = modelId,
                        isCustomProxy = true
                    )
                    viewModel.repository.db.providerDao().upsert(entity)
                    viewModel.repository.db.modelDao().upsertAll(
                        listOf(AIModelEntity(id, modelId, modelId))
                    )
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun ProviderConfigDialog(
    provider: AIProviderEntity,
    models: List<AIModelEntity>,
    existingMasked: String,
    existingAccountId: String,
    onDismiss: () -> Unit,
    onSave: (AIProviderEntity, String?, String?) -> Unit,
    onClearKey: () -> Unit
) {
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf(existingAccountId) }
    var selectedModel by remember { mutableStateOf(provider.selectedModelId) }
    var priorityStr by remember { mutableStateOf(provider.priority.toString()) }
    var expandedModels by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${provider.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current stored key: $existingMasked", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Enter New API Key (leave blank to keep existing)") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle key visibility")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("provider_key_input")
                )
                if (provider.baseUrl.contains("{accountId}") || provider.id == "cloudflare") {
                    OutlinedTextField(
                        value = accountId,
                        onValueChange = { accountId = it },
                        label = { Text("Cloudflare Account ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = { selectedModel = it },
                        label = { Text("Model ID") },
                        trailingIcon = {
                            IconButton(onClick = { expandedModels = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose discovered model")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = expandedModels, onDismissRequest = { expandedModels = false }) {
                        models.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("${m.displayName} (${m.contextWindow / 1000}K)") },
                                onClick = {
                                    selectedModel = m.modelId
                                    expandedModels = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = priorityStr,
                    onValueChange = { priorityStr = it },
                    label = { Text("Routing Priority (1 = highest)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = provider.copy(
                        baseUrl = baseUrl.trim().trimEnd('/'),
                        selectedModelId = selectedModel.trim().ifBlank { provider.selectedModelId },
                        priority = priorityStr.toIntOrNull()?.coerceIn(1, 99) ?: provider.priority
                    )
                    val keyToSave = if (apiKeyInput.isNotBlank()) apiKeyInput else null
                    onSave(updated, keyToSave, accountId)
                },
                modifier = Modifier.testTag("save_provider_dialog_button")
            ) { Text("Encrypt & Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onClearKey) { Text("Wipe Key", color = Color(0xFFEF4444)) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun AddCustomProviderDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://") }
    var modelId by remember { mutableStateOf("gpt-4o-mini") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add OpenAI-Compatible Provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Provider Name") }, singleLine = true)
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("API Base URL (e.g. https://api.example.com/v1)") }, singleLine = true)
                OutlinedTextField(value = modelId, onValueChange = { modelId = it }, label = { Text("Default Model ID") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && baseUrl.startsWith("http"),
                onClick = { onCreate(name.trim(), baseUrl.trim(), modelId.trim()) }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ExternalRegistryTab() {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val filtered = remember(search, selectedCategory) {
        ExternalApiCatalog.integrations.filter { item ->
            (selectedCategory == "All" || item.category == selectedCategory) &&
                (search.isBlank() || item.name.contains(search, true) || item.capabilities.contains(search, true))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("external_75_api_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search ${ExternalApiCatalog.integrations.size} documented APIs...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExternalApiCatalog.categories.forEach { cat ->
                    FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat) })
                }
            }
        }

        items(filtered, key = { it.id }) { api ->
            WafaGlassCard(
                accentColor = if (api.liveCallableInApp) EmeraldSignal else VioletPulse,
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.docsUrl))) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WafaHudBadge("#${api.id} ${api.category}", ElectricCyan)
                    WafaHudBadge(api.freeTierStatus, if (api.liveCallableInApp) EmeraldSignal else VioletPulse)
                }
                Spacer(Modifier.height(4.dp))
                Text(api.name, style = MaterialTheme.typography.titleSmall)
                Text(api.capabilities, style = MaterialTheme.typography.bodySmall)
                Text("Auth: ${api.authMethod} • Limits: ${api.usageLimits}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(api.baseUrl, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
            }
        }
    }
}

@Composable
private fun TelemetryAndVaultTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val usage by viewModel.usageRecords.collectAsStateWithLifecycle()
    val logs by viewModel.apiLogs.collectAsStateWithLifecycle()
    var importJsonText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                Text("BYOK Device Vault Security Disclosure", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Personal API keys are encrypted locally using Android Keystore (AES-256-GCM) and never included in exports, logs, or source code. For multi-user or production-sensitive workloads, configure a private backend proxy URL in Settings.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val json = viewModel.repository.exportNonSecretProvidersJson()
                            val info = viewModel.repository.docEngine.writePlainFile(json, "wafa_providers_no_secrets", "json")
                            clipboard.setText(AnnotatedString(json))
                            shareGeneratedFile(context, info.file, "application/json")
                            feedback = "Exported non-secret provider config (keys stripped)."
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Export Config") }

                    OutlinedButton(onClick = { showImportDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("Import Config")
                    }
                }
            }
        }

        if (feedback != null) {
            item { WafaHudBadge(feedback!!, EmeraldSignal) }
        }

        item {
            val totalRequests = usage.size
            val okRequests = usage.count { it.succeeded }
            val totalTokens = usage.sumOf { it.promptTokensEst + it.completionTokensEst }
            WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                Text("Usage Telemetry", style = MaterialTheme.typography.titleSmall)
                Text("Requests: $okRequests / $totalRequests succeeded • Est. Tokens: $totalTokens", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Redacted API Logs (${logs.size})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.clearLogs() }) { Text("Clear Logs") }
            }
        }

        items(logs, key = { it.id }) { log ->
            WafaGlassCard(
                accentColor = if (log.statusCode in 200..299) EmeraldSignal else Color(0xFFEF4444),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${log.providerId.uppercase()} ${log.endpoint} -> HTTP ${log.statusCode} (${log.latencyMs}ms)", style = MaterialTheme.typography.labelMedium)
                if (log.redactedError.isNotBlank()) {
                    Text(log.redactedError, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Non-Secret Provider JSON") },
            text = {
                OutlinedTextField(value = importJsonText, onValueChange = { importJsonText = it }, maxLines = 8)
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        viewModel.repository.importNonSecretProvidersJson(importJsonText)
                            .onSuccess { feedback = "Imported $it provider profiles." }
                            .onFailure { feedback = "Import error: ${it.message}" }
                        showImportDialog = false
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }
}
