package com.anggrayudi.storage.callback

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.callback.FileCallback.FileConflictAction
import com.anggrayudi.storage.file.FileSize
import kotlinx.coroutines.CancellableContinuation

/**
 * Created on 3/1/21
 * @author Anggrayudi H
 */
interface FolderCallback {

    @WorkerThread
    fun onPrepare() {
        // default implementation
    }

    @WorkerThread
    fun onCountingFiles() {
        // default implementation
    }

    /**
     * @param folder directory to be copied/moved
     * @return Time interval to watch folder copy/move progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the operation.
     */
    @WorkerThread
    fun onStart(folder: DocumentFile, totalFilesToCopy: Int): Long = 0

    /**
     * Do not call `super` when you override this function.
     *
     * The thread that does copy/move will be suspended until the user gives an answer via [FileConflictAction.confirmResolution].
     * You have to give an answer, or the thread will be alive until the app is killed and end up as a zombie thread.
     * If you want to cancel, just pass [ConflictResolution.SKIP] into [FileConflictAction.confirmResolution].
     * If the worker thread is suspended for too long, it may be interrupted by the system.
     *
     * @param canMerge when conflict found, action `MERGE` may not exists.
     *                 This happens if the destination is a file.
     */
    @UiThread
    fun onParentConflict(destinationFolder: DocumentFile, action: ParentFolderConflictAction, canMerge: Boolean) {
        action.confirmResolution(ConflictResolution.CREATE_NEW)
    }

    @UiThread
    fun onContentConflict(destinationFolder: DocumentFile, conflictedFiles: MutableList<FileConflict>, action: FolderContentConflictAction) {
        action.confirmResolution(conflictedFiles)
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
        return fileSize + 100 * FileSize.MB < freeSpace // Give tolerant 100MB
    }

    /**
     * Only called if the returned [onStart] greater than `0`
     *
     * @param progress   in percent
     * @param writeSpeed in bytes
     * @param fileCount total files/folders that are successfully copied/moved
     */
    @WorkerThread
    fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int, fileCount: Int) {
        // default implementation
    }

    /**
     * If `totalCopiedFiles` are less than `totalFilesToCopy`, then some files cannot be copied/moved or the files are skipped due to [ConflictResolution.MERGE]
     * [onFailed] can be called before [onCompleted] when an error has occurred.
     * @param folder newly moved/copied file
     * @param success `true` if the process is not canceled and no error during copy/move
     * @param totalFilesToCopy total files, not folders
     * @param totalCopiedFiles total files, not folders
     */
    @WorkerThread
    fun onCompleted(folder: DocumentFile, totalFilesToCopy: Int, totalCopiedFiles: Int, success: Boolean) {
        // default implementation
    }

    @WorkerThread
    fun onFailed(errorCode: ErrorCode) {
        // default implementation
    }

    class ParentFolderConflictAction(private val continuation: CancellableContinuation<ConflictResolution>) {

        fun confirmResolution(resolution: ConflictResolution) {
            continuation.resumeWith(Result.success(resolution))
        }
    }

    class FolderContentConflictAction(private val continuation: CancellableContinuation<List<FileConflict>>) {

        fun confirmResolution(resolutions: List<FileConflict>) {
            continuation.resumeWith(Result.success(resolutions))
        }
    }

    enum class ConflictResolution {
        /**
         * Delete the folder in destination if existed, then start copy/move.
         */
        REPLACE,

        /**
         * Skip duplicate files inside the folder.
         */
        MERGE,

        /**
         * * If a folder named `ABC` already exist, then create a new one named `ABC (1)`
         * * If the folder is empty, just use it.
         */
        CREATE_NEW,

        /**
         * Cancel copy/move.
         */
        SKIP
    }

    class FileConflict(
        val source: DocumentFile,
        val target: DocumentFile,
        var solution: Solution = Solution.CREATE_NEW
    ) {
        enum class Solution {
            ACCEPT_SOURCE,
            ACCEPT_TARGET,
            CREATE_NEW
        }
    }

    enum class ErrorCode {
        STORAGE_PERMISSION_DENIED,
        CANNOT_CREATE_FILE_IN_TARGET,
        SOURCE_FOLDER_NOT_FOUND,
        SOURCE_FILE_NOT_FOUND,
        INVALID_TARGET_FOLDER,
        UNKNOWN_IO_ERROR,
        CANCELED,
        TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER,
        NO_SPACE_LEFT_ON_TARGET_PATH
    }
}