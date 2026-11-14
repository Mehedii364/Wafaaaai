package com.example.feature.spreadsheets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.ai.TaskCategory
import com.example.core.files.SpreadsheetSheet
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun SpreadsheetStudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var workbookTitle by remember { mutableStateOf("Monthly_Budget_Analysis") }
    var sheet1Csv by remember {
        mutableStateOf(
            "Category,Planned,Actual\nCloud APIs,50,42\nStorage,20,18\nDomains,15,15\nTotal,=SUM(B2:B4),=SUM(C2:C4)"
        )
    }
    var sheet2Csv by remember {
        mutableStateOf("Metric,Value\nBudget Variance,=B5-C5\nAverage Planned,=AVERAGE(B2:B4)")
    }
    var activeSheetIndex by remember { mutableStateOf(0) }
    var aiPrompt by remember { mutableStateOf("") }
    var aiAnalysis by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val openCsvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, _, preview) = viewModel.repository.docEngine.readFileMetaAndPreview(uri)
            workbookTitle = name.substringBeforeLast('.')
            sheet1Csv = preview
            statusMsg = "Imported $name"
        }
    }

    val activeRows = remember(sheet1Csv, sheet2Csv, activeSheetIndex) {
        val raw = if (activeSheetIndex == 0) sheet1Csv else sheet2Csv
        raw.lines().filter { it.isNotBlank() }.map { line -> line.split(",").map { it.trim() } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Excel & Data Studio",
            subtitle = "Multi-Sheet XLSX • Live Formulas • CSV",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { openCsvPicker.launch(arrayOf("text/*", "*/*")) }) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Import CSV/XLSX", tint = EmeraldSignal)
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
                    value = workbookTitle,
                    onValueChange = { workbookTitle = it },
                    label = { Text("Workbook Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activeSheetIndex == 0,
                        onClick = { activeSheetIndex = 0 },
                        label = { Text("Sheet 1 (Data)") }
                    )
                    FilterChip(
                        selected = activeSheetIndex == 1,
                        onClick = { activeSheetIndex = 1 },
                        label = { Text("Sheet 2 (Summary)") }
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = if (activeSheetIndex == 0) sheet1Csv else sheet2Csv,
                    onValueChange = {
                        if (activeSheetIndex == 0) sheet1Csv = it else sheet2Csv = it
                    },
                    label = { Text("CSV / Formula Grid (supports =SUM, =AVERAGE, =MIN, =MAX, =COUNT)") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("spreadsheet_csv_input"),
                    maxLines = 12
                )
            }

            // Live Evaluated Table Preview
            item {
                WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                    Text("Live Evaluated Grid Preview", style = MaterialTheme.typography.titleSmall, color = EmeraldSignal)
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        activeRows.forEachIndexed { rIdx, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { cell ->
                                    val display = if (cell.startsWith("=")) {
                                        val evaluated = viewModel.repository.docEngine.evaluateFormulaOrNull(cell, activeRows)
                                        if (evaluated != null) String.format("%.2f", evaluated) else cell
                                    } else cell
                                    Surface(
                                        color = if (rIdx == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = display,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.widthIn(min = 84.dp).padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Export Real XLSX / CSV
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val s1Rows = sheet1Csv.lines().filter { it.isNotBlank() }.map { it.split(",").map { c -> c.trim() } }
                            val s2Rows = sheet2Csv.lines().filter { it.isNotBlank() }.map { it.split(",").map { c -> c.trim() } }
                            val info = viewModel.repository.docEngine.createXlsx(
                                listOf(SpreadsheetSheet("Data", s1Rows), SpreadsheetSheet("Summary", s2Rows)),
                                workbookTitle
                            )
                            viewModel.recordGeneratedArtifact("SPREADSHEET", info.file.name, "XLSX", info.file, info.previewSummary)
                            statusMsg = "Generated ${info.previewSummary}"
                            shareGeneratedFile(context, info.file, mimeForFormat("XLSX"))
                        },
                        modifier = Modifier.weight(1f).testTag("export_xlsx_button")
                    ) { Text("Export XLSX") }

                    OutlinedButton(
                        onClick = {
                            val csv = if (activeSheetIndex == 0) sheet1Csv else sheet2Csv
                            val info = viewModel.repository.docEngine.writePlainFile(csv, workbookTitle, "csv")
                            viewModel.recordGeneratedArtifact("SPREADSHEET", info.file.name, "CSV", info.file, info.previewSummary)
                            statusMsg = "Generated ${info.previewSummary}"
                            shareGeneratedFile(context, info.file, mimeForFormat("CSV"))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Export CSV") }
                }
            }

            // AI Formula / Data Analysis
            item {
                OutlinedTextField(
                    value = aiPrompt,
                    onValueChange = { aiPrompt = it },
                    label = { Text("Ask AI to generate CSV/formulas or analyze this data") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = !isRunning,
                        onClick = {
                            isRunning = true
                            aiAnalysis = ""
                            viewModel.runSingleShotAiTask(
                                taskCategory = TaskCategory.SPREADSHEET,
                                systemInstruction = "Analyze this spreadsheet data. Provide summary statistics, anomalies, pivot-style breakdown, and useful Excel formulas.",
                                userPrompt = "Request: ${aiPrompt.ifBlank { "Analyze data" }}\n\nSheet:\n$sheet1Csv",
                                onDelta = { aiAnalysis = it },
                                onDone = { final, err -> isRunning = false; if (err != null) statusMsg = err else aiAnalysis = final }
                            )
                        }
                    ) { Text(if (isRunning) "Analyzing..." else "AI Analyze Data") }
                }
            }

            if (statusMsg != null) {
                item { WafaHudBadge(statusMsg!!, ElectricCyan) }
            }

            if (aiAnalysis.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        WafaRichTextContent(text = aiAnalysis)
                    }
                }
            }
        }
    }
}
