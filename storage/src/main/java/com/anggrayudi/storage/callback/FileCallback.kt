package com.anggrayudi.storage.callback

import androidx.annotation.UiThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.ErrorCode
import com.anggrayudi.storage.media.MediaFile
import kotlinx.coroutines.CancellableContinuation

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
interface FileCallback {

    /**
     * @param file can be [DocumentFile] or [MediaFile]
     * @return Time interval to watch file copy/move progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the action.
     */
    @JvmDefault
    fun onStart(file: Any): Long = 0

    /**
     * @return suspend the thread that doing copy/move at fixed time in milliseconds until user confirmed an action.
     *         Do not let the thread to be suspended for too long. We suggest max timeout 15 seconds.
     */
    @UiThread
    @JvmDefault
    fun onConflict(destinationFile: DocumentFile, action: FileConflictAction): Long {
        action.confirmResolution(ConflictResolution.CREATE_NEW)
        return FileConflictAction.DEFAULT_CONFIRMATION_TIMEOUT
    }

    /**
     * Given `freeSpace` and `fileSize`, then you decide whether the process will be continued or not.
     * You can give space tolerant here, e.g. 100MB
     *
     * @param freeSpace of target path
     * @return `true` to continue process
     */
    @JvmDefault
    fun onCheckFreeSpace(freeSpace: Long, fileSize: Long): Boolean {
        // default implementation
        return true
    }

    /**
     * Only called if the returned [onStart] greater than `0`
     *
     * @param progress   in percent
     * @param writeSpeed in bytes
     */
    @JvmDefault
    fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int) {
        // default implementation
    }

    /**
     * @param file can be [DocumentFile] or [MediaFile]
     * @param file newly moved/copied file
     */
    @JvmDefault
    fun onCompleted(file: Any) {
        // default implementation
    }

    @JvmDefault
    fun onFailed(errorCode: ErrorCode) {
        // default implementation
    }

    class FileConflictAction(private val continuation: CancellableContinuation<ConflictResolution>) {

        fun confirmResolution(resolution: ConflictResolution) {
            continuation.resumeWith(Result.success(resolution))
        }

        companion object {
            const val DEFAULT_CONFIRMATION_TIMEOUT = 10_000L
        }
    }

    enum class ConflictResolution {
        /**
         * Delete the file in destination if existed, then start copy/move.
         */
        REPLACE,

        /**
         * * If a file named `ABC.zip` already exist, then create a new one named `ABC (1).zip`
         */
        CREATE_NEW,

        /**
         * Cancel copy/move.
         */
        SKIP_DUPLICATE
    }
}