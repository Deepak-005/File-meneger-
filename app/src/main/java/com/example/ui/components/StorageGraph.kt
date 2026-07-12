package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FileRepository

@Composable
fun StorageGraph(
    stats: FileRepository.StorageStats,
    modifier: Modifier = Modifier
) {
    val total = stats.totalSpace.toDouble()
    val used = stats.usedSpace.toDouble()
    val free = stats.freeSpace.toDouble()

    // Animatable sweep angle
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(stats) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    // Color theme
    val colorUsed = MaterialTheme.colorScheme.primary
    val colorFree = MaterialTheme.colorScheme.surfaceVariant
    val colorImages = Color(0xFF4CAF50)      // Green
    val colorVideos = Color(0xFF2196F3)      // Blue
    val colorAudio = Color(0xFFFF9800)       // Orange/Amber
    val colorDocs = Color(0xFF00BCD4)        // Cyan
    val colorApps = Color(0xFFE91E63)        // Pink
    val colorZips = Color(0xFF9C27B0)        // Purple
    val colorOther = Color(0xFF9E9E9E)       // Gray

    // Percentage
    val usedPercent = (used / total * 100).toInt()

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 24.dp.toPx()
            val sizeMin = size.minDimension
            val radius = (sizeMin - strokeWidth) / 2f
            val centerPoint = center

            // 1. Draw base free space circle
            drawCircle(
                color = colorFree,
                radius = radius,
                center = centerPoint,
                style = Stroke(width = strokeWidth)
            )

            // 2. Draw category slices (proportional)
            var currentAngle = -90f

            val categories = listOf(
                Pair(stats.imagesSize, colorImages),
                Pair(stats.videosSize, colorVideos),
                Pair(stats.audioSize, colorAudio),
                Pair(stats.documentsSize, colorDocs),
                Pair(stats.appsSize, colorApps),
                Pair(stats.zipSize, colorZips),
                Pair(stats.otherSize, colorOther)
            )

            categories.forEach { (sizeBytes, color) ->
                if (sizeBytes > 0) {
                    val angle = (sizeBytes.toDouble() / total * 360f).toFloat() * animProgress.value
                    drawArc(
                        color = color,
                        startAngle = currentAngle,
                        sweepAngle = angle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    currentAngle += angle
                }
            }
        }

        // Center typography
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$usedPercent%",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Storage Used",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatBytes(stats.usedSpace) + " / " + formatBytes(stats.totalSpace),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
