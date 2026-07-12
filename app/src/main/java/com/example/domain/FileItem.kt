package com.example.domain

import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String = if (isDirectory) "" else File(path).extension.lowercase(),
    val mimeType: String = getMimeTypeFromExtension(extension),
    val isFavorite: Boolean = false,
    val isHidden: Boolean = name.startsWith(".")
) {
    val formattedSize: String
        get() {
            if (isDirectory) {
                // Directories don't have direct sizes represented easily, but we'll return item count if cached
                return ""
            }
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

    val formattedDate: String
        get() {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(lastModified))
        }

    companion object {
        fun getMimeTypeFromExtension(ext: String): String {
            return when (ext) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
                "mp4", "mkv", "3gp", "webm", "avi" -> "video/*"
                "mp3", "wav", "ogg", "m4a", "aac", "flac" -> "audio/*"
                "pdf" -> "application/pdf"
                "zip", "rar", "tar", "gz", "7z" -> "application/zip"
                "apk" -> "application/vnd.android.package-archive"
                "txt", "html", "xml", "json", "kt", "java", "css", "js", "md", "csv" -> "text/plain"
                else -> "*/*"
            }
        }
    }
}
