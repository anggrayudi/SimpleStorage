package com.anggrayudi.storage.callback

import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.callback.FileCallback.FileConflictAction
import com.anggrayudi.storage.file.CreateMode
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

/**
 * Created on 3/1/21
 * @author Anggrayudi H
 */
abstract class FolderCallback @JvmOverloads constructor(
    uiScope: CoroutineScope = GlobalScope
) : BaseFileCallback<FolderCallback.ErrorCode, FolderCallback.Report, FolderCallback.Result>(uiScope) {

    @UiThread
    open fun onCountingFiles() {
        // default implementation
    }

    /**
     * @param folder directory to be copied/moved
     * @return Time interval to watch folder copy/move progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the operation.
     */
    @UiThread
    open fun onStart(folder: DocumentFile, totalFilesToCopy: Int, workerThread: Thread): Long = 0

    /**
     * Do not call `super` when you override this function.
     *
     * The thread that does copy/move will be suspended until the user gives an answer via [FileConflictAction.confirmResolution].
     * You have to give an answer, or the thread will be alive until the app is killed and end up as a zombie thread.
     * If you want to cancel, just pass [ConflictResolution.SKIP] into [FileConflictAction.confirmResolution].
     * If the worker thread is suspended for too long, it may be interrupted by the system.
     *
     * @param destinationFolder when copying `/storage/AAAA-BBBB/Movie` to `/storage/AAAA-BBBB/Others`, it will be `/storage/AAAA-BBBB/Others/Movie`
     * @param canMerge when conflict found, action `MERGE` may not exists.
     *                 This happens if the destination is a file.
     */
    @UiThread
    open fun onParentConflict(destinationFolder: DocumentFile, action: ParentFolderConflictAction, canMerge: Boolean) {
        action.confirmResolution(ConflictResolution.CREATE_NEW)
    }

    @UiThread
    open fun onContentConflict(destinationFolder: DocumentFile, conflictedFiles: MutableList<FileConflict>, action: FolderContentConflictAction) {
        action.confirmResolution(conflictedFiles)
    }

    class ParentFolderConflictAction(private val continuation: CancellableContinuation<ConflictResolution>) {

        fun confirmResolution(resolution: ConflictResolution) {
            continuation.resumeWith(kotlin.Result.success(resolution))
        }
    }

    class FolderContentConflictAction(private val continuation: CancellableContinuation<List<FileConflict>>) {

        fun confirmResolution(resolutions: List<FileConflict>) {
            continuation.resumeWith(kotlin.Result.success(resolutions))
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
        SKIP;

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        fun toCreateMode() = when (this) {
            REPLACE -> CreateMode.REPLACE
            MERGE -> CreateMode.REUSE
            else -> CreateMode.CREATE_NEW
        }

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        fun toFileConflictResolution() = when (this) {
            REPLACE -> FileCallback.ConflictResolution.REPLACE
            CREATE_NEW -> FileCallback.ConflictResolution.CREATE_NEW
            else -> FileCallback.ConflictResolution.SKIP
        }
    }

    class FileConflict(
        val source: DocumentFile,
        val target: DocumentFile,
        var solution: FileCallback.ConflictResolution = FileCallback.ConflictResolution.CREATE_NEW
    )

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
     * @param folder newly moved/copied file
     * @param success `true` if the process is not canceled and no error during copy/move
     * @param totalFilesToCopy total files, not folders
     * @param totalCopiedFiles total files, not folders
     */
    class Result(val folder: DocumentFile, val totalFilesToCopy: Int, val totalCopiedFiles: Int, val success: Boolean)
}