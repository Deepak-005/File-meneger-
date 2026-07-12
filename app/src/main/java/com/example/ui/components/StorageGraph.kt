package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FileRepository

enum class ChartType {
    Donut, Pie
}

@Composable
fun StorageGraph(
    stats: FileRepository.StorageStats,
    selectedIndex: Int,
    onSliceSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var chartType by remember { mutableStateOf(ChartType.Donut) }
    val total = stats.totalSpace.toDouble()
    val used = stats.usedSpace.toDouble()

    // Animatable sweep angle progress
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(stats, chartType) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    // Colors
    val colorFree = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val colorImages = Color(0xFF4CAF50)      // Green
    val colorVideos = Color(0xFF2196F3)      // Blue
    val colorAudio = Color(0xFFFF9800)       // Orange/Amber
    val colorDocs = Color(0xFF00BCD4)        // Cyan
    val colorApps = Color(0xFFE91E63)        // Pink
    val colorZips = Color(0xFF9C27B0)        // Purple
    val colorOther = Color(0xFF9E9E9E)       // Gray

    val usedPercent = if (total > 0) (used / total * 100).toInt() else 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Chart Switcher Segmented Control
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val options = listOf(ChartType.Donut, ChartType.Pie)
            options.forEach { type ->
                val selected = chartType == type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { chartType = type }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (type == ChartType.Donut) "Donut Chart" else "Pie Chart",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = modifier
                .aspectRatio(1f)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val dividerColor = MaterialTheme.colorScheme.surface

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 24.dp.toPx()
                val sizeMin = size.minDimension
                val radius = (sizeMin - strokeWidth) / 2f
                val centerPoint = center

                // 1. Draw base free space circle (only for Donut)
                if (chartType == ChartType.Donut) {
                    drawCircle(
                        color = colorFree,
                        radius = radius,
                        center = centerPoint,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    // Draw base circle for Pie representing Total Space
                    drawCircle(
                        color = colorFree,
                        radius = radius + (strokeWidth / 2f),
                        center = centerPoint,
                        style = Fill
                    )
                }

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

                categories.forEachIndexed { index, (sizeBytes, color) ->
                    if (sizeBytes > 0) {
                        val angle = (sizeBytes.toDouble() / total * 360f).toFloat() * animProgress.value
                        if (angle > 0f) {
                            val isHighlighted = index == selectedIndex
                            val scale = if (isHighlighted) 1.08f else 1.0f
                            val currentRadius = radius * scale
                            val currentStrokeWidth = strokeWidth * scale

                            if (chartType == ChartType.Pie) {
                                val topLeftOffset = Offset(
                                    centerPoint.x - (currentRadius + strokeWidth / 2f),
                                    centerPoint.y - (currentRadius + strokeWidth / 2f)
                                )
                                val arcSize = Size(
                                    (currentRadius + strokeWidth / 2f) * 2f,
                                    (currentRadius + strokeWidth / 2f) * 2f
                                )

                                // Solid slice
                                drawArc(
                                    color = color,
                                    startAngle = currentAngle,
                                    sweepAngle = angle,
                                    useCenter = true,
                                    topLeft = topLeftOffset,
                                    size = arcSize,
                                    style = Fill
                                )

                                // White/surface separator lines (Recharts-style spacing)
                                drawArc(
                                    color = dividerColor,
                                    startAngle = currentAngle,
                                    sweepAngle = angle,
                                    useCenter = true,
                                    topLeft = topLeftOffset,
                                    size = arcSize,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            } else {
                                val topLeftOffset = Offset(
                                    centerPoint.x - currentRadius,
                                    centerPoint.y - currentRadius
                                )
                                val arcSize = Size(currentRadius * 2f, currentRadius * 2f)

                                // Donut slice
                                drawArc(
                                    color = color,
                                    startAngle = currentAngle,
                                    sweepAngle = angle,
                                    useCenter = false,
                                    topLeft = topLeftOffset,
                                    size = arcSize,
                                    style = Stroke(
                                        width = currentStrokeWidth,
                                        cap = if (isHighlighted) StrokeCap.Square else StrokeCap.Round
                                    )
                                )
                            }
                            currentAngle += angle
                        }
                    }
                }
            }

            // Center typography (Only shown in Donut chart or when details are simple)
            if (chartType == ChartType.Donut) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$usedPercent%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
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
            } else {
                // Pie Chart Center Indicator (Subtle overlay for selection)
                if (selectedIndex != -1) {
                    val colorsList = listOf(colorImages, colorVideos, colorAudio, colorDocs, colorApps, colorZips, colorOther)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colorsList[selectedIndex])
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
