package com.example.ui.viewmodel

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FileRepository
import com.example.data.RecycleEntity
import com.example.domain.FileItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortBy { NAME, SIZE, DATE, TYPE }
enum class SortOrder { ASCENDING, DESCENDING }
enum class PasteMode { COPY, CUT }

sealed class StorageUiState {
    object Loading : StorageUiState()
    data class Success(val stats: FileRepository.StorageStats) : StorageUiState()
    data class Error(val message: String) : StorageUiState()
}

class FileViewModel(private val repository: FileRepository) : ViewModel() {

    // --- Current Directory State ---
    private val defaultRootPath = Environment.getExternalStorageDirectory().absolutePath
    private val _currentPath = MutableStateFlow(defaultRootPath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    // --- Settings UI States ---
    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // --- Sort State ---
    private val _sortBy = MutableStateFlow(SortBy.NAME)
    val sortBy: StateFlow<SortBy> = _sortBy.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ASCENDING)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // --- Raw File State ---
    private val _rawFiles = MutableStateFlow<List<FileItem>>(emptyList())

    // --- UI Toast/Event Flow ---
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    // --- App Lock Settings State ---
    private val _appLockPin = MutableStateFlow<String?>(null)
    val appLockPin: StateFlow<String?> = _appLockPin.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(true)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    // --- Multi Selection State ---
    private val _selectedItems = MutableStateFlow<Set<FileItem>>(emptySet())
    val selectedItems: StateFlow<Set<FileItem>> = _selectedItems.asStateFlow()

    // --- Clipboard State ---
    private val _clipboardPaths = MutableStateFlow<List<String>>(emptyList())
    val clipboardPaths: StateFlow<List<String>> = _clipboardPaths.asStateFlow()

    private val _pasteMode = MutableStateFlow<PasteMode?>(null)
    val pasteMode: StateFlow<PasteMode?> = _pasteMode.asStateFlow()

    // --- Favorites state mapped from Room ---
    val favorites: StateFlow<List<FileItem>> = repository.allFavorites.map { entities ->
        entities.map { entity ->
            val file = File(entity.filePath)
            FileItem(
                name = entity.fileName,
                path = entity.filePath,
                size = if (file.exists()) file.length() else 0L,
                lastModified = if (file.exists()) file.lastModified() else entity.addedTimestamp,
                isDirectory = entity.isDirectory,
                isFavorite = true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Recent files state mapped from Room ---
    val recents: StateFlow<List<FileItem>> = repository.recentFiles.map { entities ->
        entities.map { entity ->
            val file = File(entity.filePath)
            FileItem(
                name = entity.fileName,
                path = entity.filePath,
                size = entity.size,
                lastModified = if (file.exists()) file.lastModified() else entity.openedTimestamp,
                isDirectory = entity.isDirectory
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Recycle Bin items state mapped from Room ---
    val recycleBin: StateFlow<List<RecycleEntity>> = repository.allRecycled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Storage Stats state ---
    private val _storageState = MutableStateFlow<StorageUiState>(StorageUiState.Loading)
    val storageState: StateFlow<StorageUiState> = _storageState.asStateFlow()

    // --- Dynamic Files Feed (Filtered, Sorted, Searched) ---
    val files: StateFlow<List<FileItem>> = combine(
        _rawFiles,
        _searchQuery,
        _sortBy,
        _sortOrder
    ) { raw, query, sortBy, sortOrder ->
        var processed = raw
        if (query.isNotEmpty()) {
            processed = processed.filter { it.name.contains(query, ignoreCase = true) }
        }
        processed = when (sortBy) {
            SortBy.NAME -> processed.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortBy.SIZE -> processed.sortedBy { it.size }
            SortBy.DATE -> processed.sortedBy { it.lastModified }
            SortBy.TYPE -> processed.sortedWith(compareBy({ !it.isDirectory }, { it.extension }))
        }
        if (sortOrder == SortOrder.DESCENDING) {
            processed = processed.reversed()
        }
        processed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Load app lock state
            val pin = repository.getSetting("app_lock_pin")
            _appLockPin.value = pin
            _isAppLocked.value = !pin.isNullOrEmpty()
            _isAppUnlocked.value = pin.isNullOrEmpty()

            // Prepopulate elegant dummy files on external files folder
            repository.prepopulateSampleFilesIfNeeded()

            // Go to pre-populated sandbox folder by default to ensure beautiful launch state!
            val sandbox = File(Environment.getExternalStorageDirectory(), "FileManagerDocs")
            if (sandbox.exists()) {
                _currentPath.value = sandbox.absolutePath
            }

            refreshFiles()
            refreshStorageStats()
        }
    }

    // --- UI Commands & Handlers ---

    fun refreshFiles() {
        viewModelScope.launch {
            val fileList = repository.listFiles(_currentPath.value, _showHiddenFiles.value)
            _rawFiles.value = fileList
        }
    }

    fun navigateTo(path: String) {
        viewModelScope.launch {
            val file = File(path)
            if (file.isDirectory) {
                _currentPath.value = path
                _searchQuery.value = ""
                _selectedItems.value = emptySet()
                refreshFiles()
            }
        }
    }

    fun navigateUp(): Boolean {
        val current = File(_currentPath.value)
        val parent = current.parentFile
        if (parent != null && _currentPath.value != defaultRootPath) {
            navigateTo(parent.absolutePath)
            return true
        }
        return false
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageState.value = StorageUiState.Loading
            try {
                val stats = repository.getStorageStats()
                _storageState.value = StorageUiState.Success(stats)
            } catch (e: Exception) {
                _storageState.value = StorageUiState.Error(e.message ?: "Failed to read storage analytics")
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val success = repository.createFolder(_currentPath.value, name)
            if (success) {
                _events.emit("Folder '$name' created successfully")
                refreshFiles()
                refreshStorageStats()
            } else {
                _events.emit("Failed to create folder '$name'")
            }
        }
    }

    fun renameFile(path: String, newName: String) {
        viewModelScope.launch {
            val success = repository.renameFile(path, newName)
            if (success) {
                _events.emit("Renamed successfully")
                refreshFiles()
            } else {
                _events.emit("Failed to rename")
            }
        }
    }

    fun deleteFile(file: FileItem) {
        viewModelScope.launch {
            val success = repository.moveToRecycleBin(file.path)
            if (success) {
                _events.emit("'${file.name}' moved to Recycle Bin")
                refreshFiles()
                refreshStorageStats()
            } else {
                _events.emit("Failed to delete")
            }
        }
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            var successCount = 0
            val items = _selectedItems.value
            items.forEach {
                if (repository.moveToRecycleBin(it.path)) {
                    successCount++
                }
            }
            _events.emit("Deleted $successCount files")
            _selectedItems.value = emptySet()
            refreshFiles()
            refreshStorageStats()
        }
    }

    // --- Clipboard (Copy, Cut, Paste) ---

    fun copySelected() {
        _clipboardPaths.value = _selectedItems.value.map { it.path }
        _pasteMode.value = PasteMode.COPY
        _selectedItems.value = emptySet()
        viewModelScope.launch { _events.emit("Copied ${_clipboardPaths.value.size} items to clipboard") }
    }

    fun cutSelected() {
        _clipboardPaths.value = _selectedItems.value.map { it.path }
        _pasteMode.value = PasteMode.CUT
        _selectedItems.value = emptySet()
        viewModelScope.launch { _events.emit("Cut ${_clipboardPaths.value.size} items to clipboard") }
    }

    fun paste() {
        val paths = _clipboardPaths.value
        val mode = _pasteMode.value ?: return
        val dest = _currentPath.value

        viewModelScope.launch {
            var successCount = 0
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val ok = if (mode == PasteMode.COPY) {
                        repository.copyFile(path, dest)
                    } else {
                        repository.moveFile(path, dest)
                    }
                    if (ok) successCount++
                }
            }
            _events.emit("Pasted $successCount items successfully")
            _clipboardPaths.value = emptyList()
            _pasteMode.value = null
            refreshFiles()
            refreshStorageStats()
        }
    }

    fun cancelPaste() {
        _clipboardPaths.value = emptyList()
        _pasteMode.value = null
    }

    // --- Selection helpers ---

    fun toggleSelection(item: FileItem) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(item)) {
            current.remove(item)
        } else {
            current.add(item)
        }
        _selectedItems.value = current
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
    }

    fun selectAll() {
        _selectedItems.value = _rawFiles.value.toSet()
    }

    // --- ZIP Archiving ---

    fun compressSelected(zipName: String) {
        val paths = _selectedItems.value.map { it.path }
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val ok = repository.compressToZip(paths, zipName, _currentPath.value)
            if (ok) {
                _events.emit("Created zip file: $zipName")
                _selectedItems.value = emptySet()
                refreshFiles()
                refreshStorageStats()
            } else {
                _events.emit("ZIP compression failed")
            }
        }
    }

    fun extractZipFile(file: FileItem) {
        viewModelScope.launch {
            val targetFolder = file.name.substringBeforeLast(".zip")
            val destDir = File(File(file.path).parent, targetFolder).absolutePath
            val ok = repository.extractZip(file.path, destDir)
            if (ok) {
                _events.emit("Extracted successfully to $targetFolder")
                refreshFiles()
                refreshStorageStats()
            } else {
                _events.emit("ZIP extraction failed")
            }
        }
    }

    // --- Search, View, Sort toggles ---

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setGridView(isGrid: Boolean) {
        _isGridView.value = isGrid
    }

    fun toggleHiddenFiles() {
        _showHiddenFiles.value = !_showHiddenFiles.value
        refreshFiles()
    }

    fun setSortBy(sort: SortBy) {
        _sortBy.value = sort
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.ASCENDING) {
            SortOrder.DESCENDING
        } else {
            SortOrder.ASCENDING
        }
    }

    // --- Favorites ---

    fun toggleFavorite(file: FileItem) {
        viewModelScope.launch {
            if (file.isFavorite) {
                repository.removeFavorite(file.path)
                _events.emit("Removed from Favorites")
            } else {
                repository.addFavorite(file.path, file.name, file.isDirectory)
                _events.emit("Added to Favorites")
            }
            refreshFiles()
        }
    }

    // --- Recycle Bin operations ---

    fun restoreRecycleBinItem(item: RecycleEntity) {
        viewModelScope.launch {
            val ok = repository.restoreFromRecycleBin(item)
            if (ok) {
                _events.emit("Restored '${item.fileName}' successfully")
                refreshFiles()
                refreshStorageStats()
            } else {
                _events.emit("Failed to restore item")
            }
        }
    }

    fun deleteRecycleBinItemPermanently(item: RecycleEntity) {
        viewModelScope.launch {
            val ok = repository.deletePermanently(item.recyclePath, isRecycled = true)
            if (ok) {
                _events.emit("Deleted permanently")
                refreshStorageStats()
            } else {
                _events.emit("Failed to delete permanently")
            }
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            val ok = repository.clearRecycleBin()
            if (ok) {
                _events.emit("Recycle Bin cleared")
                refreshStorageStats()
            } else {
                _events.emit("Failed to clear Recycle Bin")
            }
        }
    }

    // --- File Preview / Opener ---

    fun openFile(file: FileItem) {
        viewModelScope.launch {
            repository.addRecent(file.path, file.name, file.isDirectory, file.size)
        }
    }

    // --- App Lock Security ---

    fun setupAppLock(pin: String?) {
        viewModelScope.launch {
            if (pin.isNullOrEmpty()) {
                repository.saveSetting("app_lock_pin", "")
                _appLockPin.value = null
                _isAppLocked.value = false
                _isAppUnlocked.value = true
                _events.emit("App Lock disabled")
            } else {
                repository.saveSetting("app_lock_pin", pin)
                _appLockPin.value = pin
                _isAppLocked.value = true
                _isAppUnlocked.value = false
                _events.emit("App Lock enabled with secure PIN")
            }
        }
    }

    fun unlockApp(pinEntered: String): Boolean {
        val actualPin = _appLockPin.value
        return if (actualPin == pinEntered) {
            _isAppUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun lockApp() {
        if (!_appLockPin.value.isNullOrEmpty()) {
            _isAppUnlocked.value = false
        }
    }
}

class FileViewModelFactory(
    private val context: Context,
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FileViewModel(FileRepository(context, database)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
