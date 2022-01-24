package com.anggrayudi.storage.callback

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.FileSize
import com.anggrayudi.storage.media.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

/**
 * Created on 02/01/22
 * @author Anggrayudi H
 */
abstract class ZipCompressionCallback<T> @OptIn(DelicateCoroutinesApi::class)
@JvmOverloads constructor(var uiScope: CoroutineScope = GlobalScope) {

    @UiThread
    open fun onCountingFiles() {
        // default implementation
    }

    /**
     * @param files files to be compressed. Can be `List<DocumentFile>` or `List<MediaFile>`
     * @param workerThread Use [Thread.interrupt] to cancel the operation
     * @return Time interval to watch the progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the operation.
     */
    @UiThread
    open fun onStart(files: List<T>, workerThread: Thread): Long = 0

    /**
     * Given `freeSpace` and `fileSize`, then you decide whether the process will be continued or not.
     * You can give space tolerant here, e.g. 100MB.
     * This function will not be triggered when compressing [MediaFile].
     *
     * @param freeSpace of target path
     * @return `true` to continue process
     */
    @WorkerThread
    open fun onCheckFreeSpace(freeSpace: Long, fileSize: Long): Boolean {
        return fileSize + 100 * FileSize.MB < freeSpace // Give tolerant 100MB
    }

    @UiThread
    open fun onReport(report: Report) {
        // default implementation
    }

    @UiThread
    open fun onDeleteEntryFiles() {
        // default implementation
    }

    /**
     * @param compressionRate size reduction in percent, e.g. 23.5
     */
    @UiThread
    open fun onCompleted(zipFile: DocumentFile, bytesCompressed: Long, totalFilesCompressed: Int, compressionRate: Float) {
        // default implementation
    }

    @UiThread
    open fun onFailed(errorCode: ErrorCode, message: String? = null) {
        // default implementation
    }

    /**
     * @param progress always `0` when compressing [MediaFile]
     */
    class Report(val progress: Float, val bytesCompressed: Long, val writeSpeed: Int, val fileCount: Int)

    enum class ErrorCode {
        STORAGE_PERMISSION_DENIED,
        CANNOT_CREATE_FILE_IN_TARGET,
        MISSING_ENTRY_FILE,
        DUPLICATE_ENTRY_FILE,
        UNKNOWN_IO_ERROR,
        CANCELED,
        NO_SPACE_LEFT_ON_TARGET_PATH
    }
}