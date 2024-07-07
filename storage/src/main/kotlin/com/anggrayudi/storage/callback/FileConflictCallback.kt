package com.anggrayudi.storage.callback

import androidx.annotation.UiThread
import com.anggrayudi.storage.callback.FileCallback.ConflictResolution
import com.anggrayudi.storage.callback.FileCallback.FileConflictAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

/**
 * @author Anggrayudi Hardiannico A.
 */
abstract class FileConflictCallback<T> @OptIn(DelicateCoroutinesApi::class) @JvmOverloads constructor(
    var uiScope: CoroutineScope = GlobalScope
) {

    /**
     * Do not call `super` when you override this function.
     *
     * The thread that does copy/move/decompress will be suspended until the user gives an answer via [FileConflictAction.confirmResolution].
     * You have to give an answer, or the thread will be alive until the app is killed and end up as a zombie thread.
     * If you want to cancel, just pass [ConflictResolution.SKIP] into [FileConflictAction.confirmResolution].
     * If the worker thread is suspended for too long, it may be interrupted by the system.
     */
    @UiThread
    open fun onFileConflict(destinationFile: T, action: FileConflictAction) {
        action.confirmResolution(ConflictResolution.CREATE_NEW)
    }
}