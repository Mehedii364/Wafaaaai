package com.example.feature.writing

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.core.ai.TaskCategory
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal

@Composable
fun WritingStudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val modes = listOf(
        "Article", "Blog Post", "Official Application", "Professional Letter",
        "Email", "SMS Message", "Social Post", "Report", "Story",
        "Script", "Documentation", "Resume / CV", "Translate (EN <-> BN)",
        "Summarize", "Rewrite / Proofread"
    )
    val tones = listOf("Professional", "Formal", "Warm & Clear", "Concise", "Persuasive", "Academic")
    val languages = listOf("English", "Bengali (বাংলা)", "Bilingual (EN + BN)")

    var selectedMode by remember { mutableStateOf("Article") }
    var selectedTone by remember { mutableStateOf("Professional") }
    var selectedLang by remember { mutableStateOf("English") }
    var topicOrDraft by remember { mutableStateOf("") }
    var generatedContent by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Writing Studio",
            subtitle = "Bangla & English • Export DOCX, PDF, MD, TXT",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Format / Mode", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modes.forEach { m ->
                        FilterChip(selected = selectedMode == m, onClick = { selectedMode = m }, label = { Text(m) })
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languages.forEach { l ->
                        FilterChip(selected = selectedLang == l, onClick = { selectedLang = l }, label = { Text(l) })
                    }
                    tones.forEach { t ->
                        FilterChip(selected = selectedTone == t, onClick = { selectedTone = t }, label = { Text(t) })
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = topicOrDraft,
                    onValueChange = { topicOrDraft = it },
                    label = { Text("Topic, instructions, or text to translate/proofread") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp)
                        .testTag("writing_input"),
                    maxLines = 8
                )
            }

            item {
                Button(
                    onClick = {
                        if (topicOrDraft.isBlank() || isRunning) return@Button
                        isRunning = true
                        statusMsg = null
                        generatedContent = ""
                        val sys = """
                            You are an expert bilingual editorial writer in WAFA AI Writing Studio.
                            Task: $selectedMode
                            Language: $selectedLang
                            Tone: $selectedTone
                            Deliver polished, structured text ready for immediate professional use.
                        """.trimIndent()
                        viewModel.runSingleShotAiTask(
                            taskCategory = TaskCategory.WRITING,
                            systemInstruction = sys,
                            userPrompt = topicOrDraft,
                            onDelta = { generatedContent = it },
                            onDone = { final, err ->
                                isRunning = false
                                if (err != null) statusMsg = err else generatedContent = final
                            }
                        )
                    },
                    enabled = !isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("writing_generate_button")
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isRunning) "Composing..." else "Generate $selectedMode")
                }
            }

            if (statusMsg != null) {
                item { WafaHudBadge(statusMsg!!, ColorWarning) }
            }

            if (generatedContent.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = generatedContent,
                            onValueChange = { generatedContent = it },
                            label = { Text("Editable Output") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 18
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(generatedContent)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy")
                            }
                            OutlinedButton(onClick = {
                                val info = viewModel.repository.docEngine.createDocx(selectedMode, generatedContent, "wafa_writing_${System.currentTimeMillis()}")
                                viewModel.recordGeneratedArtifact("WRITING", info.file.name, "DOCX", info.file, info.previewSummary)
                                shareGeneratedFile(context, info.file, mimeForFormat("DOCX"))
                            }) {
                                Text("Export DOCX")
                            }
                            OutlinedButton(onClick = {
                                val info = viewModel.repository.docEngine.createPdf(selectedMode, generatedContent, "wafa_writing_${System.currentTimeMillis()}")
                                viewModel.recordGeneratedArtifact("WRITING", info.file.name, "PDF", info.file, info.previewSummary)
                                shareGeneratedFile(context, info.file, mimeForFormat("PDF"))
                            }) {
                                Text("Export PDF")
                            }
                            OutlinedButton(onClick = {
                                val info = viewModel.repository.docEngine.writePlainFile(generatedContent, "wafa_writing_${System.currentTimeMillis()}", "md")
                                viewModel.recordGeneratedArtifact("WRITING", info.file.name, "MD", info.file, info.previewSummary)
                                shareGeneratedFile(context, info.file, mimeForFormat("MD"))
                            }) {
                                Text("Export MD")
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ColorWarning = androidx.compose.ui.graphics.Color(0xFFF59E0B)
