package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FileRepository
import com.example.domain.FileItem
import com.example.ui.components.StorageGraph
import com.example.ui.viewmodel.FileViewModel
import com.example.ui.viewmodel.StorageUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: FileViewModel,
    onCategoryClick: (String) -> Unit, // Navigate to browser filter
    onFileClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val storageState by viewModel.storageState.collectAsState()
    val recents by viewModel.recents.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen")
    ) {
        // 1. Header with Title & Refresh button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Storage",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-time analysis & quick actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { viewModel.refreshStorageStats() },
                    modifier = Modifier.testTag("refresh_stats_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Stats"
                    )
                }
            }
        }

        // 2. Storage Visualizer Graph
        item {
            when (val state = storageState) {
                is StorageUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is StorageUiState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = "Error scanning storage: ${state.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is StorageUiState.Success -> {
                    val stats = state.stats
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StorageGraph(
                            stats = stats,
                            modifier = Modifier.size(240.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Category breakdown flow row
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            maxItemsInEachRow = 3,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CategoryLegendBadge(
                                label = "Photos",
                                sizeBytes = stats.imagesSize,
                                color = Color(0xFF4CAF50),
                                icon = Icons.Default.Photo,
                                onClick = { onCategoryClick("image/*") }
                            )
                            CategoryLegendBadge(
                                label = "Videos",
                                sizeBytes = stats.videosSize,
                                color = Color(0xFF2196F3),
                                icon = Icons.Default.Movie,
                                onClick = { onCategoryClick("video/*") }
                            )
                            CategoryLegendBadge(
                                label = "Audios",
                                sizeBytes = stats.audioSize,
                                color = Color(0xFFFF9800),
                                icon = Icons.Default.AudioFile,
                                onClick = { onCategoryClick("audio/*") }
                            )
                            CategoryLegendBadge(
                                label = "Documents",
                                sizeBytes = stats.documentsSize,
                                color = Color(0xFF00BCD4),
                                icon = Icons.Default.Description,
                                onClick = { onCategoryClick("text/plain") }
                            )
                            CategoryLegendBadge(
                                label = "Apps",
                                sizeBytes = stats.appsSize,
                                color = Color(0xFFE91E63),
                                icon = Icons.Default.Android,
                                onClick = { onCategoryClick("application/vnd.android.package-archive") }
                            )
                            CategoryLegendBadge(
                                label = "Archives",
                                sizeBytes = stats.zipSize,
                                color = Color(0xFF9C27B0),
                                icon = Icons.Default.FolderZip,
                                onClick = { onCategoryClick("application/zip") }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // 3. Recent Files Section
        if (recents.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            items(recents) { item ->
                RecentFileRow(
                    file = item,
                    onClick = {
                        viewModel.openFile(item)
                        onFileClick(item)
                    }
                )
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Empty Stats",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No recent file operations",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun CategoryLegendBadge(
    label: String,
    sizeBytes: Long,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(text = formatSize(sizeBytes), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RecentFileRow(
    file: FileItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        file.mimeType.startsWith("image/") -> Icons.Default.Photo
                        file.mimeType.startsWith("video/") -> Icons.Default.Movie
                        file.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
                        file.extension == "apk" -> Icons.Default.Android
                        file.extension in listOf("zip", "rar", "tar", "gz", "7z") -> Icons.Default.FolderZip
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = when {
                        file.mimeType.startsWith("image/") -> Color(0xFF00BCD4)
                        file.mimeType.startsWith("video/") -> Color(0xFF2196F3)
                        file.mimeType.startsWith("audio/") -> Color(0xFFFF5722)
                        file.extension == "apk" -> Color(0xFF4CAF50)
                        file.extension in listOf("zip", "rar", "tar", "gz", "7z") -> Color(0xFF9C27B0)
                        else -> Color(0xFF03A9F4)
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Opened: ${file.formattedDate} • ${file.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
