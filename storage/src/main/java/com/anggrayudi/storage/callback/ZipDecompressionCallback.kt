package com.anggrayudi.storage.callback

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.FileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

/**
 * Created on 02/01/22
 * @author Anggrayudi H
 */
abstract class ZipDecompressionCallback(var uiScope: CoroutineScope = GlobalScope) {

    @UiThread
    open fun onValidate() {
        // default implementation
    }

    /**
     * @param zipFile files to be decompressed
     * @param workerThread Use [Thread.interrupt] to cancel the operation
     * @return Time interval to watch the progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the operation.
     */
    @UiThread
    open fun onStart(zipFile: DocumentFile, workerThread: Thread): Long = 0

    /**
     * Given `freeSpace` and `fileSize`, then you decide whether the process will be continued or not.
     * You can give space tolerant here, e.g. 100MB
     *
     * @param freeSpace of target path
     * @return `true` to continue process
     */
    @WorkerThread
    open fun onCheckFreeSpace(freeSpace: Long, zipFileSize: Long): Boolean {
        // Give tolerant 100MB
        // Estimate the final size of decompressed files is increased by 20%
        return zipFileSize * 1.2 + 100 * FileSize.MB < freeSpace
    }

    @UiThread
    open fun onReport(report: Report) {
        // default implementation
    }

    /**
     * @param decompressionRate size expansion in percent, e.g. 23.5
     */
    @UiThread
    open fun onCompleted(zipFile: DocumentFile, targetFolder: DocumentFile, bytesDecompressed: Long, totalFilesDecompressed: Int, decompressionRate: Float) {
        // default implementation
    }

    @UiThread
    open fun onFailed(errorCode: ErrorCode) {
        // default implementation
    }

    /**
     * Can't calculate write speed, progress and decompressed file size for the given period [onStart],
     * because we can't get the final size of the decompressed files unless we unzip it first,
     * so only `bytesDecompressed` and `fileCount` that can be provided.
     * @param fileCount decompressed files in total
     */
    class Report(val bytesDecompressed: Long, val writeSpeed: Int, val fileCount: Int)

    enum class ErrorCode {
        STORAGE_PERMISSION_DENIED,
        CANNOT_CREATE_FILE_IN_TARGET,
        MISSING_ZIP_FILE,
        NOT_A_ZIP_FILE,
        UNKNOWN_IO_ERROR,
        CANCELED,
        NO_SPACE_LEFT_ON_TARGET_PATH
    }
}