@file:JvmName("FileUtils")

package com.anggrayudi.storage.file

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.annotation.WorkerThread
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.extension.trimFileSeparator
import com.anggrayudi.storage.file.DocumentFileCompat.MIME_TYPE_BINARY_FILE
import com.anggrayudi.storage.file.DocumentFileCompat.MIME_TYPE_UNKNOWN
import com.anggrayudi.storage.file.DocumentFileCompat.removeForbiddenCharsFromFilename
import java.io.File
import java.io.IOException

/**
 * Created on 07/09/20
 * @author Anggrayudi H
 */

/**
 * ID of this storage. For external storage, it will return [DocumentFileCompat.PRIMARY],
 * otherwise it is a SD Card and will return integers like `6881-2249`.
 */
val File.storageId: String
    get() = if (path.startsWith(SimpleStorage.externalStoragePath)) {
        DocumentFileCompat.PRIMARY
    } else {
        path.substringAfter("/storage/", "").substringBefore('/')
    }

val File.inPrimaryStorage: Boolean
    get() = path.startsWith(SimpleStorage.externalStoragePath)

val File.inSdCardStorage: Boolean
    get() = storageId != DocumentFileCompat.PRIMARY && path.startsWith("/storage/$storageId")

val File.storageType: StorageType?
    get() = when {
        inPrimaryStorage -> StorageType.EXTERNAL
        inSdCardStorage -> StorageType.SD_CARD
        else -> null
    }

val File.basePath: String
    get() {
        val externalStoragePath = SimpleStorage.externalStoragePath
        val sdCardStoragePath = "/storage/$storageId"
        return when {
            path.startsWith(externalStoragePath) -> path.substringAfter(externalStoragePath, "").trimFileSeparator()
            path.startsWith(sdCardStoragePath) -> path.substringAfter(sdCardStoragePath, "").trimFileSeparator()
            else -> ""
        }
    }

val File.rootPath: String
    get() {
        val storageId = storageId
        return when {
            storageId == DocumentFileCompat.PRIMARY -> SimpleStorage.externalStoragePath
            storageId.isNotEmpty() -> "/storage/$storageId"
            else -> ""
        }
    }

val File.simplePath
    get() = "$storageId:$basePath".removePrefix(":")

/**
 *  Returns:
 * * `null` if it is a directory or the file does not exist
 * * [DocumentFileCompat.MIME_TYPE_UNKNOWN] if the file exists but the mime type is not found
 */
val File.mimeType: String?
    get() = if (isFile) DocumentFileCompat.getMimeTypeFromExtension(extension) else null

@JvmOverloads
fun File.getRootRawFile(requiresWriteAccess: Boolean = false) = rootPath.let {
    if (it.isEmpty()) null else File(it).run {
        if (canRead() && (requiresWriteAccess && isWritable || !requiresWriteAccess)) this else null
    }
}

val File.isReadOnly: Boolean
    get() = canRead() && !isWritable

val File.canModify: Boolean
    get() = canRead() && isWritable

val File.isEmpty: Boolean
    get() = isFile && length() == 0L || isDirectory && list().isNullOrEmpty()

fun File.createNewFileIfPossible(): Boolean = try {
    isFile || createNewFile()
} catch (e: IOException) {
    false
}

/**
 * Use it, because [File.canWrite] is not reliable on Android 10.
 * Read [this issue](https://github.com/anggrayudi/SimpleStorage/issues/24#issuecomment-830000378)
 */
val File.isWritable: Boolean
    get() = canWrite() && (isFile || (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Environment.isExternalStorageManager(this)))

/**
 * Create file and if exists, increment file name.
 */
@WorkerThread
fun File.makeFile(name: String, mimeType: String? = MIME_TYPE_UNKNOWN, forceCreate: Boolean = true): File? {
    if (!isDirectory || !isWritable) {
        return null
    }

    val cleanName = name.removeForbiddenCharsFromFilename().trimFileSeparator()
    val subFolder = cleanName.substringBeforeLast('/', "")
    val parent = if (subFolder.isEmpty()) this else {
        File(this, subFolder).apply { mkdirs() }
    }

    val filename = cleanName.substringAfterLast('/')
    val extensionByName = cleanName.substringAfterLast('.', "")
    val extension = if (extensionByName.isNotEmpty() && (mimeType == null || mimeType == MIME_TYPE_UNKNOWN || mimeType == MIME_TYPE_BINARY_FILE)) {
        extensionByName
    } else {
        DocumentFileCompat.getExtensionFromMimeTypeOrFileName(mimeType, cleanName)
    }
    val baseFileName = filename.removeSuffix(".$extension")
    val fullFileName = "$baseFileName.$extension".trimEnd('.')

    if (!forceCreate) {
        val existingFile = File(parent, fullFileName)
        if (existingFile.exists()) return existingFile.let { if (it.isFile) it else null }
    }

    return try {
        File(parent, autoIncrementFileName(fullFileName)).let { if (it.createNewFile()) it else null }
    } catch (e: IOException) {
        null
    }
}

/**
 * @param name can input `MyFolder` or `MyFolder/SubFolder`
 */
@WorkerThread
@JvmOverloads
fun File.makeFolder(name: String, forceCreate: Boolean = true): File? {
    if (!isDirectory || !isWritable) {
        return null
    }

    val directorySequence = DocumentFileCompat.getDirectorySequence(name.removeForbiddenCharsFromFilename()).toMutableList()
    val folderNameLevel1 = directorySequence.removeFirstOrNull() ?: return null
    val incrementedFolderNameLevel1 = if (forceCreate) autoIncrementFileName(folderNameLevel1) else folderNameLevel1
    val newDirectory = File(this, incrementedFolderNameLevel1).apply { mkdir() }
    return if (newDirectory.isDirectory) {
        if (directorySequence.isEmpty()) {
            newDirectory
        } else {
            File(newDirectory, directorySequence.joinToString("/")).let { if (it.mkdirs() || it.exists()) it else null }
        }
    } else {
        null
    }
}

fun File.toDocumentFile(context: Context) = if (canRead()) DocumentFileCompat.fromFile(context, this) else null

fun File.deleteEmptyFolders() {
    if (isDirectory && isWritable) {
        walkFileTreeAndDeleteEmptyFolders().reversed().forEach { it.delete() }
    }
}

private fun File.walkFileTreeAndDeleteEmptyFolders(): List<File> {
    val fileTree = mutableListOf<File>()
    listFiles()?.forEach {
        if (it.isDirectory && !it.delete()) { // Deletion is only success if the folder is empty
            fileTree.add(it)
            fileTree.addAll(it.walkFileTreeAndDeleteEmptyFolders())
        }
    }
    return fileTree
}

/**
 * Avoid duplicate file name.
 * It doesn't work if you are outside [Context.getExternalFilesDir] and don't have full disk access for Android 10+.
 */
fun File.autoIncrementFileName(filename: String): String {
    return if (File(absolutePath, filename).exists()) {
        val baseName = filename.substringBeforeLast('.')
        val ext = filename.substringAfterLast('.', "")
        val prefix = "$baseName ("
        val lastFile = list().orEmpty().filter {
            it.startsWith(prefix) && (DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITH_EXTENSION.matches(it)
                    || DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(it))
        }
            .maxOfOrNull { it }
            .orEmpty()
        var count = lastFile.substringAfterLast('(', "")
            .substringBefore(')', "")
            .toIntOrNull() ?: 0
        "$baseName (${++count}).$ext".trimEnd('.')
    } else {
        filename
    }
}