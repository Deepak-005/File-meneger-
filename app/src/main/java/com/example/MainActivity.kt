package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.domain.FileItem
import com.example.ui.AppNavHost
import com.example.ui.Routes
import com.example.ui.screens.AppLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FileViewModel
import com.example.ui.viewmodel.FileViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "file_manager_db"
        ).fallbackToDestructiveMigration().build()

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            var useDynamicColors by remember { mutableStateOf(false) }

            MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = useDynamicColors) {
                MainContent(
                    database = database,
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = it },
                    useDynamicColors = useDynamicColors,
                    onDynamicColorsToggle = { useDynamicColors = it },
                    onShareFile = { file -> shareFileItem(file) }
                )
            }
        }
    }

    private fun shareFileItem(fileItem: FileItem) {
        val file = File(fileItem.path)
        if (!file.exists()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = fileItem.mimeType
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share File via"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    database: AppDatabase,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    useDynamicColors: Boolean,
    onDynamicColorsToggle: (Boolean) -> Unit,
    onShareFile: (FileItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: FileViewModel = viewModel(
        factory = FileViewModelFactory(context, database)
    )

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()
    val appLockPin by viewModel.appLockPin.collectAsState()

    // Permissions check
    var hasPermissions by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        viewModel.refreshFiles()
        viewModel.refreshStorageStats()
    }

    LaunchedEffect(Unit) {
        val reqs = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reqs.add(Manifest.permission.READ_MEDIA_IMAGES)
            reqs.add(Manifest.permission.READ_MEDIA_VIDEO)
            reqs.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            reqs.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            reqs.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val missing = reqs.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            hasPermissions = true
        }

        // Manage all files access for API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        addCategory("android.intent.category.DEFAULT")
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            }
        }
    }

    // Event collector
    LaunchedEffect(viewModel.events) {
        viewModel.events.collectLatest { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isAppUnlocked && !appLockPin.isNullOrEmpty()) {
            AppLockScreen(
                viewModel = viewModel,
                onUnlocked = { viewModel.refreshFiles() }
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(300.dp)
                    ) {
                        DrawerSheetContent(
                            onShortcutClick = { path ->
                                viewModel.navigateTo(path)
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(Routes.BROWSER)
                            },
                            onSettingsClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(Routes.SETTINGS)
                            }
                        )
                    }
                }
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        val currentRoute = currentDestination?.route

                        // Hide the primary scaffold topbar on viewers
                        val isViewer = currentRoute?.startsWith("image_viewer") == true ||
                                currentRoute?.startsWith("video_player") == true ||
                                currentRoute?.startsWith("audio_player") == true ||
                                currentRoute?.startsWith("text_editor") == true ||
                                currentRoute?.startsWith("pdf_viewer") == true

                        val selectedItems by viewModel.selectedItems.collectAsState()
                        val hideTopBar = isViewer || (currentRoute == Routes.BROWSER && selectedItems.isNotEmpty())

                        if (!hideTopBar) {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = when (currentRoute) {
                                            Routes.DASHBOARD -> "Storage Stats"
                                            Routes.BROWSER -> "File Browser"
                                            Routes.FAVORITES -> "Favorites"
                                            Routes.RECYCLE_BIN -> "Recycle Bin"
                                            Routes.SETTINGS -> "Settings"
                                            else -> "File Manager"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu Drawer")
                                    }
                                },
                                actions = {
                                    if (appLockPin != null) {
                                        IconButton(onClick = { viewModel.lockApp() }) {
                                            Icon(imageVector = Icons.Default.Security, contentDescription = "Lock App")
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    },
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        val currentRoute = currentDestination?.route

                        val isViewer = currentRoute?.startsWith("image_viewer") == true ||
                                currentRoute?.startsWith("video_player") == true ||
                                currentRoute?.startsWith("audio_player") == true ||
                                currentRoute?.startsWith("text_editor") == true ||
                                currentRoute?.startsWith("pdf_viewer") == true

                        val selectedItems by viewModel.selectedItems.collectAsState()
                        val hideBottomBar = isViewer || (currentRoute == Routes.BROWSER && selectedItems.isNotEmpty())

                        if (!hideBottomBar) {
                            NavigationBar {
                                val items = listOf(
                                    Triple(Routes.DASHBOARD, "Storage", Icons.Outlined.Storage to Icons.Filled.Storage),
                                    Triple(Routes.BROWSER, "Files", Icons.Default.Folder to Icons.Default.Folder),
                                    Triple(Routes.FAVORITES, "Starred", Icons.Outlined.Star to Icons.Filled.Star),
                                    Triple(Routes.RECYCLE_BIN, "Recycled", Icons.Outlined.Delete to Icons.Filled.Delete),
                                    Triple(Routes.SETTINGS, "Settings", Icons.Outlined.Settings to Icons.Filled.Settings)
                                )

                                items.forEach { (route, label, icons) ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == route } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) icons.second else icons.first,
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(text = label, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = onThemeToggle,
                        useDynamicColors = useDynamicColors,
                        onDynamicColorsToggle = onDynamicColorsToggle,
                        onShareFile = onShareFile,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerSheetContent(
    onShortcutClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val rootPath = Environment.getExternalStorageDirectory().absolutePath

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = "File Manager",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "File Manager",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Aesthetic M3 storage engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Storage Locations",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
        )

        val paths = listOf(
            Triple("Documents", "$rootPath/Documents", Icons.Default.Description),
            Triple("Downloads", "$rootPath/Download", Icons.Default.Android),
            Triple("Photos", "$rootPath/DCIM", Icons.Default.Photo),
            Triple("Pictures", "$rootPath/Pictures", Icons.Default.Photo),
            Triple("Music", "$rootPath/Music", Icons.Default.AudioFile),
            Triple("Movies", "$rootPath/Movies", Icons.Default.Movie)
        )

        paths.forEach { (name, path, icon) ->
            NavigationDrawerItem(
                label = { Text(name) },
                selected = false,
                onClick = { onShortcutClick(path) },
                icon = { Icon(imageVector = icon, contentDescription = name) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            onClick = onSettingsClick,
            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}
