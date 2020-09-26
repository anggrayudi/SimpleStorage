package com.anggrayudi.storage.file

import android.content.Context
import com.anggrayudi.storage.SimpleStorage
import java.io.File

/**
 * Created on 07/09/20
 * @author Anggrayudi H
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

val File.rootPath: String
    get() {
        val storageId = storageId
        return when {
            storageId == DocumentFileCompat.PRIMARY -> SimpleStorage.externalStoragePath
            storageId.isNotEmpty() -> "/storage/$storageId"
            else -> ""
        }
    }

val File.filePath: String
    get() {
        val externalStoragePath = SimpleStorage.externalStoragePath
        val sdCardStoragePath = "/storage/$storageId"
        return when {
            path.startsWith(externalStoragePath) -> path.substringAfter(externalStoragePath, "").trim { it == '/' }
            path.startsWith(sdCardStoragePath) -> path.substringAfter(sdCardStoragePath, "").trim { it == '/' }
            else -> ""
        }
    }

val File.isReadOnly: Boolean
    get() = canRead() && !canWrite()

val File.isModifiable: Boolean
    get() = canRead() && canWrite()

fun File.toDocumentFile(context: Context) = if (canRead()) DocumentFileCompat.fromFile(context, this) else null

fun File.avoidDuplicateFileNameFor(filename: String): String {
    return if (File(this, filename).isFile) {
        val baseName = filename.substringBeforeLast('.')
        val ext = filename.substringAfterLast('.')
        val prefix = "$baseName ("
        val lastFile = listFiles { _, name ->
            name.startsWith(prefix) && (DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITH_EXTENSION.matches(name)
                    || DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(name))
        }.orEmpty().filter { it.isFile }.maxOfOrNull { it.name }
        var count = lastFile.orEmpty().substringAfterLast('(').substringBefore(')').toIntOrNull() ?: 0
        "$baseName (${++count}).$ext"
    } else {
        filename
    }
}

fun File.avoidDuplicateFolderNameFor(folderName: String): String {
    return if (File("$path/$folderName").isDirectory) {
        val prefix = "$folderName ("
        val lastFolder = listFiles { _, name ->
            name.startsWith(prefix) && DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(name)
        }.orEmpty().filter { it.isDirectory }.maxOfOrNull { it.name }
        var count = lastFolder.orEmpty().substringAfterLast('(').substringBefore(')').toIntOrNull() ?: 0
        "$folderName (${++count})"
    } else {
        folderName
    }
}