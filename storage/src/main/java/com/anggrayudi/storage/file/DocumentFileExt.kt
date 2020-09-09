package com.anggrayudi.storage.file

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.callback.FileCopyCallback
import com.anggrayudi.storage.callback.FileMoveCallback
import com.anggrayudi.storage.extension.closeStream
import com.anggrayudi.storage.extension.startCoroutineTimer
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
    } else null

/**
 * `true` if this file located in primary storage, i.e. external storage.
 * All files created by [DocumentFile.fromFile] are always treated from external storage.
 */
val DocumentFile.inPrimaryStorage: Boolean
    get() = isExternalStorageDocument && storageId == DocumentFileCompat.PRIMARY || isJavaFile

/**
 * `true` if this file located in SD Card
 */
val DocumentFile.inSdCardStorage: Boolean
    get() = isExternalStorageDocument && storageId != DocumentFileCompat.PRIMARY && !isJavaFile

/**
 * `true` if this file was created with [File]
 */
val DocumentFile.isJavaFile: Boolean
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
fun DocumentFile.toJavaFile(): File? {
    return when {
        isJavaFile -> File(uri.path!!)
        inPrimaryStorage -> File("${SimpleStorage.externalStoragePath}/$filePath")
        else -> null
    }
}

fun File.toDocumentFile(context: Context) = DocumentFileCompat.fromFile(context, this)

/**
 * @return File path without storage ID, otherwise return empty `String` if this is the root path or if this [DocumentFile] is picked
 * from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT]
 */
val DocumentFile.filePath: String
    get() = when {
        isJavaFile -> uri.path.orEmpty().replaceFirst(SimpleStorage.externalStoragePath, "").run {
            if (startsWith("/")) replaceFirst("/", "") else this
        }
        !isExternalStorageDocument -> ""
        else -> uri.path.orEmpty().substringAfterLast("/document/$storageId:", "")
    }

/**
 * Root path of this file.
 * * For file picked from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT], it will return empty `String`
 * * For file stored in external or primary storage, it will return [SimpleStorage.externalStoragePath].
 * * For file stored in SD Card, it will return integers like `6881-2249:`
 */
val DocumentFile.rootPath: String
    get() = when {
        !isExternalStorageDocument -> ""
        inSdCardStorage -> "$storageId:"
        else -> SimpleStorage.externalStoragePath
    }

/**
 * * For file in SD Card: `6881-2249:Music/song.mp3`
 * * For file in external storage: `/storage/emulated/0/Music/song.mp3`
 * @see DocumentFileCompat.fromFullPath
 */
val DocumentFile.fullPath: String
    get() = when {
        !isExternalStorageDocument -> ""
        isJavaFile -> uri.path.orEmpty()
        inPrimaryStorage -> "${SimpleStorage.externalStoragePath}/$filePath"
        else -> "$storageId:$filePath"
    }

/**
 * Delete this file and create new empty file using previous `filename` and `mimeType`.
 * It cannot be applied if current [DocumentFile] is a directory.
 */
fun DocumentFile.recreateFile(): DocumentFile? {
    return if (!isExternalStorageDocument || isDirectory) {
        null
    } else {
        val filename = name.orEmpty()
        val mimeType = type ?: DocumentFileCompat.MIME_TYPE_UNKNOWN
        val parentFile = parentFile
        delete()
        parentFile?.createFile(mimeType, filename)
    }
}

fun DocumentFile.getRootDocumentFile(context: Context) =
    if (isExternalStorageDocument) DocumentFileCompat.getRootDocumentFile(context, storageId) else null

/**
 * @return `true` if this file has read and write access, or if this file has URI permission for read and write access.
 */
val DocumentFile.isModifiable: Boolean
    get() = canRead() && canWrite()

fun DocumentFile.isRootUriPermissionGranted(context: Context): Boolean {
    return isExternalStorageDocument && DocumentFileCompat.isStorageUriPermissionGranted(context, storageId)
}

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
fun DocumentFile.createBinaryFile(
    name: String,
    appendBinFileExtension: Boolean = true,
    mimeType: String = DocumentFileCompat.MIME_TYPE_BINARY_FILE
): DocumentFile? {
    return when {
        !isExternalStorageDocument || isFile -> null
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
            val filename = if (appendBinFileExtension) {
                if (name.endsWith(".bin")) name else "$name.bin"
            } else {
                name
            }
            createFile(mimeType, filename)
        }
        else -> {
            val filename = name.removeSuffix(".bin")
            createFile(mimeType, filename)?.apply {
                if (!appendBinFileExtension) {
                    renameTo(name)
                }
            }
        }
    }
}

fun DocumentFile.findFiles(names: Array<String>, documentType: DocumentFileType = DocumentFileType.ALL): List<DocumentFile> {
    val files = listFiles().filter { it.name in names }
    return when (documentType) {
        DocumentFileType.FILE -> files.filter { it.isFile }
        DocumentFileType.FOLDER -> files.filter { it.isDirectory }
        else -> files
    }
}

/**
 * @param recursive walk into sub folders
 */
@WorkerThread
fun DocumentFile.search(
    recursive: Boolean = false,
    documentType: DocumentFileType = DocumentFileType.ALL,
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
@WorkerThread
fun DocumentFile.openOutputStream(context: Context, append: Boolean = true): OutputStream? {
    return try {
        if (isJavaFile) {
            FileOutputStream(toJavaFile(), append)
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
        if (isJavaFile) {
            // handle file from external storage
            FileInputStream(toJavaFile())
        } else {
            context.contentResolver.openInputStream(uri)
        }
    } catch (e: FileNotFoundException) {
        null
    }
}

@UiThread
fun DocumentFile.openFileIntent(context: Context, authority: String) = Intent(Intent.ACTION_VIEW)
    .setData(if (isJavaFile) FileProvider.getUriForFile(context, authority, File(uri.path!!)) else uri)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

// TODO: 08/09/20 moveTo and copyTo for folder and subfolders

@WorkerThread
fun DocumentFile.copyTo(context: Context, targetFolder: DocumentFile, callback: FileCopyCallback? = null) {
    if (targetFolder.isDownloadsDocument) {
        copyTo(context, DocumentFileCompat.PRIMARY, Environment.DIRECTORY_DOWNLOADS, callback)
    } else {
        copyTo(context, targetFolder.storageId, targetFolder.filePath, callback)
    }
}

@WorkerThread
fun DocumentFile.copyTo(context: Context, targetStorageId: String, targetFolderPath: String, callback: FileCopyCallback? = null) {
    if (targetStorageId.isEmpty()) {
        callback?.onFailed(ErrorCode.TARGET_FOLDER_NOT_FOUND)
        return
    }

    if (targetStorageId == storageId && targetFolderPath == parentFile?.filePath) {
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
    val watchProgress = reportInterval > 0
    try {
        val targetFile = createTargetFile(context, targetStorageId, targetFolderPath, callback) ?: return
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
    targetFolderPath: String,
    callback: FileCallback?
): DocumentFile? {
    try {
        val targetFolder = DocumentFileCompat.mkdirs(context, targetStorageId, targetFolderPath)
        if (targetFolder == null) {
            callback?.onFailed(ErrorCode.STORAGE_PERMISSION_DENIED)
            return null
        }

        var targetFile = targetFolder.findFile(name.orEmpty())
        if (targetFile?.isFile == true) {
            callback?.onFailed(ErrorCode.TARGET_FILE_EXISTS)
            return null
        }

        targetFile = targetFolder.createFile(type ?: DocumentFileCompat.MIME_TYPE_UNKNOWN, name.orEmpty())
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
fun DocumentFile.moveTo(context: Context, targetFolder: DocumentFile, callback: FileMoveCallback? = null) {
    if (targetFolder.isDownloadsDocument) {
        moveTo(context, DocumentFileCompat.PRIMARY, Environment.DIRECTORY_DOWNLOADS, callback)
    } else {
        moveTo(context, targetFolder.storageId, targetFolder.filePath, callback)
    }
}

@WorkerThread
fun DocumentFile.moveTo(context: Context, targetStorageId: String, targetFolderPath: String, callback: FileMoveCallback? = null) {
    if (targetStorageId == storageId && targetFolderPath == parentFile?.filePath) {
        callback?.onFailed(ErrorCode.TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER)
        return
    }

    if (!isFile) {
        callback?.onFailed(ErrorCode.SOURCE_FILE_NOT_FOUND)
        return
    }

    if (inPrimaryStorage && targetStorageId == DocumentFileCompat.PRIMARY) {
        val sourcePath = uri.path!!.substringAfterLast("/document/$storageId:", "")
        val externalStoragePath = SimpleStorage.externalStoragePath
        val sourceFile = File("$externalStoragePath/$sourcePath")
        val targetFile = File("$externalStoragePath/$targetFolderPath", name.orEmpty())
        targetFile.parentFile?.mkdirs()
        if (sourceFile.renameTo(targetFile)) {
            callback?.onCompleted(DocumentFile.fromFile(targetFile))
            return
        }
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && storageId == targetStorageId) {
            val targetDocumentUri = DocumentFileCompat.fromPath(context, targetStorageId, targetFolderPath)?.uri ?: return
            val movedFileUri = DocumentsContract.moveDocument(context.contentResolver, uri, parentFile!!.uri, targetDocumentUri)
            if (movedFileUri != null) {
                val newFile = DocumentFile.fromTreeUri(context, movedFileUri)
                if (newFile != null && newFile.isFile) {
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
    val watchProgress = reportInterval > 0

    try {
        val targetFile = createTargetFile(context, targetStorageId, targetFolderPath, callback) ?: return
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