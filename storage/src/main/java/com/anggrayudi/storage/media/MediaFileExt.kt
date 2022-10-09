@file:JvmName("MediaFileUtils")

package com.anggrayudi.storage.media

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.callback.ZipCompressionCallback
import com.anggrayudi.storage.callback.ZipDecompressionCallback
import com.anggrayudi.storage.extension.*
import com.anggrayudi.storage.file.*
import kotlinx.coroutines.Job
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Created on 21/01/22
 * @author Anggrayudi H
 */

@WorkerThread
fun List<MediaFile>.compressToZip(
    context: Context,
    targetZipFile: DocumentFile,
    deleteSourceWhenComplete: Boolean = false,
    callback: ZipCompressionCallback<MediaFile>
) {
    callback.uiScope.postToUi { callback.onCountingFiles() }
    val entryFiles = distinctBy { it.uri }.filter { !it.isEmpty }
    if (entryFiles.isEmpty()) {
        callback.uiScope.postToUi { callback.onFailed(ZipCompressionCallback.ErrorCode.MISSING_ENTRY_FILE, "No entry files found") }
        return
    }

    var zipFile: DocumentFile? = targetZipFile
    if (!targetZipFile.exists() || targetZipFile.isDirectory) {
        zipFile = targetZipFile.findParent(context)?.makeFile(context, targetZipFile.fullName, MimeType.ZIP)
    }
    if (zipFile == null) {
        callback.uiScope.postToUi { callback.onFailed(ZipCompressionCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET) }
        return
    }
    if (!zipFile.isWritable(context)) {
        callback.uiScope.postToUi { callback.onFailed(ZipCompressionCallback.ErrorCode.STORAGE_PERMISSION_DENIED, "Destination ZIP file is not writable") }
        return
    }

    val thread = Thread.currentThread()
    val reportInterval = awaitUiResult(callback.uiScope) { callback.onStart(entryFiles, thread) }
    if (reportInterval < 0) return

    var success = false
    var bytesCompressed = 0L
    var timer: Job? = null
    var zos: ZipOutputStream? = null
    try {
        zos = ZipOutputStream(zipFile.openOutputStream(context))
        var writeSpeed = 0
        var fileCompressedCount = 0
        if (reportInterval > 0) {
            timer = startCoroutineTimer(repeatMillis = reportInterval) {
                val report = ZipCompressionCallback.Report(0f, bytesCompressed, writeSpeed, fileCompressedCount)
                callback.uiScope.postToUi { callback.onReport(report) }
                writeSpeed = 0
            }
        }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        entryFiles.forEach { entry ->
            entry.openInputStream()?.use { input ->
                zos.putNextEntry(ZipEntry(entry.fullName))
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    zos.write(buffer, 0, bytes)
                    bytesCompressed += bytes
                    writeSpeed += bytes
                    bytes = input.read(buffer)
                }
            } ?: throw FileNotFoundException("File ${entry.fullName} is not found")
            fileCompressedCount++
        }
        success = true
    } catch (e: InterruptedIOException) {
        callback.uiScope.postToUi { callback.onFailed(ZipCompressionCallback.ErrorCode.CANCELED) }
    } catch (e: FileNotFoundException) {
        callback.uiScope.postToUi { callback.onFailed(ZipCompressionCallback.ErrorCode.MISSING_ENTRY_FILE, e.message) }
    } catch (e: IOException) {
        if (e.message?.contains("no space", true) == true) {
            callback.uiScope.postToUi { callback.onFailed(ZipCompressionCallback.ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH) }
        } else {
            callback.uiScope.postToUi { callback.onFailed(ZipCompressionCallback.ErrorCode.UNKNOWN_IO_ERROR) }
        }
    } catch (e: SecurityException) {
        callback.uiScope.postToUi { callback.onFailed(ZipCompressionCallback.ErrorCode.STORAGE_PERMISSION_DENIED, e.message) }
    } finally {
        timer?.cancel()
        zos.closeEntryQuietly()
        zos.closeStreamQuietly()
    }
    if (success) {
        if (deleteSourceWhenComplete) {
            callback.uiScope.postToUi { callback.onDeleteEntryFiles() }
            forEach { it.delete() }
        }
        val sizeReduction = (bytesCompressed - zipFile.length()).toFloat() / bytesCompressed * 100
        callback.uiScope.postToUi { callback.onCompleted(zipFile, bytesCompressed, entryFiles.size, sizeReduction) }
    } else {
        zipFile.delete()
    }
}

@WorkerThread
fun MediaFile.decompressZip(
    context: Context,
    targetFolder: DocumentFile,
    callback: ZipDecompressionCallback<MediaFile>
) {
    callback.uiScope.postToUi { callback.onValidate() }
    if (isEmpty) {
        callback.uiScope.postToUi { callback.onFailed(ZipDecompressionCallback.ErrorCode.MISSING_ZIP_FILE) }
        return
    }
    if (mimeType != MimeType.ZIP) {
        callback.uiScope.postToUi { callback.onFailed(ZipDecompressionCallback.ErrorCode.NOT_A_ZIP_FILE) }
        return
    }

    var destFolder: DocumentFile? = targetFolder
    if (!targetFolder.exists() || targetFolder.isFile) {
        destFolder = targetFolder.findParent(context)?.makeFolder(context, targetFolder.fullName)
    }
    if (destFolder == null || !destFolder.isWritable(context)) {
        callback.uiScope.postToUi { callback.onFailed(ZipDecompressionCallback.ErrorCode.STORAGE_PERMISSION_DENIED) }
        return
    }

    val thread = Thread.currentThread()
    val reportInterval = awaitUiResult(callback.uiScope) { callback.onStart(this, thread) }
    if (reportInterval < 0) return

    var success = false
    var bytesDecompressed = 0L
    var skippedDecompressedBytes = 0L
    var fileDecompressedCount = 0
    var timer: Job? = null
    var zis: ZipInputStream? = null
    var targetFile: DocumentFile? = null
    try {
        zis = ZipInputStream(openInputStream())
        var writeSpeed = 0
        if (reportInterval > 0) {
            timer = startCoroutineTimer(repeatMillis = reportInterval) {
                val report = ZipDecompressionCallback.Report(bytesDecompressed, writeSpeed, fileDecompressedCount)
                callback.uiScope.postToUi { callback.onReport(report) }
                writeSpeed = 0
            }
        }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entry = zis.nextEntry
        var canSuccess = true
        while (entry != null) {
            if (entry.isDirectory) {
                destFolder.makeFolder(context, entry.name, CreateMode.REUSE)
            } else {
                val folder = entry.name.substringBeforeLast('/', "").let {
                    if (it.isEmpty()) destFolder else destFolder.makeFolder(context, it, CreateMode.REUSE)
                } ?: throw IOException()
                val fileName = entry.name.substringAfterLast('/')
                targetFile = folder.makeFile(context, fileName)
                if (targetFile == null) {
                    callback.uiScope.postToUi { callback.onFailed(ZipDecompressionCallback.ErrorCode.CANNOT_CREATE_FILE_IN_TARGET) }
                    canSuccess = false
                    break
                }
                if (targetFile.length() > 0 && targetFile.isFile) {
                    // user has selected 'SKIP'
                    skippedDecompressedBytes += targetFile.length()
                    entry = zis.nextEntry
                    continue
                }
                targetFile.openOutputStream(context)?.use { output ->
                    var bytes = zis.read(buffer)
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesDecompressed += bytes
                        writeSpeed += bytes
                        bytes = zis.read(buffer)
                    }
                } ?: throw IOException()
                fileDecompressedCount++
            }
            entry = zis.nextEntry
        }
        success = canSuccess
    } catch (e: InterruptedIOException) {
        callback.uiScope.postToUi { callback.onFailed(ZipDecompressionCallback.ErrorCode.CANCELED) }
    } catch (e: FileNotFoundException) {
        callback.uiScope.postToUi { callback.onFailed(ZipDecompressionCallback.ErrorCode.MISSING_ZIP_FILE) }
    } catch (e: IOException) {
        if (e.message?.contains("no space", true) == true) {
            callback.uiScope.postToUi { callback.onFailed(ZipDecompressionCallback.ErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH) }
        } else {
            callback.uiScope.postToUi { callback.onFailed(ZipDecompressionCallback.ErrorCode.UNKNOWN_IO_ERROR) }
        }
    } catch (e: SecurityException) {
        callback.uiScope.postToUi { callback.onFailed(ZipDecompressionCallback.ErrorCode.STORAGE_PERMISSION_DENIED) }
    } finally {
        timer?.cancel()
        zis.closeEntryQuietly()
        zis.closeStreamQuietly()
    }
    if (success) {
        val info = ZipDecompressionCallback.DecompressionInfo(bytesDecompressed, skippedDecompressedBytes, fileDecompressedCount, 0f)
        callback.uiScope.postToUi { callback.onCompleted(this, destFolder, info) }
    } else {
        targetFile?.delete()
    }
}