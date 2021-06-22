package com.anggrayudi.storage.file

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.system.Os
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.extension.*
import com.anggrayudi.storage.file.StorageId.DATA
import com.anggrayudi.storage.file.StorageId.PRIMARY
import com.anggrayudi.storage.media.FileDescription
import com.anggrayudi.storage.media.MediaStoreCompat
import java.io.File
import java.io.IOException
import java.net.URLDecoder

/**
 * Created on 16/08/20
 * @author Anggrayudi H
 */
object DocumentFileCompat {

    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /*
    File picker for each API version gives the following URIs:
    * API 26 - 27 => content://com.android.providers.downloads.documents/document/22
    * API 28 - 29 => content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fscreenshot.jpeg
    * API 30+     => content://com.android.providers.downloads.documents/document/msf%3A42
     */
    const val DOWNLOADS_FOLDER_AUTHORITY = "com.android.providers.downloads.documents"

    const val MEDIA_FOLDER_AUTHORITY = "com.android.providers.media.documents"

    /**
     * Only available on API 26 to 29.
     */
    const val DOWNLOADS_TREE_URI = "content://$DOWNLOADS_FOLDER_AUTHORITY/tree/downloads"

    val FILE_NAME_DUPLICATION_REGEX_WITH_EXTENSION = Regex("(.*?) \\(\\d+\\)\\.[a-zA-Z0-9]+")

    val FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION = Regex("(.*?) \\(\\d+\\)")

    @JvmStatic
    fun isRootUri(uri: Uri): Boolean {
        val path = uri.path ?: return false
        return uri.isExternalStorageDocument && path.indexOf(':') == path.length - 1
    }

    /**
     * @param fullPath For SD card can be full path `storage/6881-2249/Music` or simple path `6881-2249:Music`.
     *             For primary storage can be `/storage/emulated/0/Music` or simple path `primary:Music`.
     */
    @JvmStatic
    fun getStorageId(context: Context, fullPath: String): String {
        return if (fullPath.startsWith('/')) {
            when {
                fullPath.startsWith(SimpleStorage.externalStoragePath) -> PRIMARY
                fullPath.startsWith(context.dataDirectory.path) -> DATA
                else -> fullPath.substringAfter("/storage/", "").substringBefore('/')
            }
        } else {
            fullPath.substringBefore(':', "").substringAfterLast('/')
        }
    }

    /**
     * @param fullPath For SD card can be full path `storage/6881-2249/Music` or simple path `6881-2249:Music`.
     *             For primary storage can be `/storage/emulated/0/Music` or simple path `primary:Music`.
     * @return Given `storage/6881-2249/Music/My Love.mp3`, then return `Music/My Love.mp3`.
     *          May return empty `String` if it is a root path of the storage.
     */
    @JvmStatic
    fun getBasePath(context: Context, fullPath: String): String {
        val basePath = if (fullPath.startsWith('/')) {
            val dataDir = context.dataDirectory.path
            val externalStoragePath = SimpleStorage.externalStoragePath
            when {
                fullPath.startsWith(externalStoragePath) -> fullPath.substringAfter(externalStoragePath)
                fullPath.startsWith(dataDir) -> fullPath.substringAfter(dataDir)
                else -> fullPath.substringAfter("/storage/", "").substringAfter('/', "")
            }
        } else {
            fullPath.substringAfter(':', "")
        }
        return basePath.trimFileSeparator().removeForbiddenCharsFromFilename()
    }

    @JvmStatic
    fun fromUri(context: Context, uri: Uri): DocumentFile? {
        return when {
            uri.isRawFile -> File(uri.path ?: return null).run { if (canRead()) DocumentFile.fromFile(this) else null }
            uri.isTreeDocumentFile -> context.fromTreeUri(uri)?.run { if (isDownloadsDocument) toWritableDownloadsDocumentFile(context) else this }
            else -> context.fromSingleUri(uri)
        }
    }

    /**
     * @param storageId If in SD card, it should be integers like `6881-2249`. Otherwise, if in external storage it will be [PRIMARY].
     * @param basePath If in Downloads folder of SD card, it will be `Downloads/MyMovie.mp4`.
     *                 If in external storage it will be `Downloads/MyMovie.mp4` as well.
     */
    @JvmOverloads
    @JvmStatic
    fun fromSimplePath(
        context: Context,
        storageId: String = PRIMARY,
        basePath: String = "",
        documentType: DocumentFileType = DocumentFileType.ANY,
        considerRawFile: Boolean = true
    ): DocumentFile? {
        if (storageId == DATA) {
            return DocumentFile.fromFile(context.dataDirectory.child(basePath))
        }
        return if (basePath.isEmpty()) {
            getRootDocumentFile(context, storageId, considerRawFile)
        } else {
            val file = exploreFile(context, storageId, basePath, documentType, considerRawFile)
            if (file == null && basePath.startsWith(Environment.DIRECTORY_DOWNLOADS) && storageId == PRIMARY) {
                val downloads = context.fromTreeUri(Uri.parse(DOWNLOADS_TREE_URI))?.takeIf { it.canRead() } ?: return null
                downloads.child(context, basePath.substringAfter('/', ""))?.takeIf {
                    documentType == DocumentFileType.ANY
                            || documentType == DocumentFileType.FILE && it.isFile
                            || documentType == DocumentFileType.FOLDER && it.isDirectory
                }
            } else {
                file
            }
        }
    }

    /**
     * `fileFullPath` for example:
     * * For file in external storage => `/storage/emulated/0/Downloads/MyMovie.mp4`.
     * * For file in SD card => `/storage/9016-4EF8/Downloads/MyMovie.mp4` or you can input simple path like this `9016-4EF8:Downloads/MyMovie.mp4`.
     *                          You can input `9016-4EF8:` or `/storage/9016-4EF8` for SD card's root path.
     * @see DocumentFile.getAbsolutePath
     */
    @JvmOverloads
    @JvmStatic
    fun fromFullPath(
        context: Context,
        fullPath: String,
        documentType: DocumentFileType = DocumentFileType.ANY,
        considerRawFile: Boolean = true
    ): DocumentFile? {
        return if (fullPath.startsWith('/')) {
            // absolute path
            fromFile(context, File(fullPath), documentType, considerRawFile)
        } else {
            // simple path
            fromSimplePath(context, fullPath.substringBefore(':'), fullPath.substringAfter(':'), documentType, considerRawFile)
        }
    }

    /**
     * Since Android 10, only app directory that is accessible by [File], e.g. `/storage/emulated/0/Android/data/com.anggrayudi.storage.sample/files`
     *
     * To continue using [File], you need to request full storage access via [SimpleStorage.requestFullStorageAccess]
     *
     * This function allows you to read and write files in external storage, regardless of API levels.
     *
     * @param considerRawFile `true` if you want to consider faster performance with [File]
     * @return `TreeDocumentFile` if `considerRawFile` is false, or if the given [File] can be read with URI permission only, otherwise return `RawDocumentFile`
     */
    @JvmOverloads
    @JvmStatic
    fun fromFile(
        context: Context,
        file: File,
        documentType: DocumentFileType = DocumentFileType.ANY,
        considerRawFile: Boolean = true
    ): DocumentFile? {
        return if (file.canRead() && (considerRawFile || file.isExternalStorageManager(context))) {
            if (documentType == DocumentFileType.FILE && !file.isFile || documentType == DocumentFileType.FOLDER && !file.isDirectory)
                null
            else
                DocumentFile.fromFile(file)
        } else {
            val basePath = file.getBasePath(context).removeForbiddenCharsFromFilename().trimFileSeparator()
            exploreFile(context, file.getStorageId(context), basePath, documentType, considerRawFile)
                ?: fromSimplePath(context, file.getStorageId(context), basePath, documentType, considerRawFile)
        }
    }

    /**
     * Returns `null` if folder does not exist or you have no permission on this directory
     *
     * @param subFile can input sub folder or sub file
     */
    @JvmOverloads
    @JvmStatic
    @Suppress("DEPRECATION")
    fun fromPublicFolder(
        context: Context,
        type: PublicDirectory,
        subFile: String = "",
        requiresWriteAccess: Boolean = false,
        considerRawFile: Boolean = true
    ): DocumentFile? {
        var rawFile = Environment.getExternalStoragePublicDirectory(type.folderName)
        if (subFile.isNotEmpty()) {
            rawFile = File("$rawFile/$subFile".trimEnd('/'))
        }
        if (rawFile.canRead() && (considerRawFile || rawFile.isExternalStorageManager(context))
            && (requiresWriteAccess && rawFile.isWritable(context) || !requiresWriteAccess)
        ) {
            return DocumentFile.fromFile(rawFile)
        }

        val folder = if (type == PublicDirectory.DOWNLOADS) {
            /*
            Root path will be                   => content://com.android.providers.downloads.documents/tree/downloads/document/downloads
            Get file/listFiles() will be        => content://com.android.providers.downloads.documents/tree/downloads/document/msf%3A268
            When creating files with makeFile() => content://com.android.providers.downloads.documents/tree/downloads/document/147
            When creating directory  "IKO5"     => content://com.android.providers.downloads.documents/tree/downloads/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FIKO5

            Seems that com.android.providers.downloads.documents no longer available on SAF's folder selector on API 30+.

            You can create directory with authority com.android.providers.downloads.documents on API 29,
            but unfortunately cannot create file in the directory. So creating directory with this authority is useless.
            Hence, convert it to writable URI with DocumentFile.toWritableDownloadsDocumentFile()
            */
            val downloadFolder = context.fromTreeUri(Uri.parse(DOWNLOADS_TREE_URI))
            if (downloadFolder?.canRead() == true) {
                downloadFolder.child(context, subFile, requiresWriteAccess)
            } else {
                fromFullPath(context, rawFile.absolutePath, considerRawFile = false)
            }
        } else {
            fromFullPath(context, rawFile.absolutePath, considerRawFile = false)
        }
        return folder?.takeIf { it.canRead() && (requiresWriteAccess && folder.isWritable(context) || !requiresWriteAccess) }
    }

    /**
     * To get root file access on API 30+, you need to have full storage access by
     * granting [Manifest.permission.MANAGE_EXTERNAL_STORAGE] in runtime.
     * @see SimpleStorage.requestFullStorageAccess
     * @see SimpleStorage.hasFullDiskAccess
     * @see Environment.isExternalStorageManager
     * @see getRootRawFile
     */
    @JvmOverloads
    @JvmStatic
    fun getRootDocumentFile(
        context: Context,
        storageId: String,
        requiresWriteAccess: Boolean = false,
        considerRawFile: Boolean = true
    ): DocumentFile? {
        if (storageId == DATA) {
            return DocumentFile.fromFile(context.dataDirectory)
        }
        return if (considerRawFile) {
            getRootRawFile(context, storageId, requiresWriteAccess)?.let { DocumentFile.fromFile(it) }
                ?: context.fromTreeUri(createDocumentUri(storageId))
        } else {
            context.fromTreeUri(createDocumentUri(storageId))
        }
    }

    /**
     * In API 29+, `/storage/emulated/0` may not be granted for URI permission,
     * but all directories under `/storage/emulated/0/Music` are granted and accessible.
     *
     * For example, given `/storage/emulated/0/Music/Metal`, then return `/storage/emulated/0/Music`
     *
     * @param fullPath construct it using [buildAbsolutePath] or [buildSimplePath]
     * @return `null` if accessible root path is not found in [ContentResolver.getPersistedUriPermissions], or the folder does not exist.
     */
    @Suppress("DEPRECATION")
    @JvmOverloads
    @JvmStatic
    fun getAccessibleRootDocumentFile(
        context: Context,
        fullPath: String,
        requiresWriteAccess: Boolean = false,
        considerRawFile: Boolean = true
    ): DocumentFile? {
        if (considerRawFile && fullPath.startsWith('/')) {
            val rootFile = File(fullPath).getRootRawFile(context, requiresWriteAccess)
            if (rootFile != null) {
                return DocumentFile.fromFile(rootFile)
            }
        }
        val storageId = getStorageId(context, fullPath)
        if (storageId == DATA) {
            return DocumentFile.fromFile(context.dataDirectory)
        }
        if (storageId.isNotEmpty()) {
            val cleanBasePath = getBasePath(context, fullPath)
            val downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            context.contentResolver.persistedUriPermissions
                // For instance, content://com.android.externalstorage.documents/tree/primary%3AMusic
                .filter { it.isReadPermission && it.isWritePermission && it.uri.isTreeDocumentFile }
                .forEach {
                    if (fullPath.startsWith(downloadPath) && it.uri.isDownloadsDocument) {
                        return context.fromTreeUri(Uri.parse(DOWNLOADS_TREE_URI))
                    }

                    val uriPath = it.uri.path // e.g. /tree/primary:Music
                    if (uriPath != null && it.uri.isExternalStorageDocument) {
                        val currentStorageId = uriPath.substringBefore(':').substringAfterLast('/')
                        val currentRootFolder = uriPath.substringAfter(':', "")
                        if (currentStorageId == storageId && (currentRootFolder.isEmpty() || cleanBasePath.hasParent(currentRootFolder))) {
                            return context.fromTreeUri(it.uri)
                        }
                    }
                }
        }
        return null
    }

    /**
     * To get root file access on API 30+, you need to have full storage access by
     * granting [Manifest.permission.MANAGE_EXTERNAL_STORAGE] in runtime.
     * @see SimpleStorage.requestFullStorageAccess
     * @see SimpleStorage.hasFullDiskAccess
     * @see Environment.isExternalStorageManager
     * @return `null` if you have no full storage access
     */
    @JvmOverloads
    @JvmStatic
    @Suppress("DEPRECATION")
    fun getRootRawFile(context: Context, storageId: String, requiresWriteAccess: Boolean = false): File? {
        val rootFile = when (storageId) {
            PRIMARY -> Environment.getExternalStorageDirectory()
            DATA -> context.dataDirectory
            else -> File("/storage/$storageId")
        }
        return rootFile.takeIf { rootFile.canRead() && (requiresWriteAccess && rootFile.isWritable(context) || !requiresWriteAccess) }
    }

    @JvmStatic
    fun buildAbsolutePath(context: Context, storageId: String, basePath: String): String {
        val cleanBasePath = basePath.removeForbiddenCharsFromFilename()
        val rootPath = when (storageId) {
            PRIMARY -> SimpleStorage.externalStoragePath
            DATA -> context.dataDirectory.path
            else -> "/storage/$storageId"
        }
        return "$rootPath/$cleanBasePath".trimEnd('/')
    }

    @JvmStatic
    fun buildAbsolutePath(context: Context, simplePath: String): String {
        val path = simplePath.trimEnd('/')
        return if (path.startsWith('/')) {
            path.removeForbiddenCharsFromFilename()
        } else {
            buildAbsolutePath(context, getStorageId(context, path), getBasePath(context, path))
        }
    }

    @JvmStatic
    fun buildSimplePath(storageId: String, basePath: String): String {
        val cleanBasePath = basePath.removeForbiddenCharsFromFilename().trimFileSeparator()
        return "$storageId:$cleanBasePath"
    }

    @JvmStatic
    fun buildSimplePath(context: Context, absolutePath: String): String {
        return buildSimplePath(getStorageId(context, absolutePath), getBasePath(context, absolutePath))
    }

    @JvmOverloads
    @JvmStatic
    fun createDocumentUri(storageId: String, basePath: String = ""): Uri =
        Uri.parse("content://$EXTERNAL_STORAGE_AUTHORITY/tree/" + Uri.encode("$storageId:$basePath"))

    @JvmStatic
    fun isAccessGranted(context: Context, storageId: String): Boolean {
        return storageId == DATA || storageId == PRIMARY && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || getRootDocumentFile(context, storageId, true) != null
    }

    @JvmStatic
    fun doesExist(context: Context, fullPath: String) = fromFullPath(context, fullPath)?.exists() == true

    @JvmStatic
    fun delete(context: Context, fullPath: String) = fromFullPath(context, fullPath)?.delete() == true

    /**
     * Check if storage has URI permission for read and write access.
     *
     * Persisted URIs revoked whenever the related folders deleted. Hence, you need to request URI permission again even though the folder
     * recreated by user. However, you should not worry about this on API 28 and lower, because URI permission always granted for root path
     * and rooth path itself can't be deleted.
     */
    @JvmOverloads
    @JvmStatic
    fun isStorageUriPermissionGranted(context: Context, storageId: String, basePath: String = ""): Boolean {
        val root = createDocumentUri(storageId, basePath)
        return context.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.isWritePermission && it.uri == root }
    }

    @JvmStatic
    fun isDownloadsUriPermissionGranted(context: Context): Boolean {
        val uri = Uri.parse(DOWNLOADS_TREE_URI)
        return context.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.isWritePermission && it.uri == uri }
    }

    /**
     * Get all storage IDs on this device. The first index is primary storage.
     * Prior to API 28, retrieving storage ID for SD card only applicable if URI permission is granted for read & write access.
     */
    @JvmStatic
    fun getStorageIds(context: Context): List<String> {
        val externalStoragePath = SimpleStorage.externalStoragePath
        val storageIds = ContextCompat.getExternalFilesDirs(context, null).filterNotNull().map {
            val path = it.path
            if (path.startsWith(externalStoragePath)) {
                // Path -> /storage/emulated/0/Android/data/com.anggrayudi.storage.sample/files
                PRIMARY
            } else {
                // Path -> /storage/131D-261A/Android/data/com.anggrayudi.storage.sample/files
                path.substringAfter("/storage/").substringBefore('/')
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            storageIds
        } else {
            val persistedStorageIds = context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission && it.isWritePermission && it.uri.isExternalStorageDocument }
                .mapNotNull { it.uri.path?.run { substringBefore(':').substringAfterLast('/') } }
            storageIds.toMutableList().run {
                addAll(persistedStorageIds)
                distinct()
            }
        }
    }

    @JvmStatic
    fun getSdCardIds(context: Context) = getStorageIds(context).filter { it != PRIMARY }

    /**
     * Create folders. You should do this process in background.
     *
     * @param fullPath construct it using [buildAbsolutePath] or [buildSimplePath]
     * @param requiresWriteAccess the folder should have write access, otherwise return `null`
     * @return `null` if you have no storage permission.
     */
    @JvmOverloads
    @JvmStatic
    fun mkdirs(
        context: Context,
        fullPath: String,
        requiresWriteAccess: Boolean = true,
        considerRawFile: Boolean = true
    ): DocumentFile? {
        if (considerRawFile && fullPath.startsWith('/') || fullPath.startsWith(context.dataDirectory.path)) {
            val folder = File(fullPath.removeForbiddenCharsFromFilename()).apply { mkdirs() }
            if (folder.isDirectory && folder.canRead() && (requiresWriteAccess && folder.isWritable(context) || !requiresWriteAccess)) {
                // Consider java.io.File for faster performance
                return DocumentFile.fromFile(folder)
            }
        }
        var currentDirectory = getAccessibleRootDocumentFile(context, fullPath, requiresWriteAccess, considerRawFile) ?: return null
        getDirectorySequence(getBasePath(context, fullPath)).forEach {
            try {
                val directory = currentDirectory.findFile(it)
                currentDirectory = when {
                    directory == null -> currentDirectory.createDirectory(it) ?: return null
                    directory.isDirectory && directory.canRead() -> directory
                    else -> return null
                }
            } catch (e: Exception) {
                return null
            }
        }
        return currentDirectory.takeIf { requiresWriteAccess && it.isWritable(context) || !requiresWriteAccess }
    }

    /**
     * Optimized performance for creating multiple folders. The result may contains `null` elements for unsuccessful creation.
     * For instance, if parameter `fullPaths` contains 5 elements and successful `mkdirs()` is 3, then return 3 non-null elements + 2 null elements.
     *
     * @param fullPaths either simple path or absolute path. Tips: use [buildAbsolutePath] or [buildSimplePath] to construct full path.
     * @param requiresWriteAccess the folder should have write access, otherwise return `null`
     */
    @JvmOverloads
    @JvmStatic
    fun mkdirs(
        context: Context,
        fullPaths: List<String>,
        requiresWriteAccess: Boolean = true,
        considerRawFile: Boolean = true
    ): Array<DocumentFile?> {
        val dataDir = context.dataDirectory.path
        val results = arrayOfNulls<DocumentFile>(fullPaths.size)
        val cleanedFullPaths = fullPaths.map { buildAbsolutePath(context, it) }
        for (path in findUniqueDeepestSubFolders(context, cleanedFullPaths)) {
            // use java.io.File for faster performance
            val folder = File(path).apply { mkdirs() }
            if (considerRawFile && folder.isDirectory && folder.canRead() || path.startsWith(dataDir)) {
                cleanedFullPaths.forEachIndexed { index, s ->
                    if (path.hasParent(s)) {
                        results[index] = DocumentFile.fromFile(File(getDirectorySequence(s).joinToString(prefix = "/", separator = "/")))
                    }
                }
            } else {
                var currentDirectory = getAccessibleRootDocumentFile(context, path, requiresWriteAccess, considerRawFile) ?: continue
                getDirectorySequence(getBasePath(context, path)).forEach {
                    try {
                        val directory = currentDirectory.findFile(it)
                        if (directory == null) {
                            currentDirectory = currentDirectory.createDirectory(it) ?: return@forEach
                            val fullPath = currentDirectory.getAbsolutePath(context)
                            cleanedFullPaths.forEachIndexed { index, s ->
                                if (fullPath == s) {
                                    results[index] = currentDirectory
                                }
                            }
                        } else if (directory.isDirectory && directory.canRead()) {
                            currentDirectory = directory
                            val fullPath = directory.getAbsolutePath(context)
                            cleanedFullPaths.forEachIndexed { index, s ->
                                if (fullPath == s) {
                                    results[index] = directory
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        return@forEach
                    }
                }
            }
        }
        results.indices.forEach { index ->
            results[index] = results[index]?.takeIf { requiresWriteAccess && it.isWritable(context) || !requiresWriteAccess }
        }
        return results
    }

    @JvmStatic
    fun createDownloadWithMediaStoreFallback(context: Context, file: FileDescription): Uri? {
        val publicFolder = fromPublicFolder(context, PublicDirectory.DOWNLOADS, requiresWriteAccess = true)
        return if (publicFolder == null && Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            MediaStoreCompat.createDownload(context, file)?.uri
        } else {
            publicFolder?.makeFile(context, file.name, file.mimeType)?.uri
        }
    }

    @JvmStatic
    fun createPictureWithMediaStoreFallback(context: Context, file: FileDescription): Uri? {
        val publicFolder = fromPublicFolder(context, PublicDirectory.PICTURES, requiresWriteAccess = true)
        return if (publicFolder == null && Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            MediaStoreCompat.createImage(context, file)?.uri
        } else {
            publicFolder?.makeFile(context, file.name, file.mimeType)?.uri
        }
    }

    /**
     * @return `null` if you don't have storage permission.
     * @param basePath file path without root path, e.g. `/storage/emulated/0/Music/Pop` should be written as `Music/Pop`
     */
    @JvmOverloads
    @JvmStatic
    fun createFile(
        context: Context,
        storageId: String = PRIMARY,
        basePath: String,
        mimeType: String = MimeType.UNKNOWN,
        considerRawFile: Boolean = true
    ): DocumentFile? {
        return if (storageId == DATA || considerRawFile && storageId == PRIMARY && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = File(buildAbsolutePath(context, storageId, basePath))
            file.parentFile?.mkdirs()
            if (create(file)) DocumentFile.fromFile(file) else null
        } else try {
            val directory = mkdirsParentDirectory(context, storageId, basePath, considerRawFile)
            val filename = getFileNameFromPath(basePath).removeForbiddenCharsFromFilename()
            if (filename.isEmpty()) null else directory?.makeFile(context, filename, mimeType)
        } catch (e: Exception) {
            null
        }
    }

    private fun getParentPath(path: String): String? = getDirectorySequence(path).let { it.getOrNull(it.size - 2) }

    private fun mkdirsParentDirectory(context: Context, storageId: String, basePath: String, considerRawFile: Boolean): DocumentFile? {
        val parentPath = getParentPath(basePath)
        return if (parentPath != null) {
            mkdirs(context, buildAbsolutePath(context, storageId, parentPath), considerRawFile)
        } else {
            getRootDocumentFile(context, storageId, true, considerRawFile)
        }
    }

    private fun getFileNameFromPath(path: String) = path.trimEnd('/').substringAfterLast('/')

    @JvmOverloads
    @JvmStatic
    fun recreate(
        context: Context,
        storageId: String = PRIMARY,
        basePath: String,
        mimeType: String = MimeType.UNKNOWN,
        considerRawFile: Boolean = true
    ): DocumentFile? {
        val file = File(buildAbsolutePath(context, storageId, basePath))
        file.delete()
        file.parentFile?.mkdirs()
        if ((considerRawFile || storageId == DATA) && create(file)) {
            return DocumentFile.fromFile(file)
        }

        val directory = mkdirsParentDirectory(context, storageId, basePath, considerRawFile)
        val filename = file.name
        if (filename.isNullOrEmpty()) {
            return null
        }
        return directory?.run {
            findFile(filename)?.delete()
            makeFile(context, filename, mimeType)
        }
    }

    private fun create(file: File): Boolean {
        return try {
            file.isFile && file.length() == 0L || file.createNewFile()
        } catch (e: IOException) {
            false
        }
    }

    internal fun String.removeForbiddenCharsFromFilename(): String = replace(":", "_")
        .replaceCompletely("//", "/")

    private fun exploreFile(
        context: Context,
        storageId: String,
        basePath: String,
        documentType: DocumentFileType,
        considerRawFile: Boolean
    ): DocumentFile? {
        val rawFile = File(buildAbsolutePath(context, storageId, basePath))
        if ((considerRawFile || storageId == DATA) && rawFile.canRead()) {
            return if (documentType == DocumentFileType.ANY || documentType == DocumentFileType.FILE && rawFile.isFile
                || documentType == DocumentFileType.FOLDER && rawFile.isDirectory
            ) {
                DocumentFile.fromFile(rawFile)
            } else {
                null
            }
        }
        val file = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            getRootDocumentFile(context, storageId, considerRawFile)?.child(context, basePath) ?: return null
        } else {
            val directorySequence = getDirectorySequence(basePath).toMutableList()
            val parentTree = ArrayList<String>(directorySequence.size)
            var grantedFile: DocumentFile? = null
            // Find granted file tree.
            // For example, /storage/emulated/0/Music may not granted, but /storage/emulated/0/Music/Pop is granted by user.
            while (directorySequence.isNotEmpty()) {
                parentTree.add(directorySequence.removeFirst())
                val folderTree = parentTree.joinToString(separator = "/")
                try {
                    grantedFile = context.fromTreeUri(createDocumentUri(storageId, folderTree))
                    if (grantedFile?.canRead() == true) break
                } catch (e: SecurityException) {
                    // ignore
                }
            }
            if (grantedFile == null || directorySequence.isEmpty()) {
                grantedFile
            } else {
                val fileTree = directorySequence.joinToString(prefix = "/", separator = "/")
                context.fromTreeUri(Uri.parse(grantedFile.uri.toString() + Uri.encode(fileTree)))
            }
        }
        return file?.takeIf {
            it.canRead() && (documentType == DocumentFileType.ANY
                    || documentType == DocumentFileType.FILE && it.isFile || documentType == DocumentFileType.FOLDER && it.isDirectory)
        }
    }

    /**
     * For example, `Downloads/Video/Sports/` will become array `["Downloads", "Video", "Sports"]`
     */
    internal fun getDirectorySequence(path: String) = path.split('/')
        .filterNot { it.isBlank() }

    /**
     * Given the following `folderFullPaths`:
     * * `/storage/9016-4EF8/Downloads`
     * * `/storage/9016-4EF8/Downloads/Archive`
     * * `/storage/9016-4EF8/Video`
     * * `/storage/9016-4EF8/Music`
     * * `/storage/9016-4EF8/Music/Favorites/Pop`
     * * `/storage/emulated/0/Music`
     * * `primary:Alarm/Morning`
     * * `primary:Alarm`
     *
     * Then return:
     * * `/storage/9016-4EF8/Downloads/Archive`
     * * `/storage/9016-4EF8/Music/Favorites/Pop`
     * * `/storage/9016-4EF8/Video`
     * * `/storage/emulated/0/Music`
     * * `/storage/emulated/0/Alarm/Morning`
     */
    @JvmStatic
    fun findUniqueDeepestSubFolders(context: Context, folderFullPaths: Collection<String>): List<String> {
        val paths = folderFullPaths.map { buildAbsolutePath(context, it) }.distinct()
        val results = ArrayList(paths)
        paths.forEach { path ->
            paths.find { it != path && path.hasParent(it) }?.let { results.remove(it) }
        }
        return results
    }

    /**
     * Given the following `folderFullPaths`:
     * * `/storage/9016-4EF8/Downloads`
     * * `/storage/9016-4EF8/Downloads/Archive`
     * * `/storage/9016-4EF8/Video`
     * * `/storage/9016-4EF8/Music`
     * * `/storage/9016-4EF8/Music/Favorites/Pop`
     * * `/storage/emulated/0/Music`
     * * `primary:Alarm/Morning`
     * * `primary:Alarm`
     *
     * Then return:
     * * `/storage/9016-4EF8/Downloads`
     * * `/storage/9016-4EF8/Music`
     * * `/storage/9016-4EF8/Video`
     * * `/storage/emulated/0/Music`
     * * `/storage/emulated/0/Alarm`
     */
    @JvmStatic
    fun findUniqueParents(context: Context, folderFullPaths: Collection<String>): List<String> {
        val paths = folderFullPaths.map { buildAbsolutePath(context, it) }.distinct()
        val results = ArrayList<String>(paths.size)
        paths.forEach { path ->
            if (!paths.any { it != path && path.hasParent(it) }) {
                results.add(path)
            }
        }
        return results
    }

    @JvmStatic
    @WorkerThread
    fun findInaccessibleStorageLocations(context: Context, fullPaths: List<String>): List<String> {
        return if (SimpleStorage.hasStoragePermission(context)) {
            val uniqueParents = findUniqueParents(context, fullPaths)
            val inaccessibleStorageLocations = ArrayList<String>(uniqueParents.size)
            // if folder not found, try create it and check whether is successful
            mkdirs(context, uniqueParents).forEachIndexed { index, folder ->
                if (folder == null) {
                    inaccessibleStorageLocations.add(uniqueParents[index])
                }
            }
            inaccessibleStorageLocations
        } else {
            fullPaths.map { buildAbsolutePath(context, it) }
        }
    }

    @JvmStatic
    fun getFreeSpace(context: Context, storageId: String): Long {
        return try {
            val file = getDocumentFileForStorageInfo(context, storageId) ?: return 0
            when {
                file.isRawFile -> StatFs(file.uri.path!!).availableBytes
                Build.VERSION.SDK_INT >= 21 -> {
                    context.contentResolver.openFileDescriptor(file.uri, "r")?.use {
                        val stats = Os.fstatvfs(it.fileDescriptor)
                        stats.f_bavail * stats.f_frsize
                    } ?: 0
                }
                else -> 0
            }
        } catch (e: Throwable) {
            0
        }
    }

    @JvmStatic
    fun getUsedSpace(context: Context, storageId: String): Long {
        return try {
            val file = getDocumentFileForStorageInfo(context, storageId) ?: return 0
            when {
                file.isRawFile -> StatFs(file.uri.path!!).run { totalBytes - availableBytes }
                Build.VERSION.SDK_INT >= 21 -> {
                    context.contentResolver.openFileDescriptor(file.uri, "r")?.use {
                        val stats = Os.fstatvfs(it.fileDescriptor)
                        stats.f_blocks * stats.f_frsize - stats.f_bavail * stats.f_frsize
                    } ?: 0
                }
                else -> 0
            }
        } catch (e: Throwable) {
            0
        }
    }

    @JvmStatic
    fun getStorageCapacity(context: Context, storageId: String): Long {
        return try {
            val file = getDocumentFileForStorageInfo(context, storageId) ?: return 0
            when {
                file.isRawFile -> StatFs(file.uri.path!!).totalBytes
                Build.VERSION.SDK_INT >= 21 -> {
                    context.contentResolver.openFileDescriptor(file.uri, "r")?.use {
                        val stats = Os.fstatvfs(it.fileDescriptor)
                        stats.f_blocks * stats.f_frsize
                    } ?: 0
                }
                else -> 0
            }
        } catch (e: Throwable) {
            0
        }
    }

    private fun getDocumentFileForStorageInfo(context: Context, storageId: String): DocumentFile? {
        return when (storageId) {
            PRIMARY -> {
                // use app private directory, so no permissions required
                val directory = context.getExternalFilesDir(null) ?: return null
                DocumentFile.fromFile(directory)
            }

            DATA -> DocumentFile.fromFile(context.dataDirectory)

            else -> {
                // /storage/131D-261A/Android/data/com.anggrayudi.storage.sample/files
                val folder = File("/storage/$storageId/Android/data/${context.packageName}/files")
                folder.mkdirs()
                if (folder.canRead()) {
                    DocumentFile.fromFile(folder)
                } else {
                    getAccessibleRootDocumentFile(context, folder.absolutePath, considerRawFile = false)
                }
            }
        }
    }

    @JvmStatic
    fun getFileNameFromUrl(url: String): String {
        return try {
            URLDecoder.decode(url, "UTF-8").substringAfterLast('/')
        } catch (e: Exception) {
            url
        }
    }
}