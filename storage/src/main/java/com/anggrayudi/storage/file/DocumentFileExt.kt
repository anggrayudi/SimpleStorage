@file:JvmName("DocumentFileUtils")

package com.anggrayudi.storage.file

import android.annotation.SuppressLint
import android.annotation.TargetApi
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
import com.anggrayudi.storage.callback.FolderCallback
import com.anggrayudi.storage.extension.*
import com.anggrayudi.storage.file.DocumentFileCompat.MIME_TYPE_BINARY_FILE
import com.anggrayudi.storage.file.DocumentFileCompat.MIME_TYPE_UNKNOWN
import com.anggrayudi.storage.file.DocumentFileCompat.PRIMARY
import com.anggrayudi.storage.file.DocumentFileCompat.removeForbiddenCharsFromFilename
import com.anggrayudi.storage.media.FileDescription
import com.anggrayudi.storage.media.MediaFile
import com.anggrayudi.storage.media.MediaStoreCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.*

/**
 * Created on 16/08/20
 * @author Anggrayudi H
 */

/**
 * ID of this storage. For external storage, it will return [PRIMARY],
 * otherwise it is a SD Card and will return integers like `6881-2249`.
 * However, it will return empty `String` if this [DocumentFile] is picked from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT]
 */
val DocumentFile.storageId: String
    get() = uri.storageId

val DocumentFile.isTreeDocumentFile: Boolean
    get() = uri.isTreeDocumentFile

val DocumentFile.isExternalStorageDocument: Boolean
    get() = uri.isExternalStorageDocument

val DocumentFile.isDownloadsDocument: Boolean
    get() = uri.isDownloadsDocument

val DocumentFile.isMediaDocument: Boolean
    get() = uri.isMediaDocument

val DocumentFile.isReadOnly: Boolean
    get() = canRead() && !isWritable

val DocumentFile.id: String
    get() = DocumentsContract.getDocumentId(uri)

val DocumentFile.rootId: String
    get() = DocumentsContract.getRootId(uri)

/**
 * Some media files do not return file extension from [DocumentFile.getName]. This function helps you to fix this kind of issue.
 */
val DocumentFile.fullName: String
    get() = if (isRawFile || isExternalStorageDocument || isDirectory) {
        name.orEmpty()
    } else {
        DocumentFileCompat.getFullFileName(name.orEmpty(), type)
    }

fun DocumentFile.isInSameMountPointWith(file: DocumentFile) = storageId == file.storageId

@SuppressLint("NewApi")
fun DocumentFile.isEmpty(context: Context): Boolean {
    return isFile && length() == 0L || isDirectory && kotlin.run {
        if (isRawFile) {
            toRawFile()?.list().isNullOrEmpty()
        } else {
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id)
                context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.count == 0 }
                    ?: true
            } catch (e: Exception) {
                true
            }
        }
    }
}

/**
 * Returns `null` if this [DocumentFile] is picked from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT]
 */
val DocumentFile.storageType: StorageType?
    get() = if (isTreeDocumentFile) {
        if (inPrimaryStorage) StorageType.EXTERNAL else StorageType.SD_CARD
    } else {
        if (inSdCardStorage) StorageType.SD_CARD else null
    }

/**
 * `true` if this file located in primary storage, i.e. external storage.
 * All files created by [DocumentFile.fromFile] are always treated from external storage.
 */
val DocumentFile.inPrimaryStorage: Boolean
    get() = isTreeDocumentFile && storageId == PRIMARY
            || isRawFile && uri.path.orEmpty().startsWith(SimpleStorage.externalStoragePath)

/**
 * `true` if this file located in SD Card
 */
val DocumentFile.inSdCardStorage: Boolean
    get() = isTreeDocumentFile && storageId != PRIMARY
            || isRawFile && uri.path.orEmpty().startsWith("/storage/$storageId")

/**
 * `true` if this file was created with [File]
 */
val DocumentFile.isRawFile: Boolean
    get() = uri.isRawFile

/**
 * Filename without extension
 */
val DocumentFile.baseName: String
    get() = fullName.substringBeforeLast('.')

/**
 * File extension
 */
val DocumentFile.extension: String
    get() = fullName.substringAfterLast('.', "")

/**
 * Advanced version of [DocumentFile.getType]. Returns:
 * * `null` if it is a directory or the file does not exist
 * * [DocumentFileCompat.MIME_TYPE_UNKNOWN] if the file exists but the mime type is not found
 */
val DocumentFile.mimeType: String?
    get() = if (isFile) type ?: DocumentFileCompat.getMimeTypeFromExtension(extension) else null

val DocumentFile.mimeTypeByFileName: String?
    get() = if (isDirectory) null else {
        val extension = name.orEmpty().substringAfterLast('.', "")
        val mimeType = DocumentFileCompat.getMimeTypeFromExtension(extension)
        if (mimeType == MIME_TYPE_UNKNOWN) type else mimeType
    }

/**
 * Please notice that accessing files with [File] only works on app private directory since Android 10. You had better to stay using [DocumentFile].
 *
 * @return `null` if you try to read files from SD Card or you want to convert a file picked
 * from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT].
 * @see toDocumentFile
 */
fun DocumentFile.toRawFile(): File? {
    return when {
        isRawFile -> File(uri.path ?: return null)
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

fun DocumentFile.toMediaFile(context: Context) = if (isTreeDocumentFile) null else MediaFile(context, uri)

/**
 * @return File path without storage ID, otherwise return empty `String` if this is the root path or if this [DocumentFile] is picked
 * from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT]
 */
@Suppress("DEPRECATION")
val DocumentFile.basePath: String
    get() = when {
        isRawFile -> uri.path?.let { File(it).basePath }.orEmpty()
        isExternalStorageDocument -> uri.path.orEmpty().substringAfterLast("/document/$storageId:", "").trimFileSeparator()
        isDownloadsDocument && isTreeDocumentFile -> {
            // content://com.android.providers.downloads.documents/tree/raw:/storage/emulated/0/Download/Denai/document/raw:/storage/emulated/0/Download/Denai
            // content://com.android.providers.downloads.documents/tree/downloads/document/raw:/storage/emulated/0/Download/Denai
            val path = uri.path.orEmpty()
            if (!path.contains("/document/raw:") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val parentTree = mutableListOf(name.orEmpty())
                var parent = this
                while (parent.parentFile?.also { parent = it } != null) {
                    parentTree.add(parent.name.orEmpty())
                }
                parentTree.reversed().joinToString("/")
            } else {
                path.substringAfterLast(SimpleStorage.externalStoragePath, "").trimFileSeparator()
            }
        }
        else -> ""
    }

/**
 * **Case 1**: Should return `Pop/Albums` from the following folders:
 * * `/storage/AAAA-BBBB/Music`
 * * `/storage/AAAA-BBBB/Music/Pop/Albums`
 *
 * **Case 2**: Should return `Albums/A Day in the Hell` from the following folders:
 * * `/storage/AAAA-BBBB/Music/Pop/Albums/A Day in the Hell`
 * * `/storage/AAAA-BBBB/Other/Pop`
 *
 * **Case 3**: Should return empty string from the following folders:
 * * `/storage/AAAA-BBBB/Music`
 * * `/storage/AAAA-BBBB/Music`
 *
 * **Case 4**: Should return `null` from the following folders:
 * * `/storage/AAAA-BBBB/Music/Metal`
 * * `/storage/AAAA-BBBB/Music/Pop/Albums`
 */
private fun DocumentFile.getSubPath(otherFolderAbsolutePath: String): String {
    val a = absolutePath
    return when {
        a.length > otherFolderAbsolutePath.length -> a.substringAfter(otherFolderAbsolutePath.substringAfterLast('/'), "").trimFileSeparator()
        otherFolderAbsolutePath.length > a.length -> otherFolderAbsolutePath.substringAfter(a.substringAfterLast('/'), "").trimFileSeparator()
        else -> ""
    }
}

/**
 * Root path of this file.
 * * For file picked from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT], it will return empty `String`
 * * For file stored in external or primary storage, it will return [SimpleStorage.externalStoragePath].
 * * For file stored in SD Card, it will return something like `/storage/6881-2249`
 */
val DocumentFile.rootPath: String
    get() = when {
        isRawFile -> uri.path?.let { File(it).rootPath }.orEmpty()
        !isTreeDocumentFile -> ""
        inSdCardStorage -> "/storage/$storageId"
        else -> SimpleStorage.externalStoragePath
    }

val DocumentFile.relativePath: String
    get() = basePath.substringBeforeLast('/', "")

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
@Suppress("DEPRECATION")
val DocumentFile.absolutePath: String
    get() = when {
        isRawFile -> uri.path.orEmpty()
        !isTreeDocumentFile -> ""
        uri.toString() == "${DocumentFileCompat.DOWNLOADS_TREE_URI}/document/downloads" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        isDownloadsDocument -> {
            val path = uri.path.orEmpty()
            if (!path.contains("/document/raw:") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val parentTree = mutableListOf(name.orEmpty())
                var parent = this
                while (parent.parentFile?.also { parent = it } != null) {
                    parentTree.add(parent.name.orEmpty())
                }
                "${SimpleStorage.externalStoragePath}/${parentTree.reversed().joinToString("/")}".trimEnd('/')
            } else {
                path.substringAfterLast("/document/raw:", "").trimEnd('/')
            }
        }
        inPrimaryStorage -> "${SimpleStorage.externalStoragePath}/$basePath".trimEnd('/')
        else -> "/storage/$storageId/$basePath".trimEnd('/')
    }

/**
 * @see absolutePath
 */
val DocumentFile.simplePath
    get() = "$storageId:$basePath".removePrefix(":")

/**
 * Delete this file and create new empty file using previous `filename` and `mimeType`.
 * It cannot be applied if current [DocumentFile] is a directory.
 */
fun DocumentFile.recreateFile(context: Context): DocumentFile? {
    return if (exists() && (isRawFile || isExternalStorageDocument)) {
        val filename = name.orEmpty()
        val parentFile = parentFile
        if (parentFile?.isWritable == true) {
            delete()
            parentFile.makeFile(context, filename, type)
        } else null
    } else null
}

@JvmOverloads
fun DocumentFile.getRootDocumentFile(context: Context, requiresWriteAccess: Boolean = false) = when {
    isTreeDocumentFile -> DocumentFileCompat.getRootDocumentFile(context, storageId, requiresWriteAccess)
    isRawFile -> uri.path?.run { File(this).getRootRawFile(requiresWriteAccess)?.let { DocumentFile.fromFile(it) } }
    else -> null
}

/**
 * @return `true` if this file exists and writeable. [DocumentFile.canWrite] may return false if you have no URI permission for read & write access.
 */
val DocumentFile.canModify: Boolean
    get() = canRead() && isWritable

/**
 * Use it, because [DocumentFile.canWrite] is not reliable on Android 10.
 * Read [this issue](https://github.com/anggrayudi/SimpleStorage/issues/24#issuecomment-830000378)
 */
val DocumentFile.isWritable: Boolean
    get() = if (isRawFile) toRawFile()!!.isWritable else canWrite()

fun DocumentFile.isRootUriPermissionGranted(context: Context): Boolean {
    return isExternalStorageDocument && DocumentFileCompat.isStorageUriPermissionGranted(context, storageId)
}

fun DocumentFile.doesExist(filename: String) = findFile(filename)?.exists() == true

/**
 * Avoid duplicate file name.
 */
@WorkerThread
fun DocumentFile.autoIncrementFileName(filename: String): String {
    toRawFile()?.let {
        return it.autoIncrementFileName(filename)
    }
    val files = listFiles()
    return if (files.find { it.name == filename }?.exists() == true) {
        val baseName = filename.substringBeforeLast('.')
        val ext = filename.substringAfterLast('.', "")
        val prefix = "$baseName ("
        val lastFile = files.filter {
            val name = it.name.orEmpty()
            name.startsWith(prefix) && (DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITH_EXTENSION.matches(name)
                    || DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(name))
        }
            .maxOfOrNull { it.name.orEmpty() }
            .orEmpty()
        var count = lastFile.substringAfterLast('(', "")
            .substringBefore(')', "")
            .toIntOrNull() ?: 0
        "$baseName (${++count}).$ext".trimEnd('.')
    } else {
        filename
    }
}

/**
 * Useful for creating temporary files. The extension is `*.bin`
 */
@WorkerThread
fun DocumentFile.createBinaryFile(context: Context, name: String, forceCreate: Boolean = true) = makeFile(context, name, MIME_TYPE_BINARY_FILE, forceCreate)

/**
 * Similar to [DocumentFile.createFile], but adds compatibility on API 28 and lower.
 * Creating files in API 28- with `createFile("my video.mp4", "video/mp4")` will create `my video.mp4`,
 * whereas API 29+ will create `my video.mp4.mp4`. This function helps you to fix this kind of bug.
 *
 * @param mimeType use [MIME_TYPE_UNKNOWN] if you're not sure about the file type
 * @param name you can input `My Video`, `My Video.mp4` or `My Folder/Sub Folder/My Video.mp4`
 * @param forceCreate if `true` and the file with this name already exists, create new file with a name that has suffix `(1)`, e.g. `My Movie (1).mp4`.
 *                    Otherwise use existed file.
 */
@JvmOverloads
@WorkerThread
fun DocumentFile.makeFile(
    context: Context,
    name: String,
    mimeType: String? = MIME_TYPE_UNKNOWN,
    forceCreate: Boolean = true
): DocumentFile? {
    if (!isDirectory || !isWritable) {
        return null
    }

    val cleanName = name.removeForbiddenCharsFromFilename().trimFileSeparator()
    val subFolder = cleanName.substringBeforeLast('/', "")
    val parent = if (subFolder.isEmpty()) this else {
        makeFolder(context, subFolder, forceCreate) ?: return null
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
        val existingFile = parent.findFile(fullFileName)
        if (existingFile?.exists() == true) return existingFile.let { if (it.isFile) it else null }
    }

    if (isRawFile) {
        // RawDocumentFile does not avoid duplicate file name, but TreeDocumentFile does.
        return DocumentFile.fromFile(toRawFile()?.makeFile(cleanName, mimeType, forceCreate) ?: return null)
    }

    val correctMimeType = DocumentFileCompat.getMimeTypeFromExtension(extension).let {
        if (it == MIME_TYPE_UNKNOWN) MIME_TYPE_BINARY_FILE else it
    }

    return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        parent.createFile(correctMimeType, baseFileName)?.also {
            if (correctMimeType == MIME_TYPE_BINARY_FILE && it.name != fullFileName)
                it.renameTo(fullFileName)
        }
    } else {
        parent.createFile(correctMimeType, fullFileName)
    }
}

/**
 * If `createNewFolderIfAlreadyExists == false`:
 * * If folder `A/B` already exists under this [DocumentFile] and you trying to create `A/B`, it will return [DocumentFile] with subfolder `A/B`.
 * * If folder `A/B` already exists under this [DocumentFile] and you trying to create `A/B/C`, it will create folder `C` and return [DocumentFile] with subfolder `A/B/C`.
 *
 * If `createNewFolderIfAlreadyExists == true`:
 * * If folder `A` already exists under this [DocumentFile] and you trying to create `A`, it will return [DocumentFile] with subfolder `A (1)`.
 * * If folder `A/B` already exists under this [DocumentFile] and you trying to create `A/B`, it will return [DocumentFile] with subfolder `A (1)/B`.
 *
 * @param name can input `MyFolder` or `MyFolder/SubFolder`
 * @param forceCreate if `true` and the file with this name already exists, create new file with a name that has suffix `(1)`, e.g. `Movies (1)`.
 *                    Otherwise use existed folder.
 */
@WorkerThread
@JvmOverloads
fun DocumentFile.makeFolder(context: Context, name: String, forceCreate: Boolean = true): DocumentFile? {
    if (!isDirectory || !isWritable) {
        return null
    }

    if (isRawFile) {
        return DocumentFile.fromFile(toRawFile()?.makeFolder(name, forceCreate) ?: return null)
    }

    // if name is "Aduhhh/Now/Dee", system will convert it to Aduhhh_Now_Dee, so create a sequence
    val directorySequence = DocumentFileCompat.getDirectorySequence(name.removeForbiddenCharsFromFilename()).toMutableList()
    val folderNameLevel1 = directorySequence.removeFirstOrNull() ?: return null
    var currentDirectory = if (isDownloadsDocument && isTreeDocumentFile) (toWritableDownloadsDocumentFile(context) ?: return null) else this
    val folderLevel1 = currentDirectory.findFile(folderNameLevel1)
    currentDirectory = if (folderLevel1 == null || forceCreate) {
        currentDirectory.createDirectory(folderNameLevel1) ?: return null
    } else if (folderLevel1.isDirectory && folderLevel1.canRead()) {
        folderLevel1
    } else {
        return null
    }
    directorySequence.forEach { folder ->
        try {
            val directory = currentDirectory.findFile(folder)
            currentDirectory = if (directory == null) {
                currentDirectory.createDirectory(folder) ?: return null
            } else if (directory.isDirectory && directory.canRead()) {
                if (forceCreate) {
                    currentDirectory.createDirectory(folder) ?: return null
                } else {
                    directory
                }
            } else {
                return null
            }
        } catch (e: Exception) {
            return null
        }
    }
    return currentDirectory
}

/**
 * Use this function if you cannot create file/folder in downloads directory.
 */
@WorkerThread
fun DocumentFile.toWritableDownloadsDocumentFile(context: Context): DocumentFile? {
    return if (isDownloadsDocument) {
        val path = uri.path.orEmpty()
        when {
            uri.toString() == "${DocumentFileCompat.DOWNLOADS_TREE_URI}/document/downloads" -> takeIf { it.isWritable }
            // content://com.android.providers.downloads.documents/tree/downloads/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FIKO5
            // raw:/storage/emulated/0/Download/IKO5
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && path.startsWith("/tree/downloads/document/raw") -> {
                var currentDirectory = DocumentFileCompat.fromPublicFolder(context, PublicDirectory.DOWNLOADS, considerRawFile = false)
                    ?: return null
                DocumentFileCompat.getDirectorySequence(path.substringAfter("/${Environment.DIRECTORY_DOWNLOADS}")).forEach {
                    val directory = currentDirectory.findFile(it) ?: return null
                    if (directory.canRead()) {
                        currentDirectory = directory
                    } else {
                        return null
                    }
                }
                currentDirectory.takeIf { it.isWritable }
            }

            // msd for directories and msf for files
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (
                    // If comes from SAF folder picker ACTION_OPEN_DOCUMENT_TREE,
                    // e.g. content://com.android.providers.downloads.documents/tree/msd%3A535/document/msd%3A535
                    path.startsWith("/tree/msd") || path.startsWith("/tree/msf")
                            // If comes from findFile() or fromPublicFolder(),
                            // e.g. content://com.android.providers.downloads.documents/tree/downloads/document/msd%3A271
                            || path.startsWith("/tree/downloads/document/msd") || path.startsWith("/tree/downloads/document/msf"))
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && (
                    // If comes from SAF folder picker ACTION_OPEN_DOCUMENT_TREE,
                    // e.g. content://com.android.providers.downloads.documents/tree/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FDenai/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FDenai
                    path.startsWith("/tree/raw")
                            // If comes from findFile() or fromPublicFolder(),
                            // e.g. content://com.android.providers.downloads.documents/tree/downloads/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FDenai
                            || path.startsWith("/tree/downloads/document/raw")
                    )
            -> takeIf { it.isWritable }

            else -> null
        }
    } else {
        null
    }
}

/**
 * @param names full file names, with their extension
 */
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
    mimeType: String = MIME_TYPE_UNKNOWN,
    name: String = "",
    regex: Regex? = null
): List<DocumentFile> {
    return when {
        !isDirectory -> emptyList()
        recursive -> walkFileTreeForSearch(documentType, mimeType, name, regex)
        else -> {
            var sequence = listFiles().asSequence().filter { it.canRead() }
            if (name.isNotEmpty()) {
                sequence = sequence.filter { it.name == name }
            }
            if (regex != null) {
                sequence = sequence.filter { regex.matches(it.name.orEmpty()) }
            }
            if (mimeType != MIME_TYPE_UNKNOWN) {
                sequence = sequence.filter { it.mimeType == mimeType }
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

private fun DocumentFile.walkFileTreeForSearch(
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
                && (mimeType == MIME_TYPE_UNKNOWN || file.mimeType == mimeType)
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
            fileTree.addAll(file.walkFileTreeForSearch(documentType, mimeType, nameFilter, regex))
        }
    }
    return fileTree
}

@WorkerThread
@JvmOverloads
fun DocumentFile.deleteRecursively(context: Context, childrenOnly: Boolean = false): Boolean {
    return if (isDirectory && canRead()) {
        val files = if (isDownloadsDocument) {
            toWritableDownloadsDocumentFile(context)?.walkFileTreeForDeletion() ?: return false
        } else {
            walkFileTreeForDeletion()
        }
        var count = files.size
        for (index in files.size - 1 downTo 0) {
            if (files[index].delete())
                count--
        }
        count == 0 && (childrenOnly || delete())
    } else {
        delete()
    }
}

private fun DocumentFile.walkFileTreeForDeletion(): List<DocumentFile> {
    val fileTree = mutableListOf<DocumentFile>()
    listFiles().forEach {
        if (!it.delete()) {
            fileTree.add(it)
        }
        if (it.isDirectory) {
            fileTree.addAll(it.walkFileTreeForDeletion())
        }
    }
    return fileTree
}

fun DocumentFile.deleteEmptyFolders() {
    if (isRawFile) {
        toRawFile()?.deleteEmptyFolders()
    } else if (isDirectory && isWritable) {
        walkFileTreeAndDeleteEmptyFolders().reversed().forEach { it.delete() }
    }
}

private fun DocumentFile.walkFileTreeAndDeleteEmptyFolders(): List<DocumentFile> {
    val fileTree = mutableListOf<DocumentFile>()
    listFiles().forEach {
        if (it.isDirectory && !it.delete()) { // Deletion is only success if the folder is empty
            fileTree.add(it)
            fileTree.addAll(it.walkFileTreeAndDeleteEmptyFolders())
        }
    }
    return fileTree
}

/**
 * @param append if `false` and the file already exists, it will recreate the file.
 */
@JvmOverloads
@WorkerThread
fun DocumentFile.openOutputStream(context: Context, append: Boolean = true) = uri.openOutputStream(context, append)

@WorkerThread
fun DocumentFile.openInputStream(context: Context) = uri.openInputStream(context)

@UiThread
fun DocumentFile.openFileIntent(context: Context, authority: String) = Intent(Intent.ACTION_VIEW)
    .setData(if (isRawFile) FileProvider.getUriForFile(context, authority, File(uri.path!!)) else uri)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun DocumentFile.hasParent(parent: DocumentFile) = absolutePath.hasParent(parent.absolutePath)

private fun DocumentFile.walkFileTree(context: Context): List<DocumentFile> {
    val fileTree = mutableListOf<DocumentFile>()
    listFiles().forEach {
        if (it.isDirectory) {
            if (it.isEmpty(context)) {
                fileTree.add(it)
            } else {
                fileTree.addAll(it.walkFileTree(context))
            }
        } else {
            fileTree.add(it)
        }
    }
    return fileTree
}

private fun DocumentFile.walkFileTreeAndSkipEmptyFiles(): List<DocumentFile> {
    val fileTree = mutableListOf<DocumentFile>()
    listFiles().forEach {
        if (it.isDirectory) {
            fileTree.addAll(it.walkFileTreeAndSkipEmptyFiles())
        } else if (it.length() > 0) {
            fileTree.add(it)
        }
    }
    return fileTree
}

// TODO: 10/05/21 Copy multiple DocumentFile with List<DocumentFile>.copyTo()

@WorkerThread
fun DocumentFile.moveFolderTo(
    context: Context,
    targetParentFolder: DocumentFile,
    skipEmptyFiles: Boolean = true,
    newFolderNameInTargetPath: String? = null,
    callback: FolderCallback
) {
    copyFolderTo(context, targetParentFolder, skipEmptyFiles, newFolderNameInTargetPath, true, callback)
}

@WorkerThread
fun DocumentFile.copyFolderTo(
    context: Context,
    targetParentFolder: DocumentFile,
    skipEmptyFiles: Boolean = true,
    newFolderNameInTargetPath: String? = null,
    callback: FolderCallback
) {
    copyFolderTo(context, targetParentFolder, skipEmptyFiles, newFolderNameInTargetPath, false, callback)
}

/**
 * @param skipEmptyFiles skip copying empty files & folders
 */
private fun DocumentFile.copyFolderTo(
    context: Context,
    targetParentFolder: DocumentFile,
    skipEmptyFiles: Boolean = true,
    newFolderNameInTargetPath: String? = null,
    deleteSourceWhenComplete: Boolean,
    callback: FolderCallback
) {
    val writableTargetParentFolder = doesMeetCopyRequirements(context, targetParentFolder, newFolderNameInTargetPath, callback) ?: return

    callback.onPrepare()

    val targetFolderParentName = (newFolderNameInTargetPath ?: name.orEmpty()).removeForbiddenCharsFromFilename().trimFileSeparator()
    val conflictResolution = handleParentFolderConflict(context, targetParentFolder, targetFolderParentName, callback)
    if (conflictResolution == FolderCallback.ConflictResolution.SKIP) {
        return
    }

    callback.onCountingFiles()

    val filesToCopy = if (skipEmptyFiles) walkFileTreeAndSkipEmptyFiles() else walkFileTree(context)
    var totalFilesToCopy = 0
    var totalSizeToCopy = 0L
    filesToCopy.forEach {
        if (it.isFile) {
            totalFilesToCopy++
            totalSizeToCopy += it.length()
        }
    }

    if (deleteSourceWhenComplete && isInSameMountPointWith(writableTargetParentFolder)) {
        if (storageId == PRIMARY && inPrimaryStorage) {
            val targetFile = File(writableTargetParentFolder.absolutePath, targetFolderParentName)
            targetFile.parentFile?.mkdirs()
            if (toRawFile()?.renameTo(targetFile) == true) {
                if (skipEmptyFiles) targetFile.deleteEmptyFolders()
                callback.onCompleted(DocumentFile.fromFile(targetFile), totalFilesToCopy, totalFilesToCopy, true)
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val sourceFile = toRawFile()
            if (sourceFile == null) {
                callback.onFailed(FolderCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
                return
            }
            writableTargetParentFolder.toRawFile()?.let { destinationFolder ->
                destinationFolder.mkdirs()
                val targetFile = File(destinationFolder, targetFolderParentName)
                if (sourceFile.renameTo(targetFile)) {
                    if (skipEmptyFiles) targetFile.deleteEmptyFolders()
                    callback.onCompleted(DocumentFile.fromFile(targetFile), totalFilesToCopy, totalFilesToCopy, true)
                    return
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val movedFileUri = parentFile?.uri?.let { DocumentsContract.moveDocument(context.contentResolver, uri, it, writableTargetParentFolder.uri) }
                if (movedFileUri != null) {
                    val newFile = context.fromTreeUri(movedFileUri)
                    if (newFile != null && newFile.isDirectory) {
                        if (newFolderNameInTargetPath != null) newFile.renameTo(targetFolderParentName)
                        if (skipEmptyFiles) newFile.deleteEmptyFolders()
                        callback.onCompleted(newFile, totalFilesToCopy, totalFilesToCopy, true)
                    } else {
                        callback.onFailed(FolderCallback.ErrorCode.INVALID_TARGET_FOLDER)
                    }
                    return
                }
            }
        } catch (e: Throwable) {
            callback.onFailed(FolderCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
            return
        }
    }

    try {
        if (!callback.onCheckFreeSpace(DocumentFileCompat.getFreeSpace(context, writableTargetParentFolder.storageId), totalSizeToCopy)) {
            callback.onFailed(FolderCallback.ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH)
            return
        }
    } catch (e: Throwable) {
        callback.onFailed(FolderCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
        return
    }

    val reportInterval = callback.onStart(this, totalFilesToCopy)
    if (reportInterval < 0) return

    val targetFolder =
        writableTargetParentFolder.makeFolder(context, targetFolderParentName, conflictResolution == FolderCallback.ConflictResolution.CREATE_NEW)
    if (targetFolder == null) {
        callback.onFailed(FolderCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
        return
    }

    if (filesToCopy.isEmpty()) {
        callback.onCompleted(targetFolder, 0, 0, true)
        return
    }

    var totalCopiedFiles = 0
    var timer: Job? = null
    var byteMoved = 0L
    var writeSpeed = 0
    if (reportInterval > 0 && totalSizeToCopy > 10 * FileSize.MB) {
        timer = startCoroutineTimer(repeatMillis = reportInterval) {
            callback.onReport(byteMoved * 100f / totalSizeToCopy, byteMoved, writeSpeed, totalCopiedFiles)
            writeSpeed = 0
        }
    }

    var success = true
    var canceled = false

    val notifyCanceled: (OutputStream?, DocumentFile?, FolderCallback.ErrorCode) -> Unit = { outputStream, targetFile, errorCode ->
        if (!canceled) {
            canceled = true
            timer?.cancel()
            outputStream.closeStream()
            targetFile?.delete()
            callback.onFailed(errorCode)
            callback.onCompleted(targetFolder, totalFilesToCopy, totalCopiedFiles, false)
        }
    }

    val targetFolderParentPath = writableTargetParentFolder.absolutePath + "/$targetFolderParentName"
    val conflictedFiles = ArrayList<FolderCallback.FileConflict>(totalFilesToCopy)
    val buffer = ByteArray(1024)

    for (sourceFile in filesToCopy) {
        try {
            if (Thread.currentThread().isInterrupted) {
                notifyCanceled(null, null, FolderCallback.ErrorCode.CANCELED)
                return
            }
            if (!sourceFile.exists()) {
                continue
            }

            val subPath = sourceFile.getSubPath(targetFolderParentPath).substringBeforeLast('/', "")
            val filename = ("$subPath/" + sourceFile.name.orEmpty()).trimFileSeparator()
            if (sourceFile.isDirectory) {
                val newFolder = targetFolder.makeFolder(context, filename, false)
                if (newFolder == null) {
                    success = false
                    break
                }
                continue
            }

            val targetFile = targetFolder.makeFile(context, filename, type, false)
            if (targetFile != null && targetFile.length() > 0) {
                conflictedFiles.add(FolderCallback.FileConflict(sourceFile, targetFile))
                continue
            }

            if (targetFile == null) {
                notifyCanceled(null, null, FolderCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
                return
            }

            createFileStreams(context, sourceFile, targetFile, callback) { inputStream, outputStream ->
                try {
                    var read = inputStream.read(buffer)
                    while (read != -1) {
                        outputStream.write(buffer, 0, read)
                        byteMoved += read
                        writeSpeed += read
                        read = inputStream.read(buffer)
                    }
                } catch (e: InterruptedIOException) {
                    notifyCanceled(outputStream, targetFile, FolderCallback.ErrorCode.CANCELED)
                    return
                } catch (e: InterruptedException) {
                    notifyCanceled(outputStream, targetFile, FolderCallback.ErrorCode.CANCELED)
                    return
                } catch (e: IOException) {
                    notifyCanceled(outputStream, targetFile, FolderCallback.ErrorCode.UNKNOWN_IO_ERROR)
                    return
                } finally {
                    inputStream.closeStream()
                    outputStream.closeStream()
                }
            }
            totalCopiedFiles++
            if (deleteSourceWhenComplete) targetFile.delete()
        } catch (e: SecurityException) {
            callback.onFailed(FolderCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
            success = false
            break
        } catch (e: InterruptedIOException) {
            notifyCanceled(null, null, FolderCallback.ErrorCode.CANCELED)
            success = false
            break
        } catch (e: InterruptedException) {
            notifyCanceled(null, null, FolderCallback.ErrorCode.CANCELED)
            success = false
            break
        } catch (e: IOException) {
            callback.onFailed(FolderCallback.ErrorCode.UNKNOWN_IO_ERROR)
            success = false
            break
        }
    }
    timer?.cancel()
    if (!success || conflictedFiles.isEmpty()) {
        if (deleteSourceWhenComplete && success) deleteRecursively(context)
        callback.onCompleted(targetFolder, totalFilesToCopy, totalCopiedFiles, success)
        return
    }
    val solutions = runBlocking<List<FolderCallback.FileConflict>> {
        suspendCancellableCoroutine { continuation ->
            launchOnUiThread {
                callback.onContentConflict(targetFolder, conflictedFiles, FolderCallback.FolderContentConflictAction(continuation))
            }
        }
    }.filter {
        // free up space first, by deleting some files
        if (it.solution == FolderCallback.FileConflict.Solution.ACCEPT_TARGET) {
            if (deleteSourceWhenComplete) it.source.delete()
            totalCopiedFiles++
        }
        it.solution != FolderCallback.FileConflict.Solution.ACCEPT_TARGET
    }
    val sizeOfConflictedFiles = solutions.sumOf { it.source.length() }
    if (reportInterval > 0 && sizeOfConflictedFiles > 10 * FileSize.MB) {
        writeSpeed = 0
        timer = startCoroutineTimer(repeatMillis = reportInterval) {
            callback.onReport(byteMoved * 100f / totalSizeToCopy, byteMoved, writeSpeed, totalCopiedFiles)
            writeSpeed = 0
        }
    }
    for (conflict in solutions) {
        if (Thread.currentThread().isInterrupted) {
            notifyCanceled(null, null, FolderCallback.ErrorCode.CANCELED)
            return
        }
        if (!conflict.source.isFile) {
            continue
        }
        val filename = conflict.target.name.orEmpty()
        if (conflict.solution == FolderCallback.FileConflict.Solution.ACCEPT_SOURCE && conflict.target.let { !it.delete() || it.exists() }) {
            continue
        }

        val targetFile = conflict.target.parentFile?.makeFile(context, filename)
        if (targetFile == null) {
            notifyCanceled(null, null, FolderCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
            return
        }

        try {
            createFileStreams(context, conflict.source, targetFile, callback) { inputStream, outputStream ->
                try {
                    var read = inputStream.read(buffer)
                    while (read != -1) {
                        outputStream.write(buffer, 0, read)
                        byteMoved += read
                        writeSpeed += read
                        read = inputStream.read(buffer)
                    }
                } catch (e: InterruptedIOException) {
                    notifyCanceled(outputStream, targetFile, FolderCallback.ErrorCode.CANCELED)
                    return
                } catch (e: InterruptedException) {
                    notifyCanceled(outputStream, targetFile, FolderCallback.ErrorCode.CANCELED)
                    return
                } catch (e: IOException) {
                    notifyCanceled(outputStream, targetFile, FolderCallback.ErrorCode.UNKNOWN_IO_ERROR)
                    return
                } finally {
                    inputStream.closeStream()
                    outputStream.closeStream()
                }
            }
            totalCopiedFiles++
            if (deleteSourceWhenComplete) targetFile.delete()
        } catch (e: SecurityException) {
            callback.onFailed(FolderCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
            success = false
            break
        } catch (e: InterruptedIOException) {
            notifyCanceled(null, null, FolderCallback.ErrorCode.CANCELED)
            success = false
            break
        } catch (e: InterruptedException) {
            notifyCanceled(null, null, FolderCallback.ErrorCode.CANCELED)
            success = false
            break
        } catch (e: IOException) {
            callback.onFailed(FolderCallback.ErrorCode.UNKNOWN_IO_ERROR)
            success = false
            break
        }
    }
    timer?.cancel()
    if (deleteSourceWhenComplete && success) deleteRecursively(context)
    callback.onCompleted(targetFolder, totalFilesToCopy, totalCopiedFiles, success)
}

private fun DocumentFile.doesMeetCopyRequirements(
    context: Context,
    targetParentFolder: DocumentFile,
    newFolderNameInTargetPath: String?,
    callback: FolderCallback
): DocumentFile? {
    if (!isDirectory) {
        callback.onFailed(FolderCallback.ErrorCode.SOURCE_FOLDER_NOT_FOUND)
        return null
    }

    if (!targetParentFolder.isDirectory) {
        callback.onFailed(FolderCallback.ErrorCode.INVALID_TARGET_FOLDER)
        return null
    }

    if (!canRead() || !targetParentFolder.isWritable) {
        callback.onFailed(FolderCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
        return null
    }

    if (targetParentFolder.absolutePath == parentFile?.absolutePath && (newFolderNameInTargetPath.isNullOrEmpty() || name == newFolderNameInTargetPath)) {
        callback.onFailed(FolderCallback.ErrorCode.TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER)
        return null
    }

    return targetParentFolder.let { if (it.isDownloadsDocument) it.toWritableDownloadsDocumentFile(context) else it }
}

/**
 * @param fileDescription Use it if you want to change file name and type in the destination.
 */
@WorkerThread
fun DocumentFile.copyFileTo(context: Context, targetFolder: File, fileDescription: FileDescription? = null, callback: FileCallback) {
    copyFileTo(context, targetFolder.absolutePath, fileDescription, callback)
}

/**
 * @param targetFolderAbsolutePath use [DocumentFileCompat.buildAbsolutePath] to construct the path
 * @param fileDescription Use it if you want to change file name and type in the destination.
 */
@WorkerThread
fun DocumentFile.copyFileTo(context: Context, targetFolderAbsolutePath: String, fileDescription: FileDescription? = null, callback: FileCallback) {
    val targetFolder = DocumentFileCompat.mkdirs(context, targetFolderAbsolutePath, true)
    if (targetFolder == null) {
        callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
    } else {
        copyFileTo(context, targetFolder, fileDescription, callback)
    }
}

/**
 * @param fileDescription Use it if you want to change file name and type in the destination.
 */
@WorkerThread
fun DocumentFile.copyFileTo(
    context: Context,
    targetFolder: DocumentFile,
    fileDescription: FileDescription? = null,
    callback: FileCallback
) {
    if (fileDescription?.subFolder.isNullOrEmpty()) {
        copyFileTo(context, targetFolder, fileDescription?.name, fileDescription?.mimeType, callback)
    } else {
        val targetDirectory = targetFolder.makeFolder(context, fileDescription?.subFolder.orEmpty(), false)
        if (targetDirectory == null) {
            callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
        } else {
            copyFileTo(context, targetDirectory, fileDescription?.name, fileDescription?.mimeType, callback)
        }
    }
}

private fun DocumentFile.copyFileTo(
    context: Context,
    targetFolder: DocumentFile,
    newFilenameInTargetPath: String?,
    newMimeTypeInTargetPath: String?,
    callback: FileCallback
) {
    val writableTargetFolder = doesMeetCopyRequirements(context, targetFolder, newFilenameInTargetPath, callback) ?: return

    callback.onPrepare()

    try {
        if (!callback.onCheckFreeSpace(DocumentFileCompat.getFreeSpace(context, writableTargetFolder.storageId), length())) {
            callback.onFailed(FileCallback.ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH)
            return
        }
    } catch (e: Throwable) {
        callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
        return
    }

    val cleanFileName = DocumentFileCompat.getFullFileName(newFilenameInTargetPath ?: name.orEmpty(), newMimeTypeInTargetPath ?: mimeTypeByFileName)
        .removeForbiddenCharsFromFilename().trimFileSeparator()
    val fileConflictResolution = handleFileConflict(context, writableTargetFolder, cleanFileName, callback)
    if (fileConflictResolution == FileCallback.ConflictResolution.SKIP) {
        return
    }

    val reportInterval = callback.onStart(this)
    if (reportInterval < 0) return
    val watchProgress = reportInterval > 0
    try {
        val targetFile = createTargetFile(
            context, writableTargetFolder, cleanFileName, newMimeTypeInTargetPath ?: mimeTypeByFileName,
            fileConflictResolution == FileCallback.ConflictResolution.CREATE_NEW, callback
        ) ?: return
        createFileStreams(context, this, targetFile, callback) { inputStream, outputStream ->
            copyFileStream(inputStream, outputStream, targetFile, watchProgress, reportInterval, false, callback)
        }
    } catch (e: SecurityException) {
        callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
    } catch (e: InterruptedIOException) {
        callback.onFailed(FileCallback.ErrorCode.CANCELED)
    } catch (e: InterruptedException) {
        callback.onFailed(FileCallback.ErrorCode.CANCELED)
    } catch (e: IOException) {
        callback.onFailed(FileCallback.ErrorCode.UNKNOWN_IO_ERROR)
    }
}

/**
 * @return writable [DocumentFile] for `targetFolder`
 */
private fun DocumentFile.doesMeetCopyRequirements(
    context: Context,
    targetFolder: DocumentFile,
    newFilenameInTargetPath: String?,
    callback: FileCallback
): DocumentFile? {
    if (!isFile) {
        callback.onFailed(FileCallback.ErrorCode.SOURCE_FILE_NOT_FOUND)
        return null
    }

    if (!targetFolder.isDirectory) {
        callback.onFailed(FileCallback.ErrorCode.TARGET_FOLDER_NOT_FOUND)
        return null
    }

    if (!canRead() || !targetFolder.isWritable) {
        callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
        return null
    }

    if (parentFile?.absolutePath == targetFolder.absolutePath && (newFilenameInTargetPath.isNullOrEmpty() || name == newFilenameInTargetPath)) {
        callback.onFailed(FileCallback.ErrorCode.TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER)
        return null
    }

    return targetFolder.let { if (it.isDownloadsDocument) it.toWritableDownloadsDocumentFile(context) else it }
}

private inline fun createFileStreams(
    context: Context,
    sourceFile: DocumentFile,
    targetFile: DocumentFile,
    callback: FileCallback,
    onStreamsReady: (InputStream, OutputStream) -> Unit
) {
    val outputStream = targetFile.openOutputStream(context)
    if (outputStream == null) {
        callback.onFailed(FileCallback.ErrorCode.TARGET_FILE_NOT_FOUND)
        return
    }

    val inputStream = sourceFile.openInputStream(context)
    if (inputStream == null) {
        callback.onFailed(FileCallback.ErrorCode.SOURCE_FILE_NOT_FOUND)
        outputStream.closeStream()
        return
    }

    onStreamsReady(inputStream, outputStream)
}

private inline fun createFileStreams(
    context: Context,
    sourceFile: DocumentFile,
    targetFile: MediaFile,
    callback: FileCallback,
    onStreamsReady: (InputStream, OutputStream) -> Unit
) {
    val outputStream = targetFile.openOutputStream()
    if (outputStream == null) {
        callback.onFailed(FileCallback.ErrorCode.TARGET_FILE_NOT_FOUND)
        return
    }

    val inputStream = sourceFile.openInputStream(context)
    if (inputStream == null) {
        callback.onFailed(FileCallback.ErrorCode.SOURCE_FILE_NOT_FOUND)
        outputStream.closeStream()
        return
    }

    onStreamsReady(inputStream, outputStream)
}

private fun createTargetFile(
    context: Context,
    targetFolder: DocumentFile,
    newFilenameInTargetPath: String,
    mimeType: String?,
    forceCreate: Boolean,
    callback: FileCallback
): DocumentFile? {
    val targetFile = targetFolder.makeFile(context, newFilenameInTargetPath, mimeType, forceCreate)
    if (targetFile == null) {
        callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
    }
    return targetFile
}

private inline fun createFileStreams(
    context: Context,
    sourceFile: DocumentFile,
    targetFile: DocumentFile,
    callback: FolderCallback,
    onStreamsReady: (InputStream, OutputStream) -> Unit
) {
    val outputStream = targetFile.openOutputStream(context)
    if (outputStream == null) {
        callback.onFailed(FolderCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
        return
    }

    val inputStream = sourceFile.openInputStream(context)
    if (inputStream == null) {
        callback.onFailed(FolderCallback.ErrorCode.SOURCE_FILE_NOT_FOUND)
        outputStream.closeStream()
        return
    }

    onStreamsReady(inputStream, outputStream)
}

/**
 * @param targetFile can be [MediaFile] or [DocumentFile]
 */
private fun DocumentFile.copyFileStream(
    inputStream: InputStream,
    outputStream: OutputStream,
    targetFile: Any,
    watchProgress: Boolean,
    reportInterval: Long,
    deleteSourceFileWhenComplete: Boolean,
    callback: FileCallback
) {
    var timer: Job? = null
    try {
        var byteMoved = 0L
        var writeSpeed = 0
        val srcSize = length()
        // using timer on small file is useless. We set minimum 10MB.
        if (watchProgress && srcSize > 10 * FileSize.MB) {
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
        if (deleteSourceFileWhenComplete) {
            delete()
        }
        if (targetFile is MediaFile) {
            targetFile.length = srcSize
        }
        callback.onCompleted(targetFile)
    } finally {
        timer?.cancel()
        inputStream.closeStream()
        outputStream.closeStream()
    }
}

/**
 * @param fileDescription Use it if you want to change file name and type in the destination.
 */
@WorkerThread
fun DocumentFile.moveFileTo(context: Context, targetFolder: File, fileDescription: FileDescription? = null, callback: FileCallback) {
    moveFileTo(context, targetFolder.absolutePath, fileDescription, callback)
}

/**
 * @param targetFolderAbsolutePath use [DocumentFileCompat.buildAbsolutePath] to construct the path
 * @param fileDescription Use it if you want to change file name and type in the destination.
 */
@WorkerThread
fun DocumentFile.moveFileTo(context: Context, targetFolderAbsolutePath: String, fileDescription: FileDescription? = null, callback: FileCallback) {
    val targetFolder = DocumentFileCompat.mkdirs(context, targetFolderAbsolutePath, true)
    if (targetFolder == null) {
        callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
    } else {
        moveFileTo(context, targetFolder, fileDescription, callback)
    }
}

/**
 * @param fileDescription Use it if you want to change file name and type in the destination.
 */
@WorkerThread
fun DocumentFile.moveFileTo(
    context: Context,
    targetFolder: DocumentFile,
    fileDescription: FileDescription? = null,
    callback: FileCallback
) {
    if (fileDescription?.subFolder.isNullOrEmpty()) {
        moveFileTo(context, targetFolder, fileDescription?.name, fileDescription?.mimeType, callback)
    } else {
        val targetDirectory = targetFolder.makeFolder(context, fileDescription?.subFolder.orEmpty(), false)
        if (targetDirectory == null) {
            callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
        } else {
            moveFileTo(context, targetDirectory, fileDescription?.name, fileDescription?.mimeType, callback)
        }
    }
}

private fun DocumentFile.moveFileTo(
    context: Context,
    targetFolder: DocumentFile,
    newFilenameInTargetPath: String?,
    newMimeTypeInTargetPath: String?,
    callback: FileCallback
) {
    val writableTargetFolder = doesMeetCopyRequirements(context, targetFolder, newFilenameInTargetPath, callback) ?: return

    callback.onPrepare()

    val cleanFileName = DocumentFileCompat.getFullFileName(newFilenameInTargetPath ?: name.orEmpty(), newMimeTypeInTargetPath ?: mimeTypeByFileName)
        .removeForbiddenCharsFromFilename().trimFileSeparator()
    val fileConflictResolution = handleFileConflict(context, writableTargetFolder, cleanFileName, callback)
    if (fileConflictResolution == FileCallback.ConflictResolution.SKIP) {
        return
    }

    val targetStorageId = writableTargetFolder.storageId
    if (targetStorageId == PRIMARY && inPrimaryStorage) {
        val targetFile = File(writableTargetFolder.absolutePath, cleanFileName)
        targetFile.parentFile?.mkdirs()
        if (toRawFile()?.renameTo(targetFile) == true) {
            callback.onCompleted(DocumentFile.fromFile(targetFile))
            return
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() && storageId == targetStorageId) {
        val sourceFile = toRawFile()
        if (sourceFile == null) {
            callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
            return
        }
        writableTargetFolder.toRawFile()?.let { destinationFolder ->
            destinationFolder.mkdirs()
            val targetFile = File(destinationFolder, cleanFileName)
            if (sourceFile.renameTo(targetFile)) {
                callback.onCompleted(DocumentFile.fromFile(targetFile))
                return
            }
        }
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && storageId == targetStorageId) {
            val movedFileUri = parentFile?.uri?.let { DocumentsContract.moveDocument(context.contentResolver, uri, it, writableTargetFolder.uri) }
            if (movedFileUri != null) {
                val newFile = context.fromTreeUri(movedFileUri)
                if (newFile != null && newFile.isFile) {
                    if (newFilenameInTargetPath != null) newFile.renameTo(cleanFileName)
                    callback.onCompleted(newFile)
                } else {
                    callback.onFailed(FileCallback.ErrorCode.TARGET_FILE_NOT_FOUND)
                }
                return
            }
        }

        if (!callback.onCheckFreeSpace(DocumentFileCompat.getFreeSpace(context, targetStorageId), length())) {
            callback.onFailed(FileCallback.ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH)
            return
        }
    } catch (e: Throwable) {
        callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
        return
    }

    val reportInterval = callback.onStart(this)
    if (reportInterval < 0) return
    val watchProgress = reportInterval > 0

    try {
        val targetFile = createTargetFile(
            context, writableTargetFolder, cleanFileName, newMimeTypeInTargetPath ?: mimeTypeByFileName,
            fileConflictResolution == FileCallback.ConflictResolution.CREATE_NEW, callback
        ) ?: return
        createFileStreams(context, this, targetFile, callback) { inputStream, outputStream ->
            copyFileStream(inputStream, outputStream, targetFile, watchProgress, reportInterval, true, callback)
        }
    } catch (e: SecurityException) {
        callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
    } catch (e: InterruptedIOException) {
        callback.onFailed(FileCallback.ErrorCode.CANCELED)
    } catch (e: InterruptedException) {
        callback.onFailed(FileCallback.ErrorCode.CANCELED)
    } catch (e: IOException) {
        callback.onFailed(FileCallback.ErrorCode.UNKNOWN_IO_ERROR)
    }
}

/**
 * @param targetFile create it with [MediaStoreCompat], e.g. [MediaStoreCompat.createDownload]
 */
@WorkerThread
@TargetApi(Build.VERSION_CODES.Q)
fun DocumentFile.moveFileTo(
    context: Context,
    targetFile: MediaFile,
    callback: FileCallback
) {
    copyFileTo(context, targetFile, true, callback)
}

/**
 * @param targetFile create it with [MediaStoreCompat], e.g. [MediaStoreCompat.createDownload]
 */
@WorkerThread
@TargetApi(Build.VERSION_CODES.Q)
fun DocumentFile.copyFileTo(
    context: Context,
    targetFile: MediaFile,
    callback: FileCallback
) {
    copyFileTo(context, targetFile, false, callback)
}

private fun DocumentFile.copyFileTo(
    context: Context,
    targetFile: MediaFile,
    deleteSourceFileWhenComplete: Boolean,
    callback: FileCallback
) {
    if (!isFile) {
        callback.onFailed(FileCallback.ErrorCode.SOURCE_FILE_NOT_FOUND)
        return
    }

    if (!canRead()) {
        callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
        return
    }

    try {
        if (!callback.onCheckFreeSpace(DocumentFileCompat.getFreeSpace(context, PRIMARY), length())) {
            callback.onFailed(FileCallback.ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH)
            return
        }
    } catch (e: Throwable) {
        callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
        return
    }

    val reportInterval = callback.onStart(this)
    if (reportInterval < 0) return
    val watchProgress = reportInterval > 0

    try {
        createFileStreams(context, this, targetFile, callback) { inputStream, outputStream ->
            copyFileStream(inputStream, outputStream, targetFile, watchProgress, reportInterval, deleteSourceFileWhenComplete, callback)
        }
    } catch (e: SecurityException) {
        callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED)
    } catch (e: InterruptedIOException) {
        callback.onFailed(FileCallback.ErrorCode.CANCELED)
    } catch (e: InterruptedException) {
        callback.onFailed(FileCallback.ErrorCode.CANCELED)
    } catch (e: IOException) {
        callback.onFailed(FileCallback.ErrorCode.UNKNOWN_IO_ERROR)
    }
}

private fun handleFileConflict(
    context: Context,
    targetFolder: DocumentFile,
    targetFileName: String,
    callback: FileCallback
): FileCallback.ConflictResolution {
    targetFolder.findFile(targetFileName)?.let { targetFile ->
        val resolution = runBlocking<FileCallback.ConflictResolution> {
            suspendCancellableCoroutine { continuation ->
                launchOnUiThread {
                    callback.onConflict(targetFile, FileCallback.FileConflictAction(continuation))
                }
            }
        }
        if (resolution == FileCallback.ConflictResolution.REPLACE) {
            val deleteSuccess = targetFile.let { if (it.isDirectory) it.deleteRecursively(context) else it.delete() }
            if (!deleteSuccess || targetFile.exists()) {
                callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
                return FileCallback.ConflictResolution.SKIP
            }
        }
        return resolution
    }
    return FileCallback.ConflictResolution.CREATE_NEW
}

private fun handleParentFolderConflict(
    context: Context,
    targetParentFolder: DocumentFile,
    targetFolderParentName: String,
    callback: FolderCallback
): FolderCallback.ConflictResolution {
    targetParentFolder.findFile(targetFolderParentName)?.let { targetFolder ->
        val canMerge = targetFolder.isDirectory
        if (canMerge && targetFolder.isEmpty(context)) {
            return FolderCallback.ConflictResolution.MERGE
        }

        val resolution = runBlocking<FolderCallback.ConflictResolution> {
            suspendCancellableCoroutine { continuation ->
                launchOnUiThread {
                    callback.onParentConflict(targetFolder, FolderCallback.ParentFolderConflictAction(continuation), canMerge)
                }
            }
        }

        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (resolution) {
            FolderCallback.ConflictResolution.REPLACE -> {
                val isFolder = targetFolder.isDirectory
                val deleteSuccess = targetFolder.let { if (isFolder) it.deleteRecursively(context, true) else (it.delete() || !it.exists()) }
                if (deleteSuccess) {
                    if (!isFolder) {
                        val newFolder = targetFolder.parentFile?.createDirectory(targetFolderParentName)
                        if (newFolder == null) {
                            callback.onFailed(FolderCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
                            return FolderCallback.ConflictResolution.SKIP
                        }
                    }
                } else {
                    callback.onFailed(FolderCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
                    return FolderCallback.ConflictResolution.SKIP
                }
            }

            FolderCallback.ConflictResolution.MERGE -> {
                if (targetFolder.isFile) {
                    if (targetFolder.delete()) {
                        val newFolder = targetFolder.parentFile?.createDirectory(targetFolderParentName)
                        if (newFolder == null) {
                            callback.onFailed(FolderCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
                            return FolderCallback.ConflictResolution.SKIP
                        }
                    } else {
                        callback.onFailed(FolderCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET)
                        return FolderCallback.ConflictResolution.SKIP
                    }
                }
            }
        }
        return resolution
    }
    return FolderCallback.ConflictResolution.CREATE_NEW
}