package com.example.data

import android.content.Context
import android.os.Environment
import com.example.domain.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val favoriteDao = database.favoriteDao()
    private val recycleDao = database.recycleDao()
    private val recentDao = database.recentDao()
    private val settingDao = database.settingDao()

    // --- Room / Local DB Queries ---

    val allFavorites: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()
    val allRecycled: Flow<List<RecycleEntity>> = recycleDao.getAllRecycled()
    val recentFiles: Flow<List<RecentEntity>> = recentDao.getRecentFiles()

    fun isFavorite(path: String): Flow<Boolean> = favoriteDao.isFavorite(path)

    suspend fun addFavorite(path: String, name: String, isDirectory: Boolean) {
        favoriteDao.insertFavorite(FavoriteEntity(path, name, isDirectory))
    }

    suspend fun removeFavorite(path: String) {
        favoriteDao.deleteFavorite(path)
    }

    suspend fun addRecent(path: String, name: String, isDirectory: Boolean, size: Long) {
        recentDao.insertRecent(RecentEntity(path, name, isDirectory, size))
    }

    suspend fun removeRecent(path: String) {
        recentDao.deleteRecent(path)
    }

    suspend fun saveSetting(key: String, value: String) {
        settingDao.insertSetting(SettingEntity(key, value))
    }

    suspend fun getSetting(key: String): String? {
        return settingDao.getSetting(key)
    }

    // --- File Operations ---

    /**
     * Lists all files in a directory.
     * If the directory is empty or permissions are lacking, we return a list.
     * On first launch, if the app directory is empty, we pre-populate some elegant mock/sample files
     * in the app's internal or external files directory to give a rich experience!
     */
    suspend fun listFiles(directoryPath: String, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            return@withContext emptyList()
        }

        val files = dir.listFiles() ?: return@withContext emptyList()
        return@withContext files
            .filter { showHidden || !it.name.startsWith(".") }
            .map { file ->
                FileItem(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory
                )
            }
    }

    suspend fun createFolder(parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        val newFolder = File(parentPath, folderName)
        if (newFolder.exists()) return@withContext false
        return@withContext newFolder.mkdirs()
    }

    suspend fun createFile(parentPath: String, fileName: String, content: String = ""): Boolean = withContext(Dispatchers.IO) {
        val newFile = File(parentPath, fileName)
        if (newFile.exists()) return@withContext false
        return@withContext try {
            newFile.createNewFile()
            if (content.isNotEmpty()) {
                newFile.writeText(content)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    suspend fun renameFile(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val oldFile = File(oldPath)
        if (!oldFile.exists()) return@withContext false
        val newFile = File(oldFile.parent, newName)
        if (newFile.exists()) return@withContext false
        val success = oldFile.renameTo(newFile)
        if (success) {
            // Update favorite DB if name changed
            favoriteDao.deleteFavorite(oldPath)
            recentDao.deleteRecent(oldPath)
        }
        return@withContext success
    }

    /**
     * Safe Deletion: Move file/folder to recycle bin (a hidden folder in internal app cache),
     * and save metadata to Room.
     */
    suspend fun moveToRecycleBin(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext false

        val recycleDir = File(context.cacheDir, ".recycle_bin")
        if (!recycleDir.exists()) recycleDir.mkdirs()

        val recycleFile = File(recycleDir, "${System.currentTimeMillis()}_${file.name}")
        val success = file.renameTo(recycleFile)
        if (success) {
            recycleDao.insertRecycle(
                RecycleEntity(
                    recyclePath = recycleFile.absolutePath,
                    originalPath = file.absolutePath,
                    fileName = file.name,
                    isDirectory = file.isDirectory,
                    size = if (file.isDirectory) 0L else file.length()
                )
            )
            // Remove from favorites/recents
            favoriteDao.deleteFavorite(path)
            recentDao.deleteRecent(path)
        }
        return@withContext success
    }

    suspend fun restoreFromRecycleBin(recycleItem: RecycleEntity): Boolean = withContext(Dispatchers.IO) {
        val recycledFile = File(recycleItem.recyclePath)
        if (!recycledFile.exists()) return@withContext false

        val originalFile = File(recycleItem.originalPath)
        // Ensure parent directories exist
        originalFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val success = recycledFile.renameTo(originalFile)
        if (success) {
            recycleDao.deleteRecycle(recycleItem.recyclePath)
        }
        return@withContext success
    }

    suspend fun deletePermanently(path: String, isRecycled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext false

        val success = deleteRecursively(file)
        if (success && isRecycled) {
            recycleDao.deleteRecycle(path)
        }
        return@withContext success
    }

    suspend fun clearRecycleBin(): Boolean = withContext(Dispatchers.IO) {
        val recycleDir = File(context.cacheDir, ".recycle_bin")
        if (recycleDir.exists()) {
            val success = deleteRecursively(recycleDir)
            recycleDao.clearRecycleBin()
            return@withContext success
        }
        return@withContext true
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        return file.delete()
    }

    // --- Clipboard Operations ---

    suspend fun copyFile(srcPath: String, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(srcPath)
        if (!src.exists()) return@withContext false
        val dest = File(destDirPath, src.name)
        return@withContext try {
            copyRecursively(src, dest)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun copyRecursively(src: File, dest: File) {
        if (src.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyRecursively(child, File(dest, child.name))
            }
        } else {
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun moveFile(srcPath: String, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(srcPath)
        if (!src.exists()) return@withContext false
        val dest = File(destDirPath, src.name)
        return@withContext src.renameTo(dest)
    }

    // --- ZIP Operations ---

    suspend fun compressToZip(filePaths: List<String>, zipName: String, parentPath: String): Boolean = withContext(Dispatchers.IO) {
        val zipFile = File(parentPath, if (zipName.endsWith(".zip")) zipName else "$zipName.zip")
        return@withContext try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (path in filePaths) {
                    val file = File(path)
                    addFileToZip(zos, file, "")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, fileToZip: File, parentDirectoryName: String) {
        if (fileToZip.isHidden) return

        val zipEntryName = if (parentDirectoryName.isEmpty()) {
            fileToZip.name
        } else {
            "$parentDirectoryName/${fileToZip.name}"
        }

        if (fileToZip.isDirectory) {
            zos.putNextEntry(ZipEntry("$zipEntryName/"))
            zos.closeEntry()
            fileToZip.listFiles()?.forEach { childFile ->
                addFileToZip(zos, childFile, zipEntryName)
            }
        } else {
            FileInputStream(fileToZip).use { fis ->
                zos.putNextEntry(ZipEntry(zipEntryName))
                val bytes = ByteArray(1024)
                var length: Int
                while (fis.read(bytes).also { length = it } >= 0) {
                    zos.write(bytes, 0, length)
                }
                zos.closeEntry()
            }
        }
    }

    suspend fun extractZip(zipPath: String, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) return@withContext false
        val destDir = File(destDirPath)
        if (!destDir.exists()) destDir.mkdirs()

        return@withContext try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            val buffer = ByteArray(1024)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Storage Analysis ---

    data class StorageStats(
        val totalSpace: Long,
        val freeSpace: Long,
        val usedSpace: Long,
        val imagesSize: Long,
        val videosSize: Long,
        val audioSize: Long,
        val documentsSize: Long,
        val appsSize: Long,
        val zipSize: Long,
        val otherSize: Long
    )

    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val path = Environment.getExternalStorageDirectory()
        val totalSpace = path.totalSpace
        val freeSpace = path.freeSpace
        val usedSpace = totalSpace - freeSpace

        var imagesSize = 0L
        var videosSize = 0L
        var audioSize = 0L
        var documentsSize = 0L
        var appsSize = 0L
        var zipSize = 0L
        var otherSize = 0L

        fun scanDir(file: File) {
            val list = file.listFiles() ?: return
            for (f in list) {
                if (f.name.startsWith(".")) continue // Skip hidden
                if (f.isDirectory) {
                    // limit recursion depth or skip Android folder to avoid massive lags
                    if (f.name == "Android") continue
                    scanDir(f)
                } else {
                    val ext = f.extension.lowercase()
                    val size = f.length()
                    when (ext) {
                        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> imagesSize += size
                        "mp4", "mkv", "3gp", "webm", "avi" -> videosSize += size
                        "mp3", "wav", "ogg", "m4a", "aac", "flac" -> audioSize += size
                        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md" -> documentsSize += size
                        "apk" -> appsSize += size
                        "zip", "rar", "tar", "gz", "7z" -> zipSize += size
                        else -> otherSize += size
                    }
                }
            }
        }

        // Run scan on primary storage
        scanDir(path)

        // Adjust "otherSize" so stats add up perfectly to used space
        val scanTotal = imagesSize + videosSize + audioSize + documentsSize + appsSize + zipSize
        val remainder = usedSpace - scanTotal
        if (remainder > 0) {
            otherSize += remainder
        }

        return@withContext StorageStats(
            totalSpace = totalSpace,
            freeSpace = freeSpace,
            usedSpace = usedSpace,
            imagesSize = imagesSize,
            videosSize = videosSize,
            audioSize = audioSize,
            documentsSize = documentsSize,
            appsSize = appsSize,
            zipSize = zipSize,
            otherSize = otherSize
        )
    }

    // --- Populate Sample Files for Initial Rich State ---
    suspend fun prepopulateSampleFilesIfNeeded() = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val appRoot = File(root, "FileManagerDocs")
        if (!appRoot.exists()) {
            appRoot.mkdirs()
        }

        // Create sample folders
        val folders = listOf("Documents", "Photos", "Audios", "Archive")
        folders.forEach { fName ->
            val folder = File(appRoot, fName)
            if (!folder.exists()) folder.mkdirs()
        }

        // Add some sample documents
        val readme = File(appRoot, "README.txt")
        if (!readme.exists()) {
            readme.writeText(
                "Welcome to modern Material 3 File Manager!\n\n" +
                        "This app is running in a secure, performant sandbox.\n" +
                        "You can manage all your files here with advanced tools:\n" +
                        " - Real-time storage analyzer & usage canvas\n" +
                        " - High contrast dark/light Material You styling\n" +
                        " - Recycle bin with safety restore mechanism\n" +
                        " - Built-in secure code/text editor\n" +
                        " - Built-in secure image and audio/video players\n" +
                        " - Multi-selection copy, move, cut, paste\n" +
                        " - Full ZIP archive compression and extraction\n\n" +
                        "Enjoy managing files on Android!"
            )
        }

        val projectNotes = File(File(appRoot, "Documents"), "Project_Notes.txt")
        if (!projectNotes.exists()) {
            projectNotes.writeText(
                "Material 3 File Manager Design Specs:\n\n" +
                        "- Concentric usage storage graph with gorgeous radial spacing\n" +
                        "- Smooth spring animations (<300ms) for folder transition\n" +
                        "- Responsive grid/list representation\n" +
                        "- Secure PIN lock fallback database"
            )
        }

        // Dummy text file pretending to be PDF/ZIP just for testing views
        val sampleZip = File(File(appRoot, "Archive"), "sample_archive.zip")
        if (!sampleZip.exists()) {
            sampleZip.writeText("Pretend zip archive data")
        }

        val samplePdf = File(File(appRoot, "Documents"), "User_Guide.pdf")
        if (!samplePdf.exists()) {
            samplePdf.writeText("Pretend PDF instructions handbook")
        }
    }
}
