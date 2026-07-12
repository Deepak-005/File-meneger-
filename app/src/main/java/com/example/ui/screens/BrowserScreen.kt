package com.example.ui.screens

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FileItem
import com.example.ui.components.FileItemGrid
import com.example.ui.components.FileItemRow
import com.example.ui.viewmodel.FileViewModel
import com.example.ui.viewmodel.SortBy
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: FileViewModel,
    onFileClick: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val clipboardPaths by viewModel.clipboardPaths.collectAsState()
    val pasteMode by viewModel.pasteMode.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val showHidden by viewModel.showHiddenFiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileItem?>(null) }
    var showZipDialog by remember { mutableStateOf(false) }

    var folderNameInput by remember { mutableStateOf("") }
    var renameInput by remember { mutableStateOf("") }
    var zipNameInput by remember { mutableStateOf("") }

    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("browser_screen"),
        topBar = {
            if (selectedItems.isNotEmpty()) {
                // Contextual selection appbar
                TopAppBar(
                    title = { Text(text = "${selectedItems.size} selected") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.copySelected() }) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = { viewModel.cutSelected() }) {
                            Icon(imageVector = Icons.Default.ContentCut, contentDescription = "Cut")
                        }
                        IconButton(onClick = { viewModel.deleteSelectedFiles() }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                        }
                        IconButton(onClick = {
                            zipNameInput = "archive"
                            showZipDialog = true
                        }) {
                            Icon(imageVector = Icons.Default.FolderZip, contentDescription = "Compress")
                        }
                        if (selectedItems.size == 1) {
                            IconButton(onClick = {
                                val item = selectedItems.first()
                                itemToRename = item
                                renameInput = item.name
                                showRenameDialog = true
                            }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Rename")
                            }
                        }
                    }
                )
            } else {
                // Standard appbar with Search and controls
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search files & folders...", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Cancel, contentDescription = "Clear search")
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("search_bar")
                        )
                    }

                    // Breadcrumbs Row
                    BreadcrumbsRow(
                        path = currentPath,
                        onBreadcrumbClick = { viewModel.navigateTo(it) }
                    )

                    // Controls Row (Grid/List toggle, Sorting, Hidden files toggle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${files.size} items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row {
                            IconButton(onClick = { viewModel.setGridView(!isGridView) }) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                                    contentDescription = "View Toggle"
                                )
                            }
                            IconButton(onClick = { viewModel.toggleHiddenFiles() }) {
                                Icon(
                                    imageVector = if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Hidden"
                                )
                            }
                            Box {
                                IconButton(onClick = { sortMenuExpanded = true }) {
                                    Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort Menu")
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Sort by Name") },
                                        onClick = {
                                            viewModel.setSortBy(SortBy.NAME)
                                            sortMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sort by Size") },
                                        onClick = {
                                            viewModel.setSortBy(SortBy.SIZE)
                                            sortMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sort by Date") },
                                        onClick = {
                                            viewModel.setSortBy(SortBy.DATE)
                                            sortMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sort by Type") },
                                        onClick = {
                                            viewModel.setSortBy(SortBy.TYPE)
                                            sortMenuExpanded = false
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Toggle Order (Asc/Desc)") },
                                        onClick = {
                                            viewModel.toggleSortOrder()
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedItems.isEmpty()) {
                FloatingActionButton(
                    onClick = {
                        folderNameInput = ""
                        showCreateFolderDialog = true
                    },
                    modifier = Modifier.testTag("fab_create_folder"),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Folder")
                }
            }
        },
        bottomBar = {
            // Paste Action bar (visible when clipboard is full)
            AnimatedVisibility(
                visible = clipboardPaths.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${clipboardPaths.size} items on clipboard",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Paste mode: ${pasteMode?.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }

                        Row {
                            Button(
                                onClick = { viewModel.cancelPaste() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = { viewModel.paste() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste Here")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (files.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Empty folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This folder is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (isGridView) {
                // Grid representation of files
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(files) { item ->
                        FileItemGrid(
                            item = item,
                            isSelected = selectedItems.contains(item),
                            onItemClick = {
                                if (selectedItems.isNotEmpty()) {
                                    viewModel.toggleSelection(item)
                                } else if (item.isDirectory) {
                                    viewModel.navigateTo(item.path)
                                } else {
                                    onFileClick(item)
                                }
                            },
                            onItemLongClick = {
                                viewModel.toggleSelection(item)
                            }
                        )
                    }
                }
            } else {
                // List representation of files
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(files) { item ->
                        FileItemRow(
                            item = item,
                            isSelected = selectedItems.contains(item),
                            onItemClick = {
                                if (selectedItems.isNotEmpty()) {
                                    viewModel.toggleSelection(item)
                                } else if (item.isDirectory) {
                                    viewModel.navigateTo(item.path)
                                } else {
                                    onFileClick(item)
                                }
                            },
                            onItemLongClick = {
                                viewModel.toggleSelection(item)
                            },
                            onFavoriteToggle = {
                                viewModel.toggleFavorite(item)
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotEmpty()) {
                            viewModel.createFolder(folderNameInput)
                        }
                        showCreateFolderDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                Button(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDialog && itemToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File/Folder") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotEmpty()) {
                            viewModel.renameFile(itemToRename!!.path, renameInput)
                        }
                        showRenameDialog = false
                        viewModel.clearSelection()
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                Button(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showZipDialog) {
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("Compress to ZIP") },
            text = {
                OutlinedTextField(
                    value = zipNameInput,
                    onValueChange = { zipNameInput = it },
                    label = { Text("ZIP File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (zipNameInput.isNotEmpty()) {
                            viewModel.compressSelected(zipNameInput)
                        }
                        showZipDialog = false
                    }
                ) {
                    Text("Compress")
                }
            },
            dismissButton = {
                Button(onClick = { showZipDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BreadcrumbsRow(
    path: String,
    onBreadcrumbClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val parts = path.split("/").filter { it.isNotEmpty() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onBreadcrumbClick(Environment.getExternalStorageDirectory().absolutePath) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Storage Root",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        var accumulatedPath = ""
        parts.forEach { part ->
            accumulatedPath += "/$part"
            val currentTarget = accumulatedPath

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "arrow",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = part,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = if (currentTarget == path) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (currentTarget == path) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { onBreadcrumbClick(currentTarget) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
