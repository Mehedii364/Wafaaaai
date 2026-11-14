package com.example.feature.presentations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.ai.TaskCategory
import com.example.core.files.PresentationSlide
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal

@Composable
fun PresentationStudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var deckTitle by remember { mutableStateOf("WAFA AI Architecture Deck") }
    var topicPrompt by remember { mutableStateOf("") }
    var slides by remember {
        mutableStateOf(
            listOf(
                PresentationSlide("Executive Summary", listOf("Personal Multimodal AI OS", "Multi-provider capability routing")),
                PresentationSlide("Core Architecture", listOf("Jetpack Compose + Material 3", "Room local store + Keystore vault")),
                PresentationSlide("Roadmap & Metrics", listOf("12 AI Providers + 75 APIs", "Real DOCX, XLSX, PPTX, PDF export"))
            )
        )
    }
    var isRunning by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Presentation Studio",
            subtitle = "AI Slide Builder • Real PPTX & PDF Export",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = deckTitle,
                    onValueChange = { deckTitle = it },
                    label = { Text("Presentation Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = topicPrompt,
                        onValueChange = { topicPrompt = it },
                        label = { Text("Topic to generate slides with AI") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = !isRunning && topicPrompt.isNotBlank(),
                        onClick = {
                            isRunning = true
                            statusMsg = null
                            val sys = """
                                Create a 5-slide presentation outline. Format STRICTLY as:
                                SLIDE: Slide Title
                                - Bullet 1
                                - Bullet 2
                                - Bullet 3
                            """.trimIndent()
                            viewModel.runSingleShotAiTask(
                                taskCategory = TaskCategory.PRESENTATION,
                                systemInstruction = sys,
                                userPrompt = "Topic: $topicPrompt\nDeck Title: $deckTitle",
                                onDelta = {},
                                onDone = { final, err ->
                                    isRunning = false
                                    if (err != null) statusMsg = err
                                    else {
                                        val parsed = parseSlides(final)
                                        if (parsed.isNotEmpty()) slides = parsed
                                        statusMsg = "Generated ${parsed.size} slides"
                                    }
                                }
                            )
                        }
                    ) { Text(if (isRunning) "..." else "AI Generate") }
                }
            }

            itemsIndexed(slides) { idx, slide ->
                WafaGlassCard(accentColor = ElectricBlue, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WafaHudBadge("Slide ${idx + 1}", ElectricCyan)
                        IconButton(onClick = {
                            if (slides.size > 1) slides = slides.toMutableList().also { it.removeAt(idx) }
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove slide")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = slide.title,
                        onValueChange = { newTitle ->
                            slides = slides.toMutableList().also { it[idx] = slide.copy(title = newTitle) }
                        },
                        label = { Text("Slide Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = slide.bullets.joinToString("\n"),
                        onValueChange = { body ->
                            slides = slides.toMutableList().also {
                                it[idx] = slide.copy(bullets = body.lines().filter { l -> l.isNotBlank() })
                            }
                        },
                        label = { Text("Slide Bullet Points (one per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { slides = slides + PresentationSlide("New Slide", listOf("Key point 1", "Key point 2")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Slide")
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val info = viewModel.repository.docEngine.createPptx(slides, deckTitle, deckTitle)
                            viewModel.recordGeneratedArtifact("PRESENTATION", info.file.name, "PPTX", info.file, info.previewSummary)
                            statusMsg = "Created ${info.previewSummary}"
                            shareGeneratedFile(context, info.file, mimeForFormat("PPTX"))
                        },
                        modifier = Modifier.weight(1f).testTag("export_pptx_button")
                    ) { Text("Export PPTX") }

                    OutlinedButton(
                        onClick = {
                            val pdfBody = slides.mapIndexed { i, s ->
                                "# Slide ${i + 1}: ${s.title}\n" + s.bullets.joinToString("\n") { "• $it" }
                            }.joinToString("\n\n")
                            val info = viewModel.repository.docEngine.createPdf(deckTitle, pdfBody, "${deckTitle}_slides")
                            viewModel.recordGeneratedArtifact("PRESENTATION", info.file.name, "PDF", info.file, info.previewSummary)
                            statusMsg = "Created ${info.previewSummary}"
                            shareGeneratedFile(context, info.file, mimeForFormat("PDF"))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Export PDF") }
                }
            }

            if (statusMsg != null) {
                item { WafaHudBadge(statusMsg!!, EmeraldSignal) }
            }
        }
    }
}

private fun parseSlides(raw: String): List<PresentationSlide> {
    val result = mutableListOf<PresentationSlide>()
    var curTitle: String? = null
    val curBullets = mutableListOf<String>()
    raw.lines().forEach { line ->
        val t = line.trim()
        if (t.startsWith("SLIDE:", ignoreCase = true)) {
            if (curTitle != null) {
                result += PresentationSlide(curTitle!!, curBullets.toList())
                curBullets.clear()
            }
            curTitle = t.substringAfter(":").trim()
        } else if (t.startsWith("-") || t.startsWith("•") || t.startsWith("*")) {
            curBullets += t.trimStart('-', '•', '*', ' ')
        }
    }
    if (curTitle != null) {
        result += PresentationSlide(curTitle!!, curBullets.toList())
    }
    return result
}
