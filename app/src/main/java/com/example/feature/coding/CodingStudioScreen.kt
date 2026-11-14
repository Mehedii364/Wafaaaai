package com.example.feature.coding

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
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VioletPulse

@Composable
fun CodingStudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val languages = listOf("Kotlin", "Python", "JavaScript", "HTML/CSS", "PHP", "Java", "C/C++", "SQL", "JSON", "XML", "YAML", "Shell")
    val actions = listOf("Generate", "Multi-File Project", "Explain", "Debug", "Code Review", "Refactor", "Optimize", "Unit Tests", "Documentation")

    var selectedLanguage by remember { mutableStateOf("Kotlin") }
    var selectedAction by remember { mutableStateOf("Generate") }
    var promptInput by remember { mutableStateOf("") }
    var codeOutput by remember { mutableStateOf("") }
    var projectFiles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedFileKey by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Coding Studio",
            subtitle = "Multi-Language Engineering • Project ZIP Export",
            onBack = onBack,
            trailing = {
                if (projectFiles.isNotEmpty() || codeOutput.isNotBlank()) {
                    IconButton(onClick = {
                        val filesToZip = if (projectFiles.isNotEmpty()) {
                            projectFiles
                        } else {
                            val ext = extForLanguage(selectedLanguage)
                            mapOf("src/Main.$ext" to codeOutput, "README.md" to "# WAFA AI Generated Code\nLanguage: $selectedLanguage\n")
                        }
                        val zipInfo = viewModel.repository.docEngine.createProjectZip(
                            filesToZip,
                            "wafa_project_${System.currentTimeMillis()}"
                        )
                        viewModel.recordGeneratedArtifact("CODING", zipInfo.file.name, "ZIP", zipInfo.file, zipInfo.previewSummary)
                        statusMsg = "Exported ${zipInfo.previewSummary}"
                        shareGeneratedFile(context, zipInfo.file, "application/zip")
                    }) {
                        Icon(Icons.Default.FolderZip, contentDescription = "Export Project ZIP", tint = ElectricCyan)
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Language", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languages.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(lang) }
                        )
                    }
                }
            }

            item {
                Text("Engineering Action", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    actions.forEach { act ->
                        FilterChip(
                            selected = selectedAction == act,
                            onClick = { selectedAction = act },
                            label = { Text(act) }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("Describe feature or paste source code") },
                    placeholder = { Text("e.g. Build a REST client in $selectedLanguage with retry logic") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp)
                        .testTag("coding_prompt_input"),
                    maxLines = 8
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (promptInput.isBlank() || isRunning) return@Button
                            isRunning = true
                            statusMsg = null
                            codeOutput = ""
                            val sys = """
                                You are a senior software engineer in WAFA AI Coding Studio.
                                Language: $selectedLanguage. Action: $selectedAction.
                                If Action is 'Multi-File Project', output each file strictly using headers like:
                                === FILE: path/to/file.ext ===
                                ```lang
                                code...
                                ```
                                Otherwise output clean, production-grade code in fenced markdown blocks.
                                Never claim code was compiled or executed unless actually run.
                            """.trimIndent()
                            viewModel.runSingleShotAiTask(
                                taskCategory = TaskCategory.CODING,
                                systemInstruction = sys,
                                userPrompt = promptInput,
                                onDelta = { codeOutput = it },
                                onDone = { finalText, err ->
                                    isRunning = false
                                    if (err != null) {
                                        statusMsg = err
                                    } else {
                                        codeOutput = finalText
                                        val parsed = parseMultiFileOutput(finalText)
                                        projectFiles = parsed
                                        selectedFileKey = parsed.keys.firstOrNull()
                                    }
                                }
                            )
                        },
                        enabled = !isRunning,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("coding_generate_button")
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isRunning) "Synthesizing..." else "Run $selectedAction")
                    }
                }
            }

            if (statusMsg != null) {
                item {
                    WafaHudBadge(statusMsg!!, VioletPulse)
                }
            }

            if (projectFiles.isNotEmpty()) {
                item {
                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        Text("Project Workspace (${projectFiles.size} files)", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            projectFiles.keys.forEach { path ->
                                FilterChip(
                                    selected = selectedFileKey == path,
                                    onClick = { selectedFileKey = path },
                                    label = { Text(path) }
                                )
                            }
                        }
                        val activeCode = projectFiles[selectedFileKey].orEmpty()
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedFileKey.orEmpty(), style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                            IconButton(onClick = { clipboard.setText(AnnotatedString(activeCode)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy file")
                            }
                        }
                        Text(
                            text = activeCode,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (codeOutput.isNotBlank() && projectFiles.isEmpty()) {
                item {
                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        WafaRichTextContent(text = codeOutput)
                    }
                }
            }
        }
    }
}

private fun parseMultiFileOutput(raw: String): Map<String, String> {
    val map = linkedMapOf<String, String>()
    val regex = Regex("=== FILE:\\s*([^\\n=]+?)\\s*===\\s*\\n```[a-zA-Z0-9_+\\-]*\\n([\\s\\S]*?)```")
    regex.findAll(raw).forEach { m ->
        val path = m.groupValues[1].trim()
        val content = m.groupValues[2].trimEnd()
        if (path.isNotBlank()) map[path] = content
    }
    return map
}

private fun extForLanguage(lang: String): String = when (lang) {
    "Kotlin" -> "kt"
    "Python" -> "py"
    "JavaScript" -> "js"
    "HTML/CSS" -> "html"
    "PHP" -> "php"
    "Java" -> "java"
    "C/C++" -> "cpp"
    "SQL" -> "sql"
    "JSON" -> "json"
    "XML" -> "xml"
    "YAML" -> "yaml"
    else -> "sh"
}
