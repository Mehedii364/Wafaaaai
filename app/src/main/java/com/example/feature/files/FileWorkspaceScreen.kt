package com.example.feature.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.TaskCategory
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.VioletPulse
import java.io.File

@Composable
fun FileWorkspaceScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsStateWithLifecycle()
    val artifacts by viewModel.artifacts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPreview by remember { mutableStateOf("") }
    var aiSummary by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }

    val safPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, size, preview) = viewModel.repository.docEngine.readFileMetaAndPreview(uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            viewModel.saveUploadedFileRecord(name, mime, size, uri.toString(), preview)
            selectedPreview = preview
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Unified File Workspace",
            subtitle = "SAF Upload • Preview • Summarize • Share",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { safPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Upload File", tint = ElectricCyan)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search uploaded & generated files...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = { safPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import File via Storage Access Framework")
                }
            }

            if (selectedPreview.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        Text("Selected File Preview", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                        Spacer(Modifier.height(6.dp))
                        Text(selectedPreview.take(1500), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            enabled = !isBusy,
                            onClick = {
                                isBusy = true
                                aiSummary = ""
                                viewModel.runSingleShotAiTask(
                                    taskCategory = TaskCategory.DOCUMENT_ANALYSIS,
                                    systemInstruction = "Summarize and extract structured insights from this file content.",
                                    userPrompt = selectedPreview,
                                    onDelta = { aiSummary = it },
                                    onDone = { final, _ -> isBusy = false; aiSummary = final }
                                )
                            }
                        ) { Text(if (isBusy) "Analyzing..." else "AI Analyze File") }
                    }
                }
            }

            if (aiSummary.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        WafaRichTextContent(text = aiSummary)
                    }
                }
            }

            val filteredFiles = files.filter {
                searchQuery.isBlank() || it.displayName.contains(searchQuery, true)
            }
            if (filteredFiles.isNotEmpty()) {
                item { Text("Imported Files (${filteredFiles.size})", style = MaterialTheme.typography.titleMedium) }
                items(filteredFiles, key = { it.id }) { f ->
                    WafaGlassCard(
                        accentColor = VioletPulse,
                        onClick = { selectedPreview = f.extractedSummary },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(f.displayName, style = MaterialTheme.typography.titleSmall)
                                Text("${f.mimeType} • ${f.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.deleteFileRecord(f.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete record")
                            }
                        }
                    }
                }
            }

            val filteredArtifacts = artifacts.filter {
                searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.format.contains(searchQuery, true)
            }
            if (filteredArtifacts.isNotEmpty()) {
                item { Text("Generated Studio Artifacts (${filteredArtifacts.size})", style = MaterialTheme.typography.titleMedium) }
                items(filteredArtifacts, key = { it.id }) { art ->
                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${art.title} (${art.format})", style = MaterialTheme.typography.titleSmall)
                                Text(art.previewText.ifBlank { "${art.byteSize / 1024} KB" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                val file = File(art.filePath)
                                if (file.exists()) shareGeneratedFile(context, file, mimeForFormat(art.format))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share artifact")
                            }
                            IconButton(onClick = { viewModel.deleteArtifact(art) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete artifact")
                            }
                        }
                    }
                }
            }

            if (filteredFiles.isEmpty() && filteredArtifacts.isEmpty()) {
                item {
                    WafaEmptyState(
                        icon = Icons.Default.FolderOpen,
                        title = "No Files Yet",
                        subtitle = "Import documents, code, audio, or spreadsheets using Storage Access Framework, or create DOCX/XLSX/PPTX/PDF in Studios."
                    )
                }
            }
        }
    }
}
