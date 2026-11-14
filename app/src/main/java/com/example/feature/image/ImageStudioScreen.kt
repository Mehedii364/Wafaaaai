package com.example.feature.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.ui.theme.VioletPulse
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
fun ImageStudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imagePrompt by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageB64 by remember { mutableStateOf<String?>(null) }
    var visionResult by remember { mutableStateOf("") }
    var lastSavedFile by remember { mutableStateOf<File?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                selectedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                selectedImageB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                statusMsg = "Loaded image (${bytes.size / 1024} KB)"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Image Intelligence",
            subtitle = "Text-to-Image • Vision QA • OCR • Compress & Convert",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = imagePrompt,
                    onValueChange = { imagePrompt = it },
                    label = { Text("Image prompt (or question for Vision/OCR)") },
                    modifier = Modifier.fillMaxWidth().testTag("image_prompt_input"),
                    maxLines = 4
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !isBusy && imagePrompt.isNotBlank(),
                        onClick = {
                            scope.launch {
                                isBusy = true
                                statusMsg = null
                                val decision = viewModel.repository.resolveRoute(TaskCategory.IMAGE_GEN)
                                if (decision == null) {
                                    isBusy = false
                                    statusMsg = "No image-capable provider configured."
                                    return@launch
                                }
                                val key = viewModel.repository.vault.getApiKey(decision.primaryProvider.id)
                                val res = viewModel.repository.gateway.generateImage(
                                    decision.primaryProvider,
                                    decision.primaryModel.modelId,
                                    key,
                                    imagePrompt
                                )
                                isBusy = false
                                if (res.success && res.imageBytes != null) {
                                    selectedBitmap = BitmapFactory.decodeByteArray(res.imageBytes, 0, res.imageBytes.size)
                                    selectedImageB64 = Base64.encodeToString(res.imageBytes, Base64.NO_WRAP)
                                    val saved = viewModel.repository.docEngine.saveImageBytes(res.imageBytes, "wafa_img_${System.currentTimeMillis()}")
                                    lastSavedFile = saved.file
                                    viewModel.recordGeneratedArtifact("IMAGE", saved.file.name, "PNG", saved.file, saved.previewSummary)
                                    statusMsg = "Generated via ${decision.primaryProvider.name}"
                                } else {
                                    statusMsg = res.error ?: "Image generation failed"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isBusy) "Working..." else "Generate Image") }

                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Pick Image") }
                }
            }

            if (selectedBitmap != null) {
                item {
                    WafaGlassCard(accentColor = VioletPulse, modifier = Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected or generated image",
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                enabled = !isBusy && selectedImageB64 != null,
                                onClick = {
                                    isBusy = true
                                    visionResult = ""
                                    viewModel.runSingleShotAiTask(
                                        taskCategory = TaskCategory.VISION,
                                        systemInstruction = "Analyze this image thoroughly: describe objects, extract all visible text (OCR), and answer the user's question.",
                                        userPrompt = imagePrompt.ifBlank { "Describe this image and extract all visible text (OCR)." },
                                        imageBase64 = selectedImageB64,
                                        imageMimeType = "image/jpeg",
                                        onDelta = { visionResult = it },
                                        onDone = { final, err -> isBusy = false; if (err != null) statusMsg = err else visionResult = final }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Vision / OCR") }

                            OutlinedButton(
                                onClick = {
                                    val bmp = selectedBitmap!!
                                    val baos = ByteArrayOutputStream()
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 72, baos)
                                    val saved = viewModel.repository.docEngine.saveImageBytes(baos.toByteArray(), "wafa_compressed_${System.currentTimeMillis()}")
                                    lastSavedFile = saved.file
                                    statusMsg = "Compressed JPEG saved (${saved.byteSize / 1024} KB)"
                                    shareGeneratedFile(context, saved.file, "image/jpeg")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Compress & Share") }
                        }
                        if (lastSavedFile != null) {
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { shareGeneratedFile(context, lastSavedFile!!, "image/png") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share Saved Image")
                            }
                        }
                    }
                }
            }

            if (statusMsg != null) {
                item { WafaHudBadge(statusMsg!!, ElectricCyan) }
            }

            if (visionResult.isNotBlank()) {
                item {
                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        Text("Vision & OCR Analysis", style = MaterialTheme.typography.titleSmall, color = EmeraldSignal)
                        Spacer(Modifier.height(6.dp))
                        WafaRichTextContent(text = visionResult)
                    }
                }
            }
        }
    }
}
