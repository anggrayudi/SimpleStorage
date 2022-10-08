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
abstract class ZipDecompressionCallback<T> @OptIn(DelicateCoroutinesApi::class) @JvmOverloads constructor(
    uiScope: CoroutineScope = GlobalScope
) : FileConflictCallback<DocumentFile>(uiScope) {

    @UiThread
    open fun onValidate() {
        // default implementation
    }

    /**
     * @param zipFile files to be decompressed. Can be [DocumentFile] or [MediaFile]
     * @param workerThread Use [Thread.interrupt] to cancel the operation
     * @return Time interval to watch the progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the operation.
     */
    @UiThread
    open fun onStart(zipFile: T, workerThread: Thread): Long = 0

    /**
     * Given `freeSpace` and `fileSize`, then you decide whether the process will be continued or not.
     * You can give space tolerant here, e.g. 100MB.
     * This function will not be triggered when decompressing [MediaFile].
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
     * @param zipFile can be [DocumentFile] or [MediaFile]
     * But for decompressing [MediaFile], it is always `0` because we can't get the actual zip file size from SAF database.
     */
    @UiThread
    open fun onCompleted(zipFile: T, targetFolder: DocumentFile, decompressionInfo: DecompressionInfo) {
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

    /**
     * @param decompressionRate size expansion in percent, e.g. 23.5.
     * @param skippedDecompressedBytes total skipped bytes because the file already exists and the user has selected [FileCallback.ConflictResolution.SKIP]
     * @param bytesDecompressed total decompressed bytes, excluded skipped files
     */
    class DecompressionInfo(val bytesDecompressed: Long, val skippedDecompressedBytes: Long, val totalFilesDecompressed: Int, val decompressionRate: Float)

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