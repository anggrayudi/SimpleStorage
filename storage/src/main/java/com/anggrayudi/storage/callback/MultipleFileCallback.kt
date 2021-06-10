package com.anggrayudi.storage.callback

import androidx.annotation.UiThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.callback.FileCallback.FileConflictAction
import com.anggrayudi.storage.callback.FolderCallback.ConflictResolution
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

/**
 * Created on 31/05/21
 * @author Anggrayudi H
 */
abstract class MultipleFileCallback @JvmOverloads constructor(
    uiScope: CoroutineScope = GlobalScope
) : BaseFileCallback<MultipleFileCallback.ErrorCode, MultipleFileCallback.Report, MultipleFileCallback.Result>(uiScope) {

    /**
     * The reason can be one of:
     * * [FolderCallback.ErrorCode.SOURCE_FILE_NOT_FOUND]
     * * [FolderCallback.ErrorCode.STORAGE_PERMISSION_DENIED]
     * * [FolderCallback.ErrorCode.TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER]
     */
    @UiThread
    open fun onInvalidSourceFilesFound(invalidSourceFiles: Map<DocumentFile, FolderCallback.ErrorCode>, action: InvalidSourceFilesAction) {
        action.confirmResolution(false)
    }

    @UiThread
    open fun onCountingFiles() {
        // default implementation
    }

    /**
     * @param files directories/files to be copied/moved
     * @return Time interval to watch folder copy/move progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the operation.
     */
    @UiThread
    open fun onStart(files: List<DocumentFile>, totalFilesToCopy: Int, workerThread: Thread): Long = 0

    /**
     * Do not call `super` when you override this function.
     *
     * The thread that does copy/move will be suspended until the user gives an answer via [FileConflictAction.confirmResolution].
     * You have to give an answer, or the thread will be alive until the app is killed and end up as a zombie thread.
     * If you want to cancel, just pass [ConflictResolution.SKIP] into [FileConflictAction.confirmResolution].
     * If the worker thread is suspended for too long, it may be interrupted by the system.
     */
    @UiThread
    open fun onParentConflict(
        destinationParentFolder: DocumentFile,
        conflictedFolders: MutableList<ParentConflict>,
        conflictedFiles: MutableList<ParentConflict>,
        action: ParentFolderConflictAction
    ) {
        action.confirmResolution(conflictedFiles)
    }

    @UiThread
    open fun onContentConflict(
        destinationParentFolder: DocumentFile,
        conflictedFiles: MutableList<FolderCallback.FileConflict>,
        action: FolderCallback.FolderContentConflictAction
    ) {
        action.confirmResolution(conflictedFiles)
    }

    class InvalidSourceFilesAction(private val continuation: CancellableContinuation<Boolean>) {

        /**
         * @param abort stop the process
         */
        fun confirmResolution(abort: Boolean) {
            continuation.resumeWith(kotlin.Result.success(abort))
        }
    }

    class ParentFolderConflictAction(private val continuation: CancellableContinuation<List<ParentConflict>>) {

        fun confirmResolution(resolution: List<ParentConflict>) {
            continuation.resumeWith(kotlin.Result.success(resolution))
        }
    }

    class ParentConflict(
        val source: DocumentFile,
        val target: DocumentFile,
        val canMerge: Boolean,
        var solution: ConflictResolution = ConflictResolution.CREATE_NEW
    )

    enum class ErrorCode {
        STORAGE_PERMISSION_DENIED,
        CANNOT_CREATE_FILE_IN_TARGET,
        SOURCE_FILE_NOT_FOUND,
        INVALID_TARGET_FOLDER,
        UNKNOWN_IO_ERROR,
        CANCELED,
        NO_SPACE_LEFT_ON_TARGET_PATH
    }

    /**
     * Only called if the returned [onStart] greater than `0`
     *
     * @param progress   in percent
     * @param writeSpeed in bytes
     * @param fileCount total files/folders that are successfully copied/moved
     */
    class Report(val progress: Float, val bytesMoved: Long, val writeSpeed: Int, val fileCount: Int)

    /**
     * If `totalCopiedFiles` are less than `totalFilesToCopy`, then some files cannot be copied/moved or the files are skipped due to [ConflictResolution.MERGE]
     * [BaseFileCallback.onFailed] can be called before [BaseFileCallback.onCompleted] when an error has occurred.
     * @param files newly moved/copied parent files/folders
     * @param success `true` if the process is not canceled and no error during copy/move
     * @param totalFilesToCopy total files, not folders
     * @param totalCopiedFiles total files, not folders
     */
    class Result(val files: List<DocumentFile>, val totalFilesToCopy: Int, val totalCopiedFiles: Int, val success: Boolean)
}