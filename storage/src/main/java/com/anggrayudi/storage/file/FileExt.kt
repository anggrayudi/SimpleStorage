@file:JvmName("FileUtils")

package com.anggrayudi.storage.file

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.extension.trimFileSeparator
import com.anggrayudi.storage.file.DocumentFileCompat.removeForbiddenCharsFromFilename
import com.anggrayudi.storage.file.StorageId.DATA
import com.anggrayudi.storage.file.StorageId.PRIMARY
import java.io.File
import java.io.IOException

/**
 * Created on 07/09/20
 * @author Anggrayudi H
 */

/**
 * ID of this storage. For external storage, it will return [PRIMARY],
 * otherwise it is a SD Card and will return integers like `6881-2249`.
 */
fun File.getStorageId(context: Context) = when {
    path.startsWith(SimpleStorage.externalStoragePath) -> PRIMARY
    path.startsWith(context.dataDirectory.path) -> DATA
    else -> path.substringAfter("/storage/", "").substringBefore('/')
}

val File.inPrimaryStorage: Boolean
    get() = path.startsWith(SimpleStorage.externalStoragePath)

fun File.inDataStorage(context: Context) = path.startsWith(context.dataDirectory.path)

fun File.inSdCardStorage(context: Context) = getStorageId(context).let { it != PRIMARY && it != DATA && path.startsWith("/storage/$it") }

fun File.getStorageType(context: Context) = when {
    inPrimaryStorage -> StorageType.EXTERNAL
    inDataStorage(context) -> StorageType.DATA
    inSdCardStorage(context) -> StorageType.SD_CARD
    else -> StorageType.UNKNOWN
}

fun File.child(name: String) = File(this, name)

/**
 * @see [Context.getDataDir]
 * @see [Context.getFilesDir]
 */
val Context.dataDirectory: File
    get() = if (Build.VERSION.SDK_INT > 23) dataDir else filesDir.parentFile!!

fun File.getBasePath(context: Context): String {
    val externalStoragePath = SimpleStorage.externalStoragePath
    if (path.startsWith(externalStoragePath)) {
        return path.substringAfter(externalStoragePath, "").trimFileSeparator()
    }
    val dataDir = context.dataDirectory.path
    if (path.startsWith(dataDir)) {
        return path.substringAfter(dataDir, "").trimFileSeparator()
    }
    val storageId = getStorageId(context)
    return path.substringAfter("/storage/$storageId", "").trimFileSeparator()
}

fun File.getRootPath(context: Context): String {
    val storageId = getStorageId(context)
    return when {
        storageId == PRIMARY -> SimpleStorage.externalStoragePath
        storageId == DATA -> context.dataDirectory.path
        storageId.isNotEmpty() -> "/storage/$storageId"
        else -> ""
    }
}

fun File.getSimplePath(context: Context) = "${getStorageId(context)}:${getBasePath(context)}".removePrefix(":")

/**
 *  Returns:
 * * `null` if it is a directory or the file does not exist
 * * [MimeType.UNKNOWN] if the file exists but the mime type is not found
 */
val File.mimeType: String?
    get() = if (isFile) MimeType.getMimeTypeFromExtension(extension) else null

@JvmOverloads
fun File.getRootRawFile(context: Context, requiresWriteAccess: Boolean = false) = getRootPath(context).let {
    if (it.isEmpty()) null else File(it).run {
        if (canRead() && (requiresWriteAccess && isWritable(context) || !requiresWriteAccess)) this else null
    }
}

fun File.isReadOnly(context: Context) = canRead() && !isWritable(context)

fun File.canModify(context: Context) = canRead() && isWritable(context)

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
fun File.isWritable(context: Context) = canWrite() && (isFile || isExternalStorageManager(context))

/**
 * @return `true` if you have full disk access
 * @see Environment.isExternalStorageManager
 */
@Suppress("DEPRECATION")
fun File.isExternalStorageManager(context: Context) = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && Environment.isExternalStorageManager(this)
        || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && path.startsWith(SimpleStorage.externalStoragePath) && SimpleStorage.hasStoragePermission(context)
        || context.writableDirs.any { path.startsWith(it.path) }

/**
 * These directories do not require storage permissions. They are always writable with full disk access.
 */
val Context.writableDirs: Set<File>
    get() {
        val dirs = mutableSetOf(dataDirectory)
        dirs.addAll(ContextCompat.getObbDirs(this).filterNotNull())
        dirs.addAll(ContextCompat.getExternalFilesDirs(this, null).mapNotNull { it?.parentFile })
        return dirs
    }

/**
 * Create file and if exists, increment file name.
 */
@WorkerThread
fun File.makeFile(context: Context, name: String, mimeType: String? = MimeType.UNKNOWN, forceCreate: Boolean = true): File? {
    if (!isDirectory || !isWritable(context)) {
        return null
    }

    val cleanName = name.removeForbiddenCharsFromFilename().trimFileSeparator()
    val subFolder = cleanName.substringBeforeLast('/', "")
    val parent = if (subFolder.isEmpty()) this else {
        File(this, subFolder).apply { mkdirs() }
    }

    val filename = cleanName.substringAfterLast('/')
    val extensionByName = cleanName.substringAfterLast('.', "")
    val extension = if (extensionByName.isNotEmpty() && (mimeType == null || mimeType == MimeType.UNKNOWN || mimeType == MimeType.BINARY_FILE)) {
        extensionByName
    } else {
        MimeType.getExtensionFromMimeTypeOrFileName(mimeType, cleanName)
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
fun File.makeFolder(context: Context, name: String, forceCreate: Boolean = true): File? {
    if (!isDirectory || !isWritable(context)) {
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

fun File.deleteEmptyFolders(context: Context): Boolean {
    return if (isDirectory && isWritable(context)) {
        walkFileTreeAndDeleteEmptyFolders().reversed().forEach { it.delete() }
        true
    } else false
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