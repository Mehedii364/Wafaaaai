package com.example.core.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VioletPulse
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WafaGlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.42f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            VioletPulse.copy(alpha = 0.28f)
        )
    )
    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderBrush, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
fun WafaHudBadge(
    label: String,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WafaWaveformOrb(
    isActive: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "jarvis_orb")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 3200 else 9000, easing = LinearEasing)
        ),
        label = "orb_rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val primary = ElectricCyan
    val secondary = ElectricBlue
    val tertiary = VioletPulse

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = size.minDimension
        val baseRadius = minDim * 0.32f * (if (isActive) 1f + rmsLevel * 0.22f else pulse)

        // Outer ambient halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(cx, cy),
                radius = minDim * 0.48f
            ),
            radius = minDim * 0.48f,
            center = Offset(cx, cy)
        )

        // Rotating segmented HUD rings
        drawArc(
            color = primary.copy(alpha = 0.85f),
            startAngle = angle,
            sweepAngle = 220f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            topLeft = Offset(cx - baseRadius * 1.15f, cy - baseRadius * 1.15f),
            size = androidx.compose.ui.geometry.Size(baseRadius * 2.3f, baseRadius * 2.3f)
        )
        drawArc(
            color = tertiary.copy(alpha = 0.75f),
            startAngle = -angle * 1.4f,
            sweepAngle = 150f,
            useCenter = false,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            topLeft = Offset(cx - baseRadius * 0.9f, cy - baseRadius * 0.9f),
            size = androidx.compose.ui.geometry.Size(baseRadius * 1.8f, baseRadius * 1.8f)
        )

        // Radial audio waveform bars
        val barCount = 24
        for (i in 0 until barCount) {
            val rad = Math.toRadians((i * (360.0 / barCount)) + angle * 0.5)
            val wave = if (isActive) {
                (0.25f + rmsLevel * 0.75f) * (0.5f + 0.5f * sin(i * 1.3 + angle * 0.08).toFloat())
            } else {
                0.15f + 0.1f * sin(i * 0.9 + angle * 0.04).toFloat()
            }
            val rIn = baseRadius * 0.52f
            val rOut = rIn + baseRadius * 0.28f * wave
            drawLine(
                color = if (i % 2 == 0) primary else secondary,
                start = Offset(cx + (rIn * cos(rad)).toFloat(), cy + (rIn * sin(rad)).toFloat()),
                end = Offset(cx + (rOut * cos(rad)).toFloat(), cy + (rOut * sin(rad)).toFloat()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Core nucleus
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, primary, secondary.copy(alpha = 0.4f)),
                center = Offset(cx, cy),
                radius = baseRadius * 0.42f
            ),
            radius = baseRadius * 0.42f,
            center = Offset(cx, cy)
        )
    }
}

@Composable
fun WafaRichTextContent(
    text: String,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val segments = splitMarkdownBlocks(text)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { seg ->
            if (seg.isCode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF030712),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = seg.language.ifBlank { "code" }.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(seg.content)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = seg.content,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = seg.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

data class MarkdownSegment(
    val isCode: Boolean,
    val language: String,
    val content: String
)

fun splitMarkdownBlocks(raw: String): List<MarkdownSegment> {
    val out = mutableListOf<MarkdownSegment>()
    val regex = Regex("```([a-zA-Z0-9_+\\-]*)\\n([\\s\\S]*?)```")
    var lastIndex = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > lastIndex) {
            val plain = raw.substring(lastIndex, match.range.first).trim()
            if (plain.isNotEmpty()) out += MarkdownSegment(false, "", plain)
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        out += MarkdownSegment(true, lang, code)
        lastIndex = match.range.last + 1
    }
    if (lastIndex < raw.length) {
        val tail = raw.substring(lastIndex).trim()
        if (tail.isNotEmpty()) out += MarkdownSegment(false, "", tail)
    }
    if (out.isEmpty()) out += MarkdownSegment(false, "", raw)
    return out
}

@Composable
fun WafaEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .minimumInteractiveComponentSize()
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun WafaScreenHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("header_back_button")
            ) {
                Icon(
                    Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}

fun shareGeneratedFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
}

fun mimeForFormat(format: String): String = when (format.uppercase()) {
    "PDF" -> "application/pdf"
    "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "ZIP" -> "application/zip"
    "PNG" -> "image/png"
    "CSV" -> "text/csv"
    else -> "text/plain"
}
