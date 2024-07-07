package com.anggrayudi.storage.callback

import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.CreateMode
import com.anggrayudi.storage.media.MediaFile
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */
abstract class FileCallback @OptIn(DelicateCoroutinesApi::class) @JvmOverloads constructor(uiScope: CoroutineScope = GlobalScope) : BaseFileCallback<FileCallback.ErrorCode, FileCallback.Report, FileCallback.Result>(uiScope) {

    /**
     * @param file can be [DocumentFile] or [MediaFile]
     * @return Time interval to watch file copy/move progress in milliseconds, otherwise `0` if you don't want to watch at all.
     * Setting negative value will cancel the operation.
     */
    @UiThread
    open fun onStart(file: Any, workerThread: Thread): Long = 0

    /**
     * Do not call `super` when you override this function.
     *
     * The thread that does copy/move will be suspended until the user gives an answer via [FileConflictAction.confirmResolution].
     * You have to give an answer, or the thread will be alive until the app is killed and end up as a zombie thread.
     * If you want to cancel, just pass [ConflictResolution.SKIP] into [FileConflictAction.confirmResolution].
     * If the worker thread is suspended for too long, it may be interrupted by the system.
     */
    @UiThread
    open fun onConflict(destinationFile: DocumentFile, action: FileConflictAction) {
        action.confirmResolution(ConflictResolution.CREATE_NEW)
    }

    class FileConflictAction(private val continuation: CancellableContinuation<ConflictResolution>) {

        fun confirmResolution(resolution: ConflictResolution) {
            continuation.resumeWith(kotlin.Result.success(resolution))
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
        SKIP;

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        fun toCreateMode(allowReuseFile: Boolean = false) = when (this) {
            REPLACE -> CreateMode.REPLACE
            CREATE_NEW -> CreateMode.CREATE_NEW
            SKIP -> if (allowReuseFile) CreateMode.REUSE else CreateMode.CREATE_NEW
        }
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

    /**
     * Only called if the returned [onStart] greater than `0`
     *
     * @param progress   in percent
     * @param writeSpeed in bytes
     */
    data class Report(val progress: Float, val bytesMoved: Long, val writeSpeed: Int)

    sealed interface Result {
        @JvmInline
        value class MediaFile(val value: com.anggrayudi.storage.media.MediaFile) : Result

        @JvmInline
        value class DocumentFile(val value: androidx.documentfile.provider.DocumentFile) : Result

        companion object {
            internal fun get(value: Any): Result =
                when (value) {
                    is com.anggrayudi.storage.media.MediaFile -> MediaFile(value)
                    is androidx.documentfile.provider.DocumentFile -> DocumentFile(value)
                    else -> throw IllegalArgumentException("Result must be either of type ${com.anggrayudi.storage.media.MediaFile::class.java.name} or ${androidx.documentfile.provider.DocumentFile::class.java.name}")
                }
        }
    }
}