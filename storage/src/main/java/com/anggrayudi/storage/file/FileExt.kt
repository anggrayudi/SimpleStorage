package com.anggrayudi.storage.file

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.annotation.WorkerThread
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.extension.trimFileSeparator
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

@JvmOverloads
fun File.getRootRawFile(requiresWriteAccess: Boolean = false) = rootPath.let {
    if (it.isEmpty()) null else File(it).run {
        if (canRead() && (requiresWriteAccess && canWrite() || !requiresWriteAccess)) this else null
    }
}

val File.isReadOnly: Boolean
    get() = canRead() && !canWrite()

val File.canModify: Boolean
    get() = canRead() && canWrite()

fun File.createNewFileIfPossible(): Boolean = try {
    isFile || createNewFile()
} catch (e: IOException) {
    false
}

/**
 * Create file and if exists, increment file name.
 */
@WorkerThread
fun File.makeFile(name: String, mimeType: String = DocumentFileCompat.MIME_TYPE_UNKNOWN): File? {
    if (!isDirectory || !canWrite()) {
        return null
    }

    val extension = if (mimeType == DocumentFileCompat.MIME_TYPE_UNKNOWN) {
        ""
    } else {
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType).orEmpty()
    }
    val baseFileName = name.removeForbiddenCharsFromFilename().removeSuffix(".$extension")
    val fullFileName = "$baseFileName.$extension".trimEnd('.')

    return try {
        File(this, autoIncrementFileName(fullFileName)).let { if (it.createNewFile()) it else null }
    } catch (e: IOException) {
        null
    }
}

/**
 * @param name can input `MyFolder` or `MyFolder/SubFolder`
 */
@WorkerThread
@JvmOverloads
fun File.makeFolder(name: String, createNewFolderIfAlreadyExists: Boolean = true): File? {
    if (!isDirectory || !canWrite()) {
        return null
    }

    val directorySequence = DocumentFileCompat.getDirectorySequence(name.removeForbiddenCharsFromFilename()).toMutableList()
    val folderNameLevel1 = directorySequence.removeFirstOrNull() ?: return null
    val incrementedFolderNameLevel1 = if (createNewFolderIfAlreadyExists) autoIncrementFileName(folderNameLevel1) else folderNameLevel1
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

/**
 * Avoid duplicate file name.
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