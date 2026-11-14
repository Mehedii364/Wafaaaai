package com.example.feature.documents

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.ai.TaskCategory
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal

@Composable
fun DocumentStudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var docTitle by remember { mutableStateOf("Executive Project Brief") }
    var docBody by remember {
        mutableStateOf(
            "# Overview\nPrepared inside WAFA AI Document Studio.\n\n## Key Points\n- Native DOCX, PDF, Markdown, TXT, and RTF export\n- On-device Storage Access Framework document reading"
        )
    }
    var secondaryDocText by remember { mutableStateOf("") }
    var showCompare by remember { mutableStateOf(false) }
    var searchTerm by remember { mutableStateOf("") }
    var aiAnalysisOutput by remember { mutableStateOf("") }
    var pdfPreviewBmp by remember { mutableStateOf<Bitmap?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val openPrimaryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, size, preview) = viewModel.repository.docEngine.readFileMetaAndPreview(uri)
            docTitle = name.substringBeforeLast('.')
            docBody = preview
            statusBanner = "Loaded $name (${size / 1024} KB)"
        }
    }

    val openComparePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, _, preview) = viewModel.repository.docEngine.readFileMetaAndPreview(uri)
            secondaryDocText = preview
            showCompare = true
            statusBanner = "Loaded comparison document: $name"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Document Studio",
            subtitle = "DOCX • PDF • Markdown • TXT • RTF",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { openPrimaryPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Open Document", tint = ElectricCyan)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Document Title") },
                    modifier = Modifier.fillMaxWidth().testTag("doc_title_input"),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = docBody,
                    onValueChange = { docBody = it },
                    label = { Text("Document Content (Markdown headings # supported)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("doc_body_input"),
                    maxLines = 14
                )
            }

            // Search inside loaded document
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchTerm,
                        onValueChange = { searchTerm = it },
                        label = { Text("Search in document") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchTerm.isNotBlank()) {
                        val matches = Regex(Regex.escape(searchTerm), RegexOption.IGNORE_CASE).findAll(docBody).count()
                        WafaHudBadge("$matches match(es)", ElectricCyan)
                    }
                }
            }

            // Real Document Exports
            item {
                Text("Generate & Export Real Files", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val info = viewModel.repository.docEngine.createDocx(docTitle, docBody, docTitle)
                            viewModel.recordGeneratedArtifact("DOCUMENT", info.file.name, "DOCX", info.file, info.previewSummary)
                            statusBanner = "Created ${info.previewSummary}"
                            shareGeneratedFile(context, info.file, mimeForFormat("DOCX"))
                        },
                        modifier = Modifier.testTag("export_docx_button")
                    ) { Text("Create DOCX") }

                    Button(
                        onClick = {
                            val info = viewModel.repository.docEngine.createPdf(docTitle, docBody, docTitle)
                            viewModel.recordGeneratedArtifact("DOCUMENT", info.file.name, "PDF", info.file, info.previewSummary)
                            pdfPreviewBmp = viewModel.repository.docEngine.renderPdfFirstPagePreview(info.file)
                            statusBanner = "Created ${info.previewSummary}"
                            shareGeneratedFile(context, info.file, mimeForFormat("PDF"))
                        },
                        modifier = Modifier.testTag("export_pdf_button")
                    ) { Text("Create PDF") }

                    OutlinedButton(onClick = {
                        val info = viewModel.repository.docEngine.writePlainFile(docBody, docTitle, "md")
                        viewModel.recordGeneratedArtifact("DOCUMENT", info.file.name, "MD", info.file, info.previewSummary)
                        statusBanner = "Created ${info.previewSummary}"
                        shareGeneratedFile(context, info.file, "text/markdown")
                    }) { Text("MD") }

                    OutlinedButton(onClick = {
                        val info = viewModel.repository.docEngine.writePlainFile(docBody, docTitle, "txt")
                        viewModel.recordGeneratedArtifact("DOCUMENT", info.file.name, "TXT", info.file, info.previewSummary)
                        statusBanner = "Created ${info.previewSummary}"
                        shareGeneratedFile(context, info.file, "text/plain")
                    }) { Text("TXT") }

                    OutlinedButton(onClick = {
                        val rtf = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Arial;}}\\f0\\fs24 ${docTitle.replace("\n", "\\par ")}\\par\\par ${docBody.replace("\n", "\\par ")}}"
                        val info = viewModel.repository.docEngine.writePlainFile(rtf, docTitle, "rtf")
                        viewModel.recordGeneratedArtifact("DOCUMENT", info.file.name, "RTF", info.file, info.previewSummary)
                        statusBanner = "Created ${info.previewSummary}"
                        shareGeneratedFile(context, info.file, "application/rtf")
                    }) { Text("RTF") }
                }
            }

            // AI Document Intelligence Actions
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        enabled = !isRunning,
                        onClick = {
                            isRunning = true
                            aiAnalysisOutput = ""
                            viewModel.runSingleShotAiTask(
                                taskCategory = TaskCategory.DOCUMENT_ANALYSIS,
                                systemInstruction = "Summarize the document into executive takeaways, key facts, and action items.",
                                userPrompt = "Title: $docTitle\n\n$docBody",
                                onDelta = { aiAnalysisOutput = it },
                                onDone = { final, err -> isRunning = false; if (err != null) statusBanner = err else aiAnalysisOutput = final }
                            )
                        }
                    ) { Text("AI Summarize") }

                    FilledTonalButton(
                        enabled = !isRunning,
                        onClick = {
                            isRunning = true
                            aiAnalysisOutput = ""
                            viewModel.runSingleShotAiTask(
                                taskCategory = TaskCategory.DOCUMENT_ANALYSIS,
                                systemInstruction = "Extract any structured data or tables from the text into clean Markdown tables.",
                                userPrompt = docBody,
                                onDelta = { aiAnalysisOutput = it },
                                onDone = { final, err -> isRunning = false; if (err != null) statusBanner = err else aiAnalysisOutput = final }
                            )
                        }
                    ) { Text("Extract Tables") }

                    OutlinedButton(onClick = { openComparePicker.launch(arrayOf("*/*")) }) {
                        Text("Load 2nd Doc to Compare")
                    }
                }
            }

            if (showCompare && secondaryDocText.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        Text("Document Comparison Ready (${secondaryDocText.length} chars)", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Button(
                            enabled = !isRunning,
                            onClick = {
                                isRunning = true
                                aiAnalysisOutput = ""
                                viewModel.runSingleShotAiTask(
                                    taskCategory = TaskCategory.DOCUMENT_ANALYSIS,
                                    systemInstruction = "Compare Document A and Document B. Highlight differences, additions, removals, and contradictions.",
                                    userPrompt = "=== DOCUMENT A ===\n$docBody\n\n=== DOCUMENT B ===\n$secondaryDocText",
                                    onDelta = { aiAnalysisOutput = it },
                                    onDone = { final, err -> isRunning = false; if (err != null) statusBanner = err else aiAnalysisOutput = final }
                                )
                            }
                        ) { Text("Run AI Document Diff") }
                    }
                }
            }

            if (statusBanner != null) {
                item { WafaHudBadge(statusBanner!!, EmeraldSignal) }
            }

            if (pdfPreviewBmp != null) {
                item {
                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        Text("PDF Page 1 Live Render", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Image(
                            bitmap = pdfPreviewBmp!!.asImageBitmap(),
                            contentDescription = "Rendered PDF first page preview",
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                        )
                    }
                }
            }

            if (aiAnalysisOutput.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        Text("Document Intelligence Output", style = MaterialTheme.typography.titleSmall, color = EmeraldSignal)
                        Spacer(Modifier.height(6.dp))
                        WafaRichTextContent(text = aiAnalysisOutput)
                    }
                }
            }
        }
    }
}
