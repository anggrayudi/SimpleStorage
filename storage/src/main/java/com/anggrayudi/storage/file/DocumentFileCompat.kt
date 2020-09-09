package com.anggrayudi.storage.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.extension.getAppDirectory
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

/**
 * Created on 16/08/20
 * @author Anggrayudi H
 */
object DocumentFileCompat {

    const val PRIMARY = "primary"

    const val MIME_TYPE_UNKNOWN = "*/*"

    const val MIME_TYPE_BINARY_FILE = "application/octet-stream"

    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    const val DOWNLOADS_FOLDER_AUTHORITY = "com.android.providers.downloads.documents"

    const val MEDIA_FOLDER_AUTHORITY = "com.android.providers.media.documents"

    val FILE_NAME_DUPLICATION_REGEX_WITH_EXTENSION = Regex("(.*?) \\(\\d+\\)\\.[a-zA-Z0-9]+")

    val FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION = Regex("(.*?) \\(\\d+\\)")

    fun isRootUri(uri: Uri): Boolean {
        val path = uri.path ?: return false
        return uri.authority == EXTERNAL_STORAGE_AUTHORITY && path.indexOf(':') == path.length - 1
    }

    /**
     * If given [Uri] with path `/tree/primary:Downloads/MyVideo.mp4`, then return `primary`.
     */
    fun getStorageId(uri: Uri): String = if (uri.scheme == ContentResolver.SCHEME_FILE) {
        PRIMARY
    } else {
        if (uri.authority == EXTERNAL_STORAGE_AUTHORITY) uri.path!!.substringBefore(':').substringAfterLast('/') else ""
    }

    /**
     * @param storageId If in SD card, it should be integers like `6881-2249`. Otherwise, if in external storage it will be [PRIMARY].
     * @param filePath If in Downloads folder of SD card, it will be `Downloads/MyMovie.mp4`.
     *                 If in external storage it will be `Downloads/MyMovie.mp4` as well.
     */
    fun fromPath(context: Context, storageId: String = PRIMARY, filePath: String): DocumentFile? {
        return if (filePath.isEmpty()) {
            getRootDocumentFile(context, storageId)
        } else {
            exploreFile(context, storageId, filePath)
        }
    }

    /**
     * @param fileFullPath For file in external storage => `/storage/emulated/0/Downloads/MyMovie.mp4`.
     *                     For file in SD card => `9016-4EF8:Downloads/MyMovie.mp4`
     */
    fun fromFullPath(context: Context, fileFullPath: String): DocumentFile? {
        return if (fileFullPath.startsWith('/')) {
            fromFile(context, File(fileFullPath))
        } else {
            fromPath(context, fileFullPath.substringBefore(':'), fileFullPath.substringAfter(':'))
        }
    }

    /**
     * Since Android 10, only app directory that is accessible by [File], e.g. `/storage/emulated/0/Android/data/com.anggrayudi.storage.sample/files`
     *
     * This function allows you to read and write files in external storage, regardless of API levels. Suppose that you input a [File] with
     * path `/storage/emulated/0/Music` on Android 10. This function will convert the [File] to [DocumentFile] with URI
     * `content://com.android.externalstorage.documents/tree/primary:/document/primary:Music`
     */
    fun fromFile(context: Context, file: File): DocumentFile? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            DocumentFile.fromFile(file)
        } else {
            val filePath = file.path.replaceFirst(SimpleStorage.externalStoragePath, "")
                .removeProhibitedCharsFromFilename()
                .trim { it == '/' }
            exploreFile(context, PRIMARY, filePath)
        }
    }

    /**
     * Returns `null` if folder does not exist or you have no permission on this directory
     */
    @Suppress("DEPRECATION")
    fun fromPublicFolder(context: Context, type: PublicDirectory): DocumentFile? {
        return try {
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                    DocumentFile.fromFile(Environment.getExternalStoragePublicDirectory(type.folderName))
                }
                type == PublicDirectory.DOWNLOADS -> {
                    DocumentFile.fromTreeUri(context, Uri.parse("content://$DOWNLOADS_FOLDER_AUTHORITY/tree/downloads"))
                }
                else -> fromPath(context, filePath = type.folderName)
            }
        } catch (e: SecurityException) {
            null
        }
    }

    fun getRootDocumentFile(context: Context, storageId: String): DocumentFile? = try {
        DocumentFile.fromTreeUri(context, createDocumentUri(storageId))
    } catch (e: SecurityException) {
        null
    }

    fun createDocumentUri(storageId: String, filePath: String = ""): Uri =
        Uri.parse("content://$EXTERNAL_STORAGE_AUTHORITY/tree/" + Uri.encode("$storageId:$filePath"))

    fun isAccessGranted(context: Context, storageId: String): Boolean {
        return storageId == PRIMARY && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || getRootDocumentFile(context, storageId)?.run { canRead() && canWrite() } ?: false
    }

    /**
     * Check if storage has URI permission for read and write access.
     */
    fun isStorageUriPermissionGranted(context: Context, storageId: String): Boolean {
        val root = createDocumentUri(storageId)
        return context.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.isWritePermission && it.uri == root }
    }

    fun getStorageIds(context: Context): List<String> {
        val storageIds = mutableListOf<String>()
        val externalStoragePath = SimpleStorage.externalStoragePath
        ContextCompat.getExternalFilesDirs(context, null).map { it.path }.forEach {
            if (it.startsWith(externalStoragePath)) {
                // Path -> /storage/emulated/0/Android/data/com.anggrayudi.storage.sample/files
                storageIds.add(PRIMARY)
            } else {
                // Path -> /storage/131D-261A/Android/data/com.anggrayudi.storage.sample/files
                val sdCardId = it.replaceFirst("/storage/", "").substringBefore('/')
                storageIds.add(sdCardId)
            }
        }
        return storageIds
    }

    fun getSdCardIds(context: Context) = getStorageIds(context).filter { it != PRIMARY }

    /**
     * Create folders. You should do this process in background.
     *
     * @return `null` if you have no storage permission. Will return to current directory if not success.
     */
    fun mkdirs(context: Context, storageId: String = PRIMARY, folderPath: String): DocumentFile? {
        if (storageId == PRIMARY && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // use java.io.File for faster performance
            val file = File(SimpleStorage.externalStoragePath + "/" + folderPath.removeProhibitedCharsFromFilename())
            file.mkdirs()
            if (file.isDirectory)
                return DocumentFile.fromFile(file)
        } else {
            var currentDirectory = getRootDocumentFile(context, storageId) ?: return null
            getDirectorySequence(folderPath.removeProhibitedCharsFromFilename()).forEach {
                try {
                    val file = currentDirectory.findFile(it)
                    if (file == null || file.isFile)
                        currentDirectory = currentDirectory.createDirectory(it) ?: return null
                    else if (file.isDirectory)
                        currentDirectory = file
                } catch (e: Throwable) {
                    return null
                }
            }
            return currentDirectory
        }
        return null
    }

    /**
     * @return `null` if you don't have storage permission.
     */
    fun createFile(context: Context, storageId: String = PRIMARY, filePath: String, mimeType: String = MIME_TYPE_UNKNOWN): DocumentFile? {
        return if (storageId == PRIMARY && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = File(SimpleStorage.externalStoragePath + "/" + filePath.removeProhibitedCharsFromFilename())
            file.parentFile?.mkdirs()
            if (create(file)) DocumentFile.fromFile(file) else null
        } else try {
            val directory = mkdirsParentDirectory(context, storageId, filePath)
            val filename = getFileNameFromPath(filePath).removeProhibitedCharsFromFilename()
            if (filename.isEmpty()) null else directory?.createFile(mimeType, filename)
        } catch (e: Throwable) {
            null
        }
    }

    private fun getParentPath(filePath: String): String? = getDirectorySequence(filePath).let { it.getOrNull(it.size - 2) }

    private fun mkdirsParentDirectory(context: Context, storageId: String, filePath: String): DocumentFile? {
        val parentPath = getParentPath(filePath)
        return if (parentPath != null) {
            mkdirs(context, storageId, parentPath)
        } else {
            getRootDocumentFile(context, storageId)
        }
    }

    private fun getFileNameFromPath(filePath: String) = filePath.substringAfterLast('/')

    fun recreate(context: Context, storageId: String = PRIMARY, filePath: String, mimeType: String = MIME_TYPE_UNKNOWN): DocumentFile? {
        if (storageId == PRIMARY && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = File(filePath.removeProhibitedCharsFromFilename())
            file.delete()
            file.parentFile?.mkdirs()
            return if (create(file)) DocumentFile.fromFile(file) else null
        } else {
            val directory = mkdirsParentDirectory(context, storageId, filePath)
            val filename = getFileNameFromPath(filePath).removeProhibitedCharsFromFilename()
            if (filename.isEmpty()) {
                return null
            }
            val existingFile = directory?.findFile(filename)
            if (existingFile?.isFile == true) {
                existingFile.delete()
            }
            return directory?.createFile(mimeType, filename)
        }
    }

    private fun create(file: File): Boolean {
        return try {
            file.createNewFile() || file.isFile && file.length() == 0L
        } catch (e: IOException) {
            false
        }
    }

    private fun String.removeProhibitedCharsFromFilename(): String = replace(":", "_")

    private fun exploreFile(context: Context, storageId: String, filePath: String): DocumentFile? {
        var current = getRootDocumentFile(context, storageId) ?: return null
        getDirectorySequence(filePath).forEach {
            current = current.findFile(it) ?: return null
        }
        return current
    }

    /**
     * For example, `Downloads/Video/Sports/` will become array `["Downloads", "Video", "Sports"]`
     */
    private fun getDirectorySequence(file: String): Array<String> {
        val cleanedPath = cleanStoragePath(file)
        if (cleanedPath.isNotEmpty()) {
            return if (cleanedPath.contains("/")) {
                cleanedPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            } else {
                arrayOf(cleanedPath)
            }
        }
        return emptyArray()
    }

    /**
     * It will remove double slash (`/`) when found
     */
    private fun cleanStoragePath(file: String): String {
        val resolvedPath = StringBuilder()
        file.split("/".toRegex())
            .dropLastWhile { it.isEmpty() }
            .map { it.trim { c -> c <= ' ' || c == '/' } }
            .filter { it.isNotEmpty() }
            .forEach { directory -> resolvedPath.append(directory).append("/") }
        return resolvedPath.toString().substringBeforeLast('/')
    }

    private fun recreateAppDirectory(context: Context) = context.getAppDirectory().apply { File(this).mkdirs() }

    /** Get available space in bytes. */
    @Suppress("DEPRECATION")
    fun getFreeSpace(context: Context, storageId: String): Long {
        try {
            if (storageId == PRIMARY) {
                val stat = StatFs(recreateAppDirectory(context))
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                    stat.availableBytes
                else
                    (stat.blockSize * stat.availableBlocks).toLong()
            } else if (Build.VERSION.SDK_INT >= 21) {
                try {
                    val fileUri = getDocumentFileForStorageInfo(context, storageId)?.uri ?: return 0
                    context.contentResolver.openFileDescriptor(fileUri, "r")?.use {
                        val stats = Os.fstatvfs(it.fileDescriptor)
                        return stats.f_bavail * stats.f_frsize
                    }
                } catch (e: ErrnoException) {
                    // ignore
                }
            }
        } catch (e: SecurityException) {
            // ignore
        } catch (e: IllegalArgumentException) {
            // ignore
        } catch (e: IOException) {
            // ignore
        }
        return 0
    }

    /** Get available space in bytes. */
    @Suppress("DEPRECATION")
    fun getUsedSpace(context: Context, storageId: String): Long {
        try {
            if (storageId == PRIMARY) {
                val stat = StatFs(recreateAppDirectory(context))
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                    stat.totalBytes - stat.availableBytes
                else
                    (stat.blockSize * stat.blockCount - stat.blockSize * stat.availableBlocks).toLong()
            } else if (Build.VERSION.SDK_INT >= 21) {
                try {
                    val fileUri = getDocumentFileForStorageInfo(context, storageId)?.uri ?: return 0
                    context.contentResolver.openFileDescriptor(fileUri, "r")?.use {
                        val stats = Os.fstatvfs(it.fileDescriptor)
                        return stats.f_blocks * stats.f_frsize - stats.f_bavail * stats.f_frsize
                    }
                } catch (e: ErrnoException) {
                    // ignore
                }
            }
        } catch (e: SecurityException) {
            // ignore
        } catch (e: IllegalArgumentException) {
            // ignore
        } catch (e: IOException) {
            // ignore
        }
        return 0
    }

    @Suppress("DEPRECATION")
    fun getStorageCapacity(context: Context, storageId: String): Long {
        try {
            if (storageId == PRIMARY) {
                val stat = StatFs(recreateAppDirectory(context))
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                    stat.totalBytes
                else
                    (stat.blockSize * stat.blockCount).toLong()
            } else if (Build.VERSION.SDK_INT >= 21) {
                try {
                    val fileUri = getDocumentFileForStorageInfo(context, storageId)?.uri ?: return 0
                    context.contentResolver.openFileDescriptor(fileUri, "r")?.use {
                        val stats = Os.fstatvfs(it.fileDescriptor)
                        return stats.f_blocks * stats.f_frsize
                    }
                } catch (e: ErrnoException) {
                    // ignore
                }
            }
        } catch (e: SecurityException) {
            // ignore
        } catch (e: IllegalArgumentException) {
            // ignore
        } catch (e: IOException) {
            // ignore
        }
        return 0
    }

    private fun getDocumentFileForStorageInfo(context: Context, storageId: String): DocumentFile? {
        return if (storageId == PRIMARY) {
            // use app private directory, so no permissions required
            val privateAppDirectory = context.getExternalFilesDir(null) ?: return null
            privateAppDirectory.mkdirs()
            DocumentFile.fromFile(privateAppDirectory)
        } else {
            getRootDocumentFile(context, storageId)
        }
    }

    fun getFileNameFromUrl(url: String): String {
        return try {
            URLDecoder.decode(url, "UTF-8").substringAfterLast('/')
        } catch (e: UnsupportedEncodingException) {
            url
        }
    }
}