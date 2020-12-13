package com.anggrayudi.storage.file

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.callback.FileCopyCallback
import com.anggrayudi.storage.callback.FileMoveCallback
import com.anggrayudi.storage.extension.closeStream
import com.anggrayudi.storage.extension.fromTreeUri
import com.anggrayudi.storage.extension.startCoroutineTimer
import com.anggrayudi.storage.file.DocumentFileCompat.removeForbiddenCharsFromFilename
import kotlinx.coroutines.Job
import java.io.*

/**
 * Created on 16/08/20
 * @author Anggrayudi H
 */

/**
 * ID of this storage. For external storage, it will return [DocumentFileCompat.PRIMARY],
 * otherwise it is a SD Card and will return integers like `6881-2249`.
 * However, it will return empty `String` if this [DocumentFile] is picked from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT]
 */
val DocumentFile.storageId: String
    get() = DocumentFileCompat.getStorageId(uri)

val DocumentFile.isExternalStorageDocument: Boolean
    get() = uri.authority == DocumentFileCompat.EXTERNAL_STORAGE_AUTHORITY

val DocumentFile.isDownloadsDocument: Boolean
    get() = uri.authority == DocumentFileCompat.DOWNLOADS_FOLDER_AUTHORITY

val DocumentFile.isMediaDocument: Boolean
    get() = uri.authority == DocumentFileCompat.MEDIA_FOLDER_AUTHORITY

val DocumentFile.isReadOnly: Boolean
    get() = canRead() && !canWrite()

val DocumentFile.id: String
    get() = DocumentsContract.getDocumentId(uri)

val DocumentFile.rootId: String
    get() = DocumentsContract.getRootId(uri)

/**
 * Returns `null` if this [DocumentFile] is picked from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT]
 */
val DocumentFile.storageType: StorageType?
    get() = if (isExternalStorageDocument) {
        if (inPrimaryStorage) StorageType.EXTERNAL else StorageType.SD_CARD
    } else if (inSdCardStorage) StorageType.SD_CARD else null

/**
 * `true` if this file located in primary storage, i.e. external storage.
 * All files created by [DocumentFile.fromFile] are always treated from external storage.
 */
val DocumentFile.inPrimaryStorage: Boolean
    get() = isExternalStorageDocument && storageId == DocumentFileCompat.PRIMARY
            || isRawFile && uri.path.orEmpty().startsWith(SimpleStorage.externalStoragePath)

/**
 * `true` if this file located in SD Card
 */
val DocumentFile.inSdCardStorage: Boolean
    get() = isExternalStorageDocument && storageId != DocumentFileCompat.PRIMARY
            || isRawFile && uri.path.orEmpty().startsWith("/storage/$storageId")

/**
 * `true` if this file was created with [File]
 */
val DocumentFile.isRawFile: Boolean
    get() = uri.scheme == ContentResolver.SCHEME_FILE

/**
 * Filename without extension
 */
val DocumentFile.baseName: String
    get() = name.orEmpty().substringBeforeLast('.')

/**
 * File extension
 */
val DocumentFile.extension: String
    get() = name.orEmpty().substringAfterLast('.', "")

/**
 * Please notice that accessing files with [File] only works on app private directory since Android 10. You had better to stay using [DocumentFile].
 *
 * @return `null` if you try to read files from SD Card or you want to convert a file picked
 * from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT].
 * @see toDocumentFile
 */
fun DocumentFile.toRawFile(): File? {
    return when {
        isRawFile -> File(uri.path!!)
        inPrimaryStorage -> File("${SimpleStorage.externalStoragePath}/$basePath")
        storageId.isNotEmpty() -> File("/storage/$storageId/$basePath")
        else -> null
    }
}

fun DocumentFile.toRawDocumentFile(): DocumentFile? {
    return if (isRawFile) this else DocumentFile.fromFile(toRawFile() ?: return null)
}

fun DocumentFile.toTreeDocumentFile(context: Context): DocumentFile? {
    return if (isRawFile) {
        DocumentFileCompat.fromFile(context, toRawFile() ?: return null, considerRawFile = false)
    } else {
        this
    }
}

/**
 * @return File path without storage ID, otherwise return empty `String` if this is the root path or if this [DocumentFile] is picked
 * from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT]
 */
val DocumentFile.basePath: String
    get() = when {
        isRawFile -> File(uri.path!!).basePath
        isExternalStorageDocument -> uri.path.orEmpty().substringAfterLast("/document/$storageId:", "")
        else -> ""
    }

/**
 * Root path of this file.
 * * For file picked from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT], it will return empty `String`
 * * For file stored in external or primary storage, it will return [SimpleStorage.externalStoragePath].
 * * For file stored in SD Card, it will return something like `/storage/6881-2249`
 */
val DocumentFile.rootPath: String
    get() = when {
        isRawFile -> File(uri.path!!).rootPath
        !isExternalStorageDocument -> ""
        inSdCardStorage -> "/storage/$storageId"
        else -> SimpleStorage.externalStoragePath
    }

/**
 * * For file in SD Card => `/storage/6881-2249/Music/song.mp3`
 * * For file in external storage => `/storage/emulated/0/Music/song.mp3`
 *
 * If you want to remember file locations in database or preference, please use this function.
 * When you reopen the file, just call [DocumentFileCompat.fromFullPath]
 *
 * @see File.getAbsolutePath
 * @see simplePath
 */
val DocumentFile.absolutePath: String
    get() = when {
        isRawFile -> uri.path.orEmpty()
        !isExternalStorageDocument -> ""
        inPrimaryStorage -> "${SimpleStorage.externalStoragePath}/$basePath"
        else -> "/storage/$storageId/$basePath"
    }

/**
 * @see absolutePath
 */
val DocumentFile.simplePath: String
    get() = "$storageId:$basePath"

/**
 * Delete this file and create new empty file using previous `filename` and `mimeType`.
 * It cannot be applied if current [DocumentFile] is a directory.
 */
fun DocumentFile.recreateFile(): DocumentFile? {
    return if (isFile && (isRawFile || isExternalStorageDocument)) {
        val filename = name.orEmpty()
        val mimeType = type ?: DocumentFileCompat.MIME_TYPE_UNKNOWN
        val parentFile = parentFile
        if (parentFile?.canWrite() == true) {
            delete()
            parentFile.makeFile(mimeType, filename)
        } else null
    } else null
}

@JvmOverloads
fun DocumentFile.getRootDocumentFile(context: Context, requiresWriteAccess: Boolean = false) = when {
    isExternalStorageDocument -> DocumentFileCompat.getRootDocumentFile(context, storageId, requiresWriteAccess)
    isRawFile -> File(uri.path!!).getRootRawFile(requiresWriteAccess)?.let { DocumentFile.fromFile(it) }
    else -> null
}

/**
 * @return `true` if this file exists and writeable. [DocumentFile.canWrite] may return false if you have no URI permission for read & write access.
 */
val DocumentFile.canModify: Boolean
    get() = canRead() && canWrite()

fun DocumentFile.isRootUriPermissionGranted(context: Context): Boolean {
    return isExternalStorageDocument && DocumentFileCompat.isStorageUriPermissionGranted(context, storageId)
}

fun DocumentFile.doesExist(filename: String) = findFile(filename)?.exists() == true

fun DocumentFile.avoidDuplicateFileNameFor(filename: String): String {
    return if (findFile(filename)?.isFile == true) {
        val baseName = filename.substringBeforeLast('.')
        val ext = filename.substringAfterLast('.')
        val prefix = "$baseName ("
        val lastFile = listFiles().filter {
            it.isFile && it.name.orEmpty().let { name ->
                name.startsWith(prefix) && (DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITH_EXTENSION.matches(name)
                        || DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(name))
            }
        }.maxOfOrNull { it.name.orEmpty() }
        var count = lastFile.orEmpty().substringAfterLast('(').substringBefore(')').toIntOrNull() ?: 0
        "$baseName (${++count}).$ext"
    } else {
        filename
    }
}

fun DocumentFile.avoidDuplicateFolderNameFor(folderName: String): String {
    return if (findFile(folderName)?.isDirectory == true) {
        val prefix = "$folderName ("
        val lastFolder = listFiles().filter {
            it.isDirectory && it.name.orEmpty().let { name ->
                name.startsWith(prefix) && DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(name)
            }
        }.maxOfOrNull { it.name.orEmpty() }
        var count = lastFolder.orEmpty().substringAfterLast('(').substringBefore(')').toIntOrNull() ?: 0
        "$folderName (${++count})"
    } else {
        folderName
    }
}

/**
 * Useful for creating temporary files. The extension is `*.bin`
 */
@WorkerThread
fun DocumentFile.createBinaryFile(name: String) = makeFile(name, DocumentFileCompat.MIME_TYPE_BINARY_FILE)

/**
 * Similar to [DocumentFile.createFile], but adds compatibility on API 28 and lower.
 * Creating files in API 28- with `createFile("my video.mp4", "video/mp4")` will create `my video.mp4`,
 * whereas API 29+ will create `my video.mp4.mp4`. This function helps you to fix this kind of bug.
 */
@JvmOverloads
@WorkerThread
fun DocumentFile.makeFile(
    name: String,
    mimeType: String = DocumentFileCompat.MIME_TYPE_UNKNOWN
): DocumentFile? {
    if (!isDirectory || !canWrite()) {
        return null
    }

    val extension = if (mimeType == DocumentFileCompat.MIME_TYPE_UNKNOWN) {
        ""
    } else {
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType).orEmpty()
    }
    val baseFileName = name.removeForbiddenCharsFromFilename().removeSuffix(".$extension")

    return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P || isRawFile) {
        createFile(mimeType, baseFileName)
    } else {
        val fullFileName = if (extension.isEmpty()) baseFileName else "$baseFileName.$extension"
        createFile(mimeType, fullFileName)
    }
}

fun DocumentFile.findFiles(names: Array<String>, documentType: DocumentFileType = DocumentFileType.ANY): List<DocumentFile> {
    val files = listFiles().filter { it.name in names }
    return when (documentType) {
        DocumentFileType.FILE -> files.filter { it.isFile }
        DocumentFileType.FOLDER -> files.filter { it.isDirectory }
        else -> files
    }
}

fun DocumentFile.findFolder(name: String): DocumentFile? = listFiles().find { it.name == name && it.isDirectory }

/**
 * Expect the file is a file literally, not a folder.
 */
fun DocumentFile.findFileLiterally(name: String): DocumentFile? = listFiles().find { it.name == name && it.isFile }

/**
 * @param recursive walk into sub folders
 */
@JvmOverloads
@WorkerThread
fun DocumentFile.search(
    recursive: Boolean = false,
    documentType: DocumentFileType = DocumentFileType.ANY,
    mimeType: String = DocumentFileCompat.MIME_TYPE_UNKNOWN,
    name: String = "",
    regex: Regex? = null
): List<DocumentFile> {
    return when {
        !isDirectory -> emptyList()
        recursive -> walkFileTree(documentType, mimeType, name, regex)
        else -> {
            var sequence = listFiles().asSequence().filter { it.canRead() }
            if (name.isNotEmpty()) {
                sequence = sequence.filter { it.name == name }
            }
            if (regex != null) {
                sequence = sequence.filter { regex.matches(it.name.orEmpty()) }
            }
            if (mimeType != DocumentFileCompat.MIME_TYPE_UNKNOWN) {
                sequence = sequence.filter { it.type == mimeType }
            }
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (documentType) {
                DocumentFileType.FILE -> sequence = sequence.filter { it.isFile }
                DocumentFileType.FOLDER -> sequence = sequence.filter { it.isDirectory }
            }
            sequence.toList()
        }
    }
}

private fun DocumentFile.walkFileTree(
    documentType: DocumentFileType,
    mimeType: String,
    nameFilter: String,
    regex: Regex?
): List<DocumentFile> {
    val fileTree = mutableListOf<DocumentFile>()
    for (file in listFiles()) {
        if (!canRead()) continue

        if (file.isFile) {
            if (documentType == DocumentFileType.FOLDER) {
                continue
            }
            val filename = file.name.orEmpty()
            if ((nameFilter.isEmpty() || filename == nameFilter)
                && (regex == null || regex.matches(filename))
                && (mimeType == DocumentFileCompat.MIME_TYPE_UNKNOWN || file.type == mimeType)
            ) {
                fileTree.add(file)
            }
        } else {
            if (documentType != DocumentFileType.FILE) {
                val folderName = file.name.orEmpty()
                if ((nameFilter.isEmpty() || folderName == nameFilter) && (regex == null || regex.matches(folderName))) {
                    fileTree.add(file)
                }
            }
            fileTree.addAll(file.walkFileTree(documentType, mimeType, nameFilter, regex))
        }
    }
    return fileTree
}

/**
 * @param append if `false` and the file already exists, it will recreate the file.
 */
@JvmOverloads
@WorkerThread
fun DocumentFile.openOutputStream(context: Context, append: Boolean = true): OutputStream? {
    return try {
        if (isRawFile) {
            FileOutputStream(toRawFile(), append)
        } else {
            context.contentResolver.openOutputStream(uri, if (append && isExternalStorageDocument) "wa" else "w")
        }
    } catch (e: FileNotFoundException) {
        null
    }
}

@WorkerThread
fun DocumentFile.openInputStream(context: Context): InputStream? {
    return try {
        if (isRawFile) {
            // handle file from external storage
            FileInputStream(toRawFile())
        } else {
            context.contentResolver.openInputStream(uri)
        }
    } catch (e: FileNotFoundException) {
        null
    }
}

@UiThread
fun DocumentFile.openFileIntent(context: Context, authority: String) = Intent(Intent.ACTION_VIEW)
    .setData(if (isRawFile) FileProvider.getUriForFile(context, authority, File(uri.path!!)) else uri)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

// TODO: 08/09/20 moveTo and copyTo for folder and subfolders

@WorkerThread
fun DocumentFile?.copyTo(
    context: Context,
    targetFolderFullPath: String,
    newFilenameInTargetPath: String? = null,
    callback: FileCopyCallback? = null
) {
    copyTo(context, File(targetFolderFullPath), newFilenameInTargetPath, callback)
}

@WorkerThread
fun DocumentFile?.copyTo(context: Context, targetFolder: File, newFilenameInTargetPath: String? = null, callback: FileCopyCallback? = null) {
    copyTo(context, targetFolder.storageId, targetFolder.basePath, newFilenameInTargetPath, callback)
}

@WorkerThread
fun DocumentFile?.copyTo(context: Context, targetFolder: DocumentFile?, newFilenameInTargetPath: String? = null, callback: FileCopyCallback? = null) {
    when {
        targetFolder == null -> callback?.onFailed(ErrorCode.TARGET_FOLDER_NOT_FOUND)
        targetFolder.isDownloadsDocument -> copyTo(
            context,
            DocumentFileCompat.PRIMARY,
            Environment.DIRECTORY_DOWNLOADS,
            newFilenameInTargetPath,
            callback
        )
        else -> copyTo(context, targetFolder.storageId, targetFolder.basePath, newFilenameInTargetPath, callback)
    }
}

@WorkerThread
fun DocumentFile?.copyTo(
    context: Context,
    targetStorageId: String,
    targetFolderBasePath: String,
    newFilenameInTargetPath: String? = null,
    callback: FileCopyCallback? = null
) {
    if (targetStorageId.isEmpty()) {
        callback?.onFailed(ErrorCode.TARGET_FOLDER_NOT_FOUND)
        return
    }

    if (this == null || !isFile) {
        callback?.onFailed(ErrorCode.SOURCE_FILE_NOT_FOUND)
        return
    }

    if (targetStorageId == storageId && targetFolderBasePath == parentFile?.basePath) {
        callback?.onFailed(ErrorCode.TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER)
        return
    }

    if (!isFile) {
        callback?.onFailed(ErrorCode.SOURCE_FILE_NOT_FOUND)
        return
    }

    try {
        if (callback?.onCheckFreeSpace(DocumentFileCompat.getFreeSpace(context, targetStorageId), length()) == false) {
            callback.onFailed(ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH)
            return
        }
    } catch (e: Throwable) {
        callback?.onFailed(ErrorCode.STORAGE_PERMISSION_DENIED)
        return
    }

    val reportInterval = callback?.onStartCopying(this) ?: 0
    if (reportInterval < 0) return
    val watchProgress = reportInterval > 0
    try {
        val targetFile = createTargetFile(context, targetStorageId, targetFolderBasePath, newFilenameInTargetPath, callback) ?: return
        createFileStreams(context, this, targetFile, callback) { inputStream, outputStream ->
            copyFileStream(inputStream, outputStream, targetFile, watchProgress, reportInterval, callback)
        }
    } catch (e: SecurityException) {
        callback?.onFailed(ErrorCode.STORAGE_PERMISSION_DENIED)
    } catch (e: InterruptedIOException) {
        callback?.onFailed(ErrorCode.CANCELLED)
    } catch (e: InterruptedException) {
        callback?.onFailed(ErrorCode.CANCELLED)
    } catch (e: IOException) {
        callback?.onFailed(ErrorCode.UNKNOWN_IO_ERROR)
    }
}

private inline fun createFileStreams(
    context: Context,
    sourceFile: DocumentFile,
    targetFile: DocumentFile,
    callback: FileCallback?,
    onStreamsReady: (InputStream, OutputStream) -> Unit
) {
    val outputStream = targetFile.openOutputStream(context)
    if (outputStream == null) {
        callback?.onFailed(ErrorCode.TARGET_FILE_NOT_FOUND)
        return
    }

    val inputStream = sourceFile.openInputStream(context)
    if (inputStream == null) {
        callback?.onFailed(ErrorCode.SOURCE_FILE_NOT_FOUND)
        outputStream.closeStream()
        return
    }

    onStreamsReady(inputStream, outputStream)
}

private fun DocumentFile.createTargetFile(
    context: Context,
    targetStorageId: String,
    targetFolderBasePath: String,
    newFilenameInTargetPath: String?,
    callback: FileCallback?
): DocumentFile? {
    try {
        val targetFolder = DocumentFileCompat.mkdirs(context, DocumentFileCompat.buildAbsolutePath(targetStorageId, targetFolderBasePath))
        if (targetFolder == null) {
            callback?.onFailed(ErrorCode.STORAGE_PERMISSION_DENIED)
            return null
        }

        var targetFile = targetFolder.findFile(name.orEmpty())
        if (targetFile?.exists() == true) {
            callback?.onFailed(ErrorCode.TARGET_FILE_EXISTS)
            return null
        }

        val mimeType = if (newFilenameInTargetPath != null && extension != newFilenameInTargetPath.substringAfterLast('.')) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(newFilenameInTargetPath.substringAfterLast('.'))
        } else {
            type
        }

        targetFile = targetFolder.makeFile(mimeType ?: DocumentFileCompat.MIME_TYPE_UNKNOWN, newFilenameInTargetPath ?: name.orEmpty())
        if (targetFile == null) {
            callback?.onFailed(ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
        } else {
            return targetFile
        }
    } catch (e: SecurityException) {
        callback?.onFailed(ErrorCode.STORAGE_PERMISSION_DENIED)
    } catch (e: InterruptedIOException) {
        callback?.onFailed(ErrorCode.CANCELLED)
    } catch (e: InterruptedException) {
        callback?.onFailed(ErrorCode.CANCELLED)
    } catch (e: IOException) {
        callback?.onFailed(ErrorCode.UNKNOWN_IO_ERROR)
    }
    return null
}

private fun DocumentFile.copyFileStream(
    inputStream: InputStream,
    outputStream: OutputStream,
    targetFile: DocumentFile,
    watchProgress: Boolean,
    reportInterval: Long,
    callback: FileCallback?
) {
    var timer: Job? = null
    try {
        var byteMoved = 0L
        var writeSpeed = 0
        val srcSize = length()
        // using timer on small file is useless. We set minimum 10MB.
        if (watchProgress && callback != null && srcSize > 10 * FileSize.MB) {
            timer = startCoroutineTimer(repeatMillis = reportInterval) {
                callback.onReport(byteMoved * 100f / srcSize, byteMoved, writeSpeed)
                writeSpeed = 0
            }
        }
        val buffer = ByteArray(1024)
        var read = inputStream.read(buffer)
        while (read != -1) {
            outputStream.write(buffer, 0, read)
            byteMoved += read
            writeSpeed += read
            read = inputStream.read(buffer)
        }
        timer?.cancel()
        if (callback is FileCopyCallback && callback.onCompleted(targetFile)) {
            delete()
        } else if (callback is FileMoveCallback) {
            delete()
            callback.onCompleted(targetFile)
        }
    } finally {
        timer?.cancel()
        inputStream.closeStream()
        outputStream.closeStream()
    }
}

@WorkerThread
fun DocumentFile?.moveTo(
    context: Context,
    targetFolderFullPath: String,
    newFilenameInTargetPath: String? = null,
    callback: FileMoveCallback? = null
) {
    moveTo(context, File(targetFolderFullPath), newFilenameInTargetPath, callback)
}

@WorkerThread
fun DocumentFile?.moveTo(context: Context, targetFolder: File, newFilenameInTargetPath: String? = null, callback: FileMoveCallback? = null) {
    moveTo(context, targetFolder.storageId, targetFolder.basePath, newFilenameInTargetPath, callback)
}

@WorkerThread
fun DocumentFile?.moveTo(context: Context, targetFolder: DocumentFile?, newFilenameInTargetPath: String? = null, callback: FileMoveCallback? = null) {
    when {
        targetFolder == null -> callback?.onFailed(ErrorCode.TARGET_FOLDER_NOT_FOUND)
        targetFolder.isDownloadsDocument -> moveTo(
            context,
            DocumentFileCompat.PRIMARY,
            Environment.DIRECTORY_DOWNLOADS,
            newFilenameInTargetPath,
            callback
        )
        else -> moveTo(context, targetFolder.storageId, targetFolder.basePath, newFilenameInTargetPath, callback)
    }
}

/**
 * @param newFilenameInTargetPath change filename in target path
 */
@WorkerThread
fun DocumentFile?.moveTo(
    context: Context,
    targetStorageId: String,
    targetFolderBasePath: String,
    newFilenameInTargetPath: String? = null,
    callback: FileMoveCallback? = null
) {
    if (targetStorageId.isEmpty()) {
        callback?.onFailed(ErrorCode.TARGET_FOLDER_NOT_FOUND)
        return
    }

    if (this == null || !isFile) {
        callback?.onFailed(ErrorCode.SOURCE_FILE_NOT_FOUND)
        return
    }

    if (targetStorageId == storageId && targetFolderBasePath == parentFile?.basePath) {
        callback?.onFailed(ErrorCode.TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER)
        return
    }

    if (inPrimaryStorage && targetStorageId == DocumentFileCompat.PRIMARY) {
        val sourcePath = uri.path!!.substringAfterLast("/document/$storageId:", "")
        val externalStoragePath = SimpleStorage.externalStoragePath
        val sourceFile = File("$externalStoragePath/$sourcePath")
        val targetFile = File("$externalStoragePath/$targetFolderBasePath", newFilenameInTargetPath ?: name.orEmpty())
        targetFile.parentFile?.mkdirs()
        if (sourceFile.renameTo(targetFile)) {
            callback?.onCompleted(DocumentFile.fromFile(targetFile))
            return
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() && storageId == targetStorageId) {
        val sourceFile = toRawFile()!!
        val targetFolder = File(DocumentFileCompat.getRootRawFile(targetStorageId)!!.path + "/$targetFolderBasePath")
        val targetFile = File(targetFolder, newFilenameInTargetPath ?: name.orEmpty())
        targetFolder.mkdirs()
        if (sourceFile.renameTo(targetFile)) {
            callback?.onCompleted(DocumentFile.fromFile(targetFile))
            return
        }
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && storageId == targetStorageId) {
            val targetFolder = DocumentFileCompat.fromSimplePath(context, targetStorageId, targetFolderBasePath)
            if (targetFolder == null) {
                callback?.onFailed(ErrorCode.STORAGE_PERMISSION_DENIED)
                return
            }
            if (newFilenameInTargetPath != null && targetFolder.doesExist(newFilenameInTargetPath)) {
                callback?.onFailed(ErrorCode.TARGET_FILE_EXISTS)
                return
            }
            val movedFileUri = DocumentsContract.moveDocument(context.contentResolver, uri, parentFile!!.uri, targetFolder.uri)
            if (movedFileUri != null) {
                val newFile = context.fromTreeUri(movedFileUri)
                if (newFile != null && newFile.isFile) {
                    if (newFilenameInTargetPath != null) newFile.renameTo(newFilenameInTargetPath)
                    callback?.onCompleted(newFile)
                } else {
                    callback?.onFailed(ErrorCode.TARGET_FILE_NOT_FOUND)
                }
                return
            }
        }

        if (callback?.onCheckFreeSpace(DocumentFileCompat.getFreeSpace(context, targetStorageId), length()) == false) {
            callback.onFailed(ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH)
            return
        }
    } catch (e: Throwable) {
        callback?.onFailed(ErrorCode.STORAGE_PERMISSION_DENIED)
        return
    }

    val reportInterval = callback?.onStartMoving(this) ?: 0
    if (reportInterval < 0) return
    val watchProgress = reportInterval > 0

    try {
        val targetFile = createTargetFile(context, targetStorageId, targetFolderBasePath, newFilenameInTargetPath, callback) ?: return
        createFileStreams(context, this, targetFile, callback) { inputStream, outputStream ->
            copyFileStream(inputStream, outputStream, targetFile, watchProgress, reportInterval, callback)
        }
    } catch (e: SecurityException) {
        callback?.onFailed(ErrorCode.STORAGE_PERMISSION_DENIED)
    } catch (e: InterruptedIOException) {
        callback?.onFailed(ErrorCode.CANCELLED)
    } catch (e: InterruptedException) {
        callback?.onFailed(ErrorCode.CANCELLED)
    } catch (e: IOException) {
        callback?.onFailed(ErrorCode.UNKNOWN_IO_ERROR)
    }
}