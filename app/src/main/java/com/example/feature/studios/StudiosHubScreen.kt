package com.example.feature.studios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.AppStrings
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.feature.navigation.WafaRoutes
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.VioletPulse
import java.io.File

private data class StudioEntry(
    val title: String,
    val description: String,
    val route: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun StudiosHubScreen(
    viewModel: MainViewModel,
    strings: AppStrings,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val artifacts by viewModel.artifacts.collectAsStateWithLifecycle()

    val entries = listOf(
        StudioEntry(strings.codingStudio, "Generate, debug, refactor, unit tests & ZIP project export", WafaRoutes.CODING, Icons.Default.Terminal, VioletPulse),
        StudioEntry(strings.writingStudio, "Articles, emails, applications, CV, EN/BN translation & tone", WafaRoutes.WRITING, Icons.Default.EditNote, EmeraldSignal),
        StudioEntry(strings.documentStudio, "Real DOCX, PDF, Markdown, TXT, RTF creation & extraction", WafaRoutes.DOCUMENTS, Icons.Default.Description, ElectricCyan),
        StudioEntry(strings.spreadsheetStudio, "Multi-sheet XLSX workbooks, formulas, CSV & data analysis", WafaRoutes.SPREADSHEET, Icons.Default.TableChart, EmeraldSignal),
        StudioEntry(strings.presentationStudio, "Real PPTX slide decks, outlines, layouts & PDF export", WafaRoutes.PRESENTATIONS, Icons.Default.Slideshow, ElectricBlue),
        StudioEntry(strings.imageStudio, "Text-to-image, vision QA, OCR & on-device image conversion", WafaRoutes.IMAGE, Icons.Default.Image, VioletPulse),
        StudioEntry(strings.audioStudio, "Mic STT/TTS, audio recording, Whisper transcription & summary", WafaRoutes.AUDIO, Icons.Default.Mic, ElectricCyan),
        StudioEntry(strings.videoStudio, "Keyframe extraction, video metadata & scene/transcript analysis", WafaRoutes.VIDEO, Icons.Default.VideoLibrary, ElectricBlue),
        StudioEntry(strings.researchEngine, "Wikipedia, arXiv, Crossref & web markdown with real citations", WafaRoutes.RESEARCH, Icons.Default.TravelExplore, EmeraldSignal),
        StudioEntry(strings.fileWorkspace, "Unified Storage Access Framework upload, inspect & organize", WafaRoutes.FILES, Icons.Default.FolderOpen, VioletPulse),
        StudioEntry(strings.personalTools, "QR gen/scan, Weather, FX Currency, JSON, Regex, Units & Calc", WafaRoutes.TOOLS, Icons.Default.BuildCircle, ElectricCyan)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("studios_hub_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WafaScreenHeader(
                title = strings.studiosTitle,
                subtitle = "Native Android Creation, Analysis & Utility Workspaces"
            )
        }

        items(entries) { entry ->
            WafaGlassCard(
                accentColor = entry.accent,
                onClick = { onNavigate(entry.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("studio_entry_${entry.route}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = entry.title,
                        tint = entry.accent,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        if (artifacts.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Generated Files (${artifacts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(artifacts.take(12), key = { it.id }) { art ->
                WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${art.title} [${art.format}]", style = MaterialTheme.typography.titleSmall)
                            Text("${art.studioType} • ${art.byteSize / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            val f = File(art.filePath)
                            if (f.exists()) shareGeneratedFile(context, f, mimeForFormat(art.format))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = strings.shareFile)
                        }
                        IconButton(onClick = { viewModel.deleteArtifact(art) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}
