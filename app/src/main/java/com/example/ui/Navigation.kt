package com.example.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.domain.FileItem
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ImageViewerScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.AudioPlayerScreen
import com.example.ui.screens.TextEditorScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.viewmodel.FileViewModel
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val DASHBOARD = "dashboard"
    const val BROWSER = "browser"
    const val FAVORITES = "favorites"
    const val RECYCLE_BIN = "recycle_bin"
    const val SETTINGS = "settings"
    const val IMAGE_VIEWER = "image_viewer?path={path}"
    const val VIDEO_PLAYER = "video_player?path={path}"
    const val AUDIO_PLAYER = "audio_player?path={path}"
    const val TEXT_EDITOR = "text_editor?path={path}"
    const val PDF_VIEWER = "pdf_viewer?path={path}"

    fun buildImageViewerRoute(path: String) = "image_viewer?path=${URLEncoder.encode(path, "UTF-8")}"
    fun buildVideoPlayerRoute(path: String) = "video_player?path=${URLEncoder.encode(path, "UTF-8")}"
    fun buildAudioPlayerRoute(path: String) = "audio_player?path=${URLEncoder.encode(path, "UTF-8")}"
    fun buildTextEditorRoute(path: String) = "text_editor?path=${URLEncoder.encode(path, "UTF-8")}"
    fun buildPdfViewerRoute(path: String) = "pdf_viewer?path=${URLEncoder.encode(path, "UTF-8")}"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: FileViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    useDynamicColors: Boolean,
    onDynamicColorsToggle: (Boolean) -> Unit,
    onShareFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    fun routeToFileViewer(file: FileItem) {
        viewModel.openFile(file)
        when {
            file.mimeType.startsWith("image/") -> navController.navigate(Routes.buildImageViewerRoute(file.path))
            file.mimeType.startsWith("video/") -> navController.navigate(Routes.buildVideoPlayerRoute(file.path))
            file.mimeType.startsWith("audio/") -> navController.navigate(Routes.buildAudioPlayerRoute(file.path))
            file.extension == "txt" || file.mimeType.startsWith("text/") -> navController.navigate(Routes.buildTextEditorRoute(file.path))
            file.extension == "pdf" -> navController.navigate(Routes.buildPdfViewerRoute(file.path))
            file.extension == "zip" -> {
                viewModel.extractZipFile(file)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier.fillMaxSize()
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                onCategoryClick = { mimeFilter ->
                    // Set query, navigate to browser
                    viewModel.setSearchQuery("")
                    navController.navigate(Routes.BROWSER)
                },
                onFileClick = { file -> routeToFileViewer(file) }
            )
        }

        composable(Routes.BROWSER) {
            BrowserScreen(
                viewModel = viewModel,
                onFileClick = { file -> routeToFileViewer(file) },
                onShareFile = onShareFile
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                viewModel = viewModel,
                onFileClick = { file -> routeToFileViewer(file) }
            )
        }

        composable(Routes.RECYCLE_BIN) {
            RecycleBinScreen(viewModel = viewModel)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                useDynamicColors = useDynamicColors,
                onDynamicColorsToggle = onDynamicColorsToggle
            )
        }

        composable(
            route = Routes.IMAGE_VIEWER,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", "UTF-8")
            val file = File(path)
            if (file.exists()) {
                ImageViewerScreen(
                    fileItem = FileItem(file.name, path, file.length(), file.lastModified(), false),
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = Routes.VIDEO_PLAYER,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", "UTF-8")
            val file = File(path)
            if (file.exists()) {
                VideoPlayerScreen(
                    fileItem = FileItem(file.name, path, file.length(), file.lastModified(), false),
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = Routes.AUDIO_PLAYER,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", "UTF-8")
            val file = File(path)
            if (file.exists()) {
                AudioPlayerScreen(
                    fileItem = FileItem(file.name, path, file.length(), file.lastModified(), false),
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = Routes.TEXT_EDITOR,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", "UTF-8")
            val file = File(path)
            if (file.exists()) {
                TextEditorScreen(
                    fileItem = FileItem(file.name, path, file.length(), file.lastModified(), false),
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = Routes.PDF_VIEWER,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { backStackEntry ->
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", "UTF-8")
            val file = File(path)
            if (file.exists()) {
                PdfViewerScreen(
                    fileItem = FileItem(file.name, path, file.length(), file.lastModified(), false),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
