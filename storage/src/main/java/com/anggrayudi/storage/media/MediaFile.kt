package com.anggrayudi.storage.media

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.extension.*
import com.anggrayudi.storage.file.*
import com.anggrayudi.storage.file.DocumentFileCompat.removeForbiddenCharsFromFilename
import kotlinx.coroutines.Job
import java.io.*

/**
 * Created on 06/09/20
 * @author Anggrayudi H
 */
@Suppress("DEPRECATION")
class MediaFile(context: Context, val uri: Uri) {

    constructor(context: Context, rawFile: File) : this(context, Uri.fromFile(rawFile))

    private val context = context.applicationContext

    interface AccessCallback {

        /**
         * When this function called, you can ask user's concent to modify other app's files.
         * @see RecoverableSecurityException
         * @see [android.app.Activity.startIntentSenderForResult]
         */
        fun onWriteAccessDenied(mediaFile: MediaFile, sender: IntentSender)
    }

    /**
     * Only useful for Android 10 and higher.
     * @see RecoverableSecurityException
     */
    var accessCallback: AccessCallback? = null

    /**
     * Some media files do not return file extension. This function helps you to fix this kind of issue.
     */
    val fullName: String
        get() = if (isRawFile) {
            toRawFile()?.name.orEmpty()
        } else {
            val mimeType = getColumnInfoString(MediaStore.MediaColumns.MIME_TYPE)
            val displayName = getColumnInfoString(MediaStore.MediaColumns.DISPLAY_NAME).orEmpty()
            MimeType.getFullFileName(displayName, mimeType)
        }

    /**
     * @see [fullName]
     */
    val name: String?
        get() = toRawFile()?.name ?: getColumnInfoString(MediaStore.MediaColumns.DISPLAY_NAME)

    val baseName: String
        get() = fullName.substringBeforeLast('.')

    val extension: String
        get() = fullName.substringAfterLast('.', "")

    /**
     * @see [mimeType]
     */
    val type: String?
        get() = toRawFile()?.name?.let { MimeType.getMimeTypeFromExtension(it.substringAfterLast('.', "")) }
            ?: getColumnInfoString(MediaStore.MediaColumns.MIME_TYPE)

    /**
     * Advanced version of [type]. Returns:
     * * `null` if the file does not exist
     * * [MimeType.UNKNOWN] if the file exists but the mime type is not found
     */
    val mimeType: String?
        get() = if (exists) {
            getColumnInfoString(MediaStore.MediaColumns.MIME_TYPE)
                ?: MimeType.getMimeTypeFromExtension(extension)
        } else null

    var length: Long
        get() = toRawFile()?.length() ?: getColumnInfoLong(MediaStore.MediaColumns.SIZE)
        set(value) {
            try {
                val contentValues = ContentValues(1).apply { put(MediaStore.MediaColumns.SIZE, value) }
                context.contentResolver.update(uri, contentValues, null, null)
            } catch (e: SecurityException) {
                handleSecurityException(e)
            }
        }

    /**
     * Check if file exists
     */
    val exists: Boolean
        get() = toRawFile()?.exists()
            ?: uri.openInputStream(context)?.use { true }
            ?: false

    /**
     * The URI presents in SAF database, but the file is not found.
     */
    val isEmpty: Boolean
        get() = context.contentResolver.query(uri, null, null, null, null)?.use {
            it.count > 0 && !exists
        } ?: false

    /**
     * `true` if this file was created with [File]. Only works on API 28 and lower.
     */
    val isRawFile: Boolean
        get() = uri.isRawFile

    val lastModified: Long
        get() = toRawFile()?.lastModified()
            ?: getColumnInfoLong(MediaStore.MediaColumns.DATE_MODIFIED)

    val owner: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getColumnInfoString(MediaStore.MediaColumns.OWNER_PACKAGE_NAME) else null

    /**
     * Check if this media is owned by your app.
     */
    val isMine: Boolean
        get() = owner == context.packageName

    /**
     * @return `null` in Android 10+ or if you try to read files from SD Card or you want to convert a file picked
     * from [Intent.ACTION_OPEN_DOCUMENT] or [Intent.ACTION_CREATE_DOCUMENT].
     * @see toDocumentFile
     */
    @Deprecated("Accessing files with java.io.File only works on app private directory since Android 10.")
    fun toRawFile() = if (isRawFile) uri.path?.let { File(it) } else null

    fun toDocumentFile() = absolutePath.let { if (it.isEmpty()) null else DocumentFileCompat.fromFullPath(context, it) }

    val absolutePath: String
        @SuppressLint("InlinedApi")
        get() {
            val file = toRawFile()
            return when {
                file != null -> file.path
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                    try {
                        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA))
                            } else ""
                        }.orEmpty()
                    } catch (e: Exception) {
                        ""
                    }
                }
                else -> {
                    val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val relativePath = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)) ?: return ""
                            val name = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))
                            "${SimpleStorage.externalStoragePath}/$relativePath/$name".trimEnd('/').replaceCompletely("//", "/")
                        } else ""
                    }.orEmpty()
                }
            }
        }

    val basePath: String
        get() = absolutePath.substringAfter(SimpleStorage.externalStoragePath).trimFileSeparator()

    /**
     * @see MediaStore.MediaColumns.RELATIVE_PATH
     */
    val relativePath: String
        @SuppressLint("InlinedApi")
        get() {
            val file = toRawFile()
            return when {
                file != null -> {
                    file.path.substringBeforeLast('/').replaceFirst(SimpleStorage.externalStoragePath, "").trimFileSeparator() + "/"
                }
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                    try {
                        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val realFolderAbsolutePath =
                                    cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA)).substringBeforeLast('/')
                                realFolderAbsolutePath.replaceFirst(SimpleStorage.externalStoragePath, "").trimFileSeparator() + "/"
                            } else ""
                        }.orEmpty()
                    } catch (e: Exception) {
                        ""
                    }
                }
                else -> {
                    val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH))
                        } else ""
                    }.orEmpty()
                }
            }
        }

    fun delete(): Boolean {
        val file = toRawFile()
        return if (file != null) {
            file.delete() || !file.exists()
        } else try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: SecurityException) {
            handleSecurityException(e)
            false
        }
    }

    /**
     * Please note that this function does not move file if you input `newName` as `Download/filename.mp4`.
     * If you want to move media files, please use [moveFileTo] instead.
     */
    fun renameTo(newName: String): Boolean {
        val file = toRawFile()
        val contentValues = ContentValues(1).apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        return if (file != null) {
            file.renameTo(File(file.parent, newName)) && context.contentResolver.update(uri, contentValues, null, null) > 0
        } else try {
            context.contentResolver.update(uri, contentValues, null, null) > 0
        } catch (e: SecurityException) {
            handleSecurityException(e)
            false
        }
    }

    /**
     * Set to `true` if the file is being written to prevent users from accessing it.
     * @see MediaStore.MediaColumns.IS_PENDING
     */
    var isPending: Boolean
        @RequiresApi(Build.VERSION_CODES.Q)
        get() = getColumnInfoInt(MediaStore.MediaColumns.IS_PENDING) == 1
        @RequiresApi(Build.VERSION_CODES.Q)
        set(value) {
            val contentValues = ContentValues(1).apply { put(MediaStore.MediaColumns.IS_PENDING, value.toInt()) }
            try {
                context.contentResolver.update(uri, contentValues, null, null)
            } catch (e: SecurityException) {
                handleSecurityException(e)
            }
        }

    private fun handleSecurityException(e: SecurityException, callback: FileCallback? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            accessCallback?.onWriteAccessDenied(this, e.userAction.actionIntent.intentSender)
        } else {
            callback?.uiScope?.postToUi { callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED) }
        }
    }

    @UiThread
    fun openFileIntent(authority: String) = Intent(Intent.ACTION_VIEW)
        .setData(if (isRawFile) FileProvider.getUriForFile(context, authority, File(uri.path!!)) else uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * @param append if `false` and the file already exists, it will recreate the file.
     */
    @WorkerThread
    @JvmOverloads
    fun openOutputStream(append: Boolean = true): OutputStream? {
        return try {
            val file = toRawFile()
            if (file != null) {
                FileOutputStream(file, append)
            } else {
                context.contentResolver.openOutputStream(uri, if (append) "wa" else "w")
            }
        } catch (e: IOException) {
            null
        }
    }

    @WorkerThread
    fun openInputStream(): InputStream? {
        return try {
            val file = toRawFile()
            if (file != null) {
                FileInputStream(file)
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: IOException) {
            null
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    fun moveTo(relativePath: String): Boolean {
        val contentValues = ContentValues(1).apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
        return try {
            context.contentResolver.update(uri, contentValues, null, null) > 0
        } catch (e: SecurityException) {
            handleSecurityException(e)
            false
        }
    }

    @WorkerThread
    fun moveTo(targetFolder: DocumentFile, fileDescription: FileDescription? = null, callback: FileCallback) {
        val sourceFile = toDocumentFile()
        if (sourceFile != null) {
            sourceFile.moveFileTo(context, targetFolder, fileDescription, callback)
            return
        }

        try {
            if (!callback.onCheckFreeSpace(DocumentFileCompat.getFreeSpace(context, targetFolder.getStorageId(context)), length)) {
                callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH) }
                return
            }
        } catch (e: Throwable) {
            callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED) }
            return
        }

        val targetDirectory = if (fileDescription?.subFolder.isNullOrEmpty()) {
            targetFolder
        } else {
            val directory = targetFolder.makeFolder(context, fileDescription?.subFolder.orEmpty(), CreateMode.REUSE)
            if (directory == null) {
                callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET) }
                return
            } else {
                directory
            }
        }

        val cleanFileName = MimeType.getFullFileName(fileDescription?.name ?: name.orEmpty(), fileDescription?.mimeType ?: type)
            .removeForbiddenCharsFromFilename().trimFileSeparator()
        val conflictResolution = handleFileConflict(targetDirectory, cleanFileName, callback)
        if (conflictResolution == FileCallback.ConflictResolution.SKIP) {
            return
        }

        val thread = Thread.currentThread()
        val reportInterval = awaitUiResult(callback.uiScope) { callback.onStart(this, thread) }
        val watchProgress = reportInterval > 0

        try {
            val targetFile = createTargetFile(
                targetDirectory, cleanFileName, fileDescription?.mimeType ?: type,
                conflictResolution.toCreateMode(), callback
            ) ?: return
            createFileStreams(targetFile, callback) { inputStream, outputStream ->
                copyFileStream(inputStream, outputStream, targetFile, watchProgress, reportInterval, true, callback)
            }
        } catch (e: SecurityException) {
            handleSecurityException(e, callback)
        } catch (e: Exception) {
            callback.uiScope.postToUi { callback.onFailed(e.toFileCallbackErrorCode()) }
        }
    }

    @WorkerThread
    fun copyTo(targetFolder: DocumentFile, fileDescription: FileDescription? = null, callback: FileCallback) {
        val sourceFile = toDocumentFile()
        if (sourceFile != null) {
            sourceFile.copyFileTo(context, targetFolder, fileDescription, callback)
            return
        }

        try {
            if (!callback.onCheckFreeSpace(DocumentFileCompat.getFreeSpace(context, targetFolder.getStorageId(context)), length)) {
                callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH) }
                return
            }
        } catch (e: Throwable) {
            callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED) }
            return
        }

        val targetDirectory = if (fileDescription?.subFolder.isNullOrEmpty()) {
            targetFolder
        } else {
            val directory = targetFolder.makeFolder(context, fileDescription?.subFolder.orEmpty(), CreateMode.REUSE)
            if (directory == null) {
                callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET) }
                return
            } else {
                directory
            }
        }

        val cleanFileName = MimeType.getFullFileName(fileDescription?.name ?: name.orEmpty(), fileDescription?.mimeType ?: type)
            .removeForbiddenCharsFromFilename().trimFileSeparator()
        val conflictResolution = handleFileConflict(targetDirectory, cleanFileName, callback)
        if (conflictResolution == FileCallback.ConflictResolution.SKIP) {
            return
        }

        val thread = Thread.currentThread()
        val reportInterval = awaitUiResult(callback.uiScope) { callback.onStart(this, thread) }
        val watchProgress = reportInterval > 0
        try {
            val targetFile = createTargetFile(
                targetDirectory, cleanFileName, fileDescription?.mimeType ?: type,
                conflictResolution.toCreateMode(), callback
            ) ?: return
            createFileStreams(targetFile, callback) { inputStream, outputStream ->
                copyFileStream(inputStream, outputStream, targetFile, watchProgress, reportInterval, false, callback)
            }
        } catch (e: SecurityException) {
            handleSecurityException(e, callback)
        } catch (e: Exception) {
            callback.uiScope.postToUi { callback.onFailed(e.toFileCallbackErrorCode()) }
        }
    }

    private fun createTargetFile(
        targetDirectory: DocumentFile,
        fileName: String,
        mimeType: String?,
        mode: CreateMode,
        callback: FileCallback
    ): DocumentFile? {
        try {
            val absolutePath = DocumentFileCompat.buildAbsolutePath(context, targetDirectory.getStorageId(context), targetDirectory.getBasePath(context))
            val targetFolder = DocumentFileCompat.mkdirs(context, absolutePath)
            if (targetFolder == null) {
                callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.STORAGE_PERMISSION_DENIED) }
                return null
            }

            val targetFile = targetFolder.makeFile(context, fileName, mimeType, mode)
            if (targetFile == null) {
                callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET) }
            } else {
                return targetFile
            }
        } catch (e: SecurityException) {
            handleSecurityException(e, callback)
        } catch (e: Exception) {
            callback.uiScope.postToUi { callback.onFailed(e.toFileCallbackErrorCode()) }
        }
        return null
    }

    private inline fun createFileStreams(
        targetFile: DocumentFile,
        callback: FileCallback,
        onStreamsReady: (InputStream, OutputStream) -> Unit
    ) {
        val outputStream = targetFile.openOutputStream(context)
        if (outputStream == null) {
            callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.TARGET_FILE_NOT_FOUND) }
            return
        }

        val inputStream = openInputStream()
        if (inputStream == null) {
            callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.SOURCE_FILE_NOT_FOUND) }
            outputStream.closeStream()
            return
        }

        onStreamsReady(inputStream, outputStream)
    }

    private fun copyFileStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        targetFile: DocumentFile,
        watchProgress: Boolean,
        reportInterval: Long,
        deleteSourceFileWhenComplete: Boolean,
        callback: FileCallback
    ) {
        var timer: Job? = null
        try {
            var bytesMoved = 0L
            var writeSpeed = 0
            val srcSize = length
            // using timer on small file is useless. We set minimum 10MB.
            if (watchProgress && srcSize > 10 * FileSize.MB) {
                timer = startCoroutineTimer(repeatMillis = reportInterval) {
                    val report = FileCallback.Report(bytesMoved * 100f / srcSize, bytesMoved, writeSpeed)
                    callback.uiScope.postToUi { callback.onReport(report) }
                    writeSpeed = 0
                }
            }
            val buffer = ByteArray(1024)
            var read = inputStream.read(buffer)
            while (read != -1) {
                outputStream.write(buffer, 0, read)
                bytesMoved += read
                writeSpeed += read
                read = inputStream.read(buffer)
            }
            timer?.cancel()
            if (deleteSourceFileWhenComplete) {
                delete()
            }
            callback.uiScope.postToUi { callback.onCompleted(targetFile) }
        } finally {
            timer?.cancel()
            inputStream.closeStream()
            outputStream.closeStream()
        }
    }

    private fun handleFileConflict(
        targetFolder: DocumentFile,
        fileName: String,
        callback: FileCallback
    ): FileCallback.ConflictResolution {
        targetFolder.findFile(fileName)?.let { targetFile ->
            val resolution = awaitUiResultWithPending<FileCallback.ConflictResolution>(callback.uiScope) {
                callback.onConflict(targetFile, FileCallback.FileConflictAction(it))
            }
            if (resolution == FileCallback.ConflictResolution.REPLACE) {
                if (!targetFile.forceDelete(context)) {
                    callback.uiScope.postToUi { callback.onFailed(FileCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET) }
                    return FileCallback.ConflictResolution.SKIP
                }
            }
            return resolution
        }
        return FileCallback.ConflictResolution.CREATE_NEW
    }

    private fun getColumnInfoString(column: String): String? {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(column)
                if (columnIndex != -1) {
                    return cursor.getString(columnIndex)
                }
            }
        }
        return null
    }

    private fun getColumnInfoLong(column: String): Long {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(column)
                if (columnIndex != -1) {
                    return cursor.getLong(columnIndex)
                }
            }
        }
        return 0
    }

    private fun getColumnInfoInt(column: String): Int {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(column)
                if (columnIndex != -1) {
                    return cursor.getInt(columnIndex)
                }
            }
        }
        return 0
    }

    override fun equals(other: Any?) = other === this || other is MediaFile && other.uri == uri

    override fun hashCode() = uri.hashCode()

    override fun toString() = uri.toString()
}