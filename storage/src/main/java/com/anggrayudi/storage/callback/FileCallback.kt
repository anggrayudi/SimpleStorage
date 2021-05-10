package com.anggrayudi.storage.callback

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.media.MediaFile
import kotlinx.coroutines.CancellableContinuation

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
interface FileCallback {

    @WorkerThread
    fun onPrepare() {
        // default implementation
    }

    /**
     * @param file can be [DocumentFile] or [MediaFile]
     * @return Time interval to watch file copy/move progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the operation.
     */
    @WorkerThread
    fun onStart(file: Any): Long = 0

    /**
     * Do not call `super` when you override this function.
     *
     * The thread that does copy/move will be suspended until the user gives an answer via [FileConflictAction.confirmResolution].
     * You have to give an answer, or the thread will be alive until the app is killed and end up as a zombie thread.
     * If you want to cancel, just pass [ConflictResolution.SKIP] into [FileConflictAction.confirmResolution].
     * If the worker thread is suspended for too long, it may be interrupted by the system.
     */
    @UiThread
    fun onConflict(destinationFile: DocumentFile, action: FileConflictAction) {
        action.confirmResolution(ConflictResolution.CREATE_NEW)
    }

    /**
     * Given `freeSpace` and `fileSize`, then you decide whether the process will be continued or not.
     * You can give space tolerant here, e.g. 100MB
     *
     * @param freeSpace of target path
     * @return `true` to continue process
     */
    @WorkerThread
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
    @WorkerThread
    fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int) {
        // default implementation
    }

    /**
     * @param file newly moved/copied file. Can be [DocumentFile] or [MediaFile]
     */
    @WorkerThread
    fun onCompleted(file: Any) {
        // default implementation
    }

    @WorkerThread
    fun onFailed(errorCode: ErrorCode) {
        // default implementation
    }

    class FileConflictAction(private val continuation: CancellableContinuation<ConflictResolution>) {

        fun confirmResolution(resolution: ConflictResolution) {
            continuation.resumeWith(Result.success(resolution))
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
        SKIP
    }

    enum class ErrorCode {
        STORAGE_PERMISSION_DENIED,
        CANNOT_CREATE_FILE_IN_TARGET,
        SOURCE_FILE_NOT_FOUND,
        TARGET_FILE_NOT_FOUND,
        TARGET_FOLDER_NOT_FOUND,
        UNKNOWN_IO_ERROR,
        CANCELED,
        TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER,
        NO_SPACE_LEFT_ON_TARGET_PATH
    }
}