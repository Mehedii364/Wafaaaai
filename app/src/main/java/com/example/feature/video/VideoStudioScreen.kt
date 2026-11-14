package com.example.feature.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.ai.TaskCategory
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import java.io.ByteArrayOutputStream

@Composable
fun VideoStudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var videoMeta by remember { mutableStateOf("") }
    var keyframes by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var transcriptOrSubtitles by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).orEmpty()
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).orEmpty()
                val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
                videoMeta = "Resolution: ${width}x${height} • Duration: ${durationMs / 1000}s • Type: $mime"

                val frames = mutableListOf<Bitmap>()
                val step = (durationMs * 1000L) / 4L
                for (i in 1..3) {
                    retriever.getFrameAtTime(step * i, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frames += it }
                }
                retriever.release()
                keyframes = frames
                statusMsg = "Extracted ${frames.size} keyframes safely on-device"
            } catch (t: Throwable) {
                statusMsg = "Video inspection failed: ${t.message}"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Video Intelligence",
            subtitle = "On-Device Keyframe & Metadata • Scene & Transcript AI",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Select Video (Android Photo Picker)") }
            }

            if (videoMeta.isNotBlank()) {
                item {
                    WafaHudBadge(videoMeta, ElectricCyan)
                }
            }

            if (keyframes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keyframes.forEachIndexed { i, bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Video Keyframe ${i + 1}",
                                modifier = Modifier.size(width = 160.dp, height = 100.dp)
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = transcriptOrSubtitles,
                    onValueChange = { transcriptOrSubtitles = it },
                    label = { Text("Paste SRT/VTT subtitles or scene questions") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    maxLines = 8
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = !isBusy && (keyframes.isNotEmpty() || transcriptOrSubtitles.isNotBlank()),
                        onClick = {
                            isBusy = true
                            analysisResult = ""
                            val frameB64 = keyframes.firstOrNull()?.let { bmp ->
                                val baos = ByteArrayOutputStream()
                                bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                            }
                            viewModel.runSingleShotAiTask(
                                taskCategory = if (frameB64 != null) TaskCategory.VISION else TaskCategory.VIDEO_ANALYSIS,
                                systemInstruction = "Analyze the video metadata, keyframe visual scene, and provided transcript/subtitles. Do not claim to have watched unprovided frames.",
                                userPrompt = "Metadata: $videoMeta\n\nTranscript/Question:\n${transcriptOrSubtitles.ifBlank { "Describe the scene shown in the extracted keyframe." }}",
                                imageBase64 = frameB64,
                                imageMimeType = if (frameB64 != null) "image/jpeg" else null,
                                onDelta = { analysisResult = it },
                                onDone = { final, err -> isBusy = false; if (err != null) statusMsg = err else analysisResult = final }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isBusy) "Analyzing..." else "Analyze Keyframe & Transcript") }
                }
            }

            if (statusMsg != null) {
                item { WafaHudBadge(statusMsg!!, ElectricBlue) }
            }

            if (analysisResult.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        WafaRichTextContent(text = analysisResult)
                    }
                }
            }
        }
    }
}
