package com.example.feature.research

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.TaskCategory
import com.example.core.database.ResearchSourceEntity
import com.example.core.network.WebCitation
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.VioletPulse
import kotlinx.coroutines.launch

@Composable
fun ResearchEngineScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedSources by viewModel.researchSources.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var citations by remember { mutableStateOf<List<WebCitation>>(emptyList()) }
    var urlMarkdown by remember { mutableStateOf("") }
    var synthesis by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isSynthesizing by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Research Engine",
            subtitle = "Live Web, Wikipedia, arXiv & Crossref • Zero Fabricated Citations",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Research topic, academic query, or fact-check") },
                    modifier = Modifier.fillMaxWidth().testTag("research_query_input"),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !isSearching && query.isNotBlank(),
                        onClick = {
                            scope.launch {
                                isSearching = true
                                statusMsg = null
                                val found = viewModel.repository.webTools.searchVerifiedSources(query)
                                citations = found
                                isSearching = false
                                if (found.isNotEmpty()) {
                                    viewModel.repository.db.researchSourceDao().insertAll(
                                        found.map {
                                            ResearchSourceEntity(
                                                query = query,
                                                title = it.title,
                                                url = it.url,
                                                snippet = it.snippet,
                                                factType = "VERIFIED_SOURCE"
                                            )
                                        }
                                    )
                                    statusMsg = "Found ${found.size} verified external sources"
                                } else {
                                    statusMsg = "No external results returned (check connectivity or refine query)"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("research_search_button")
                    ) { Text(if (isSearching) "Searching..." else "Live Source Search") }

                    FilledTonalButton(
                        enabled = !isSynthesizing && (citations.isNotEmpty() || urlMarkdown.isNotBlank() || query.isNotBlank()),
                        onClick = {
                            isSynthesizing = true
                            synthesis = ""
                            val sourceContext = buildString {
                                citations.forEachIndexed { idx, c ->
                                    append("[${idx + 1}] (${c.sourceName}) ${c.title} - ${c.url}\n${c.snippet}\n\n")
                                }
                                if (urlMarkdown.isNotBlank()) {
                                    append("Website Extract:\n${urlMarkdown.take(5000)}")
                                }
                            }
                            val sys = """
                                You are a rigorous research analyst.
                                Structure your response strictly into three labeled sections:
                                1. VERIFIED FACTS FROM SOURCES (cite [1], [2]... only from provided sources; never invent URLs)
                                2. ASSUMPTIONS & GAPS
                                3. AI-GENERATED CONCLUSIONS
                            """.trimIndent()
                            viewModel.runSingleShotAiTask(
                                taskCategory = TaskCategory.RESEARCH,
                                systemInstruction = sys,
                                userPrompt = "Research Question: $query\n\nRetrieved Sources:\n${sourceContext.ifBlank { "No external sources retrieved yet; clearly mark everything as unverified AI knowledge." }}",
                                onDelta = { synthesis = it },
                                onDone = { final, err -> isSynthesizing = false; if (err != null) statusMsg = err else synthesis = final }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isSynthesizing) "Synthesizing..." else "AI Synthesis") }
                }
            }

            // Website URL Analyzer
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Analyze website URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        enabled = urlInput.isNotBlank(),
                        onClick = {
                            scope.launch {
                                statusMsg = "Fetching webpage markdown..."
                                viewModel.repository.webTools.fetchUrlMarkdown(urlInput)
                                    .onSuccess {
                                        urlMarkdown = it
                                        statusMsg = "Loaded ${it.length} chars from URL"
                                    }
                                    .onFailure { statusMsg = it.message }
                            }
                        }
                    ) { Text("Read URL") }
                }
            }

            if (statusMsg != null) {
                item { WafaHudBadge(statusMsg!!, ElectricCyan) }
            }

            if (citations.isNotEmpty()) {
                item {
                    Text("Verified External Sources (${citations.size})", style = MaterialTheme.typography.titleMedium, color = EmeraldSignal)
                }
                items(citations) { c ->
                    WafaGlassCard(
                        accentColor = EmeraldSignal,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WafaHudBadge(c.sourceName, EmeraldSignal)
                        Spacer(Modifier.height(4.dp))
                        Text(c.title, style = MaterialTheme.typography.titleSmall)
                        Text(c.url, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        if (c.snippet.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(c.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (synthesis.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = VioletPulse, modifier = Modifier.fillMaxWidth()) {
                        Text("Structured Research Report", style = MaterialTheme.typography.titleSmall, color = VioletPulse)
                        Spacer(Modifier.height(6.dp))
                        WafaRichTextContent(text = synthesis)
                    }
                }
            }

            if (savedSources.isNotEmpty() && citations.isEmpty()) {
                item {
                    Text("Saved Source History (${savedSources.size})", style = MaterialTheme.typography.titleSmall)
                }
                items(savedSources.take(8), key = { it.id }) { s ->
                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.titleSmall)
                                Text(s.url, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                            }
                            IconButton(onClick = {
                                scope.launch { viewModel.repository.db.researchSourceDao().deleteById(s.id) }
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete source")
                            }
                        }
                    }
                }
            }
        }
    }
}
