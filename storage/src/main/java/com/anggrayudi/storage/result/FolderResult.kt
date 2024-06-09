package com.anggrayudi.storage.result

import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.callback.FolderConflictCallback
import com.anggrayudi.storage.callback.FolderConflictCallback.ConflictResolution
import com.anggrayudi.storage.callback.SingleFileConflictCallback

/**
 * Created on 7/6/24
 * @author Anggrayudi Hardiannico A.
 */
sealed class FolderResult {
    data object Validating : FolderResult()
    data object Preparing : FolderResult()
    data object CountingFiles : FolderResult()

    /**
     * Called after the user chooses [FolderConflictCallback.ConflictResolution.REPLACE] or [SingleFileConflictCallback.ConflictResolution.REPLACE]
     */
    data object DeletingConflictedFiles : FolderResult()
    data class Starting(val files: List<DocumentFile>, val totalFilesToCopy: Int) : FolderResult()

    /**
     * @param fileCount total files/folders that are successfully copied/moved
     */
    data class InProgress(val progress: Float, val bytesMoved: Long, val writeSpeed: Int, val fileCount: Int) : FolderResult()

    /**
     * If `totalCopiedFiles` are less than `totalFilesToCopy`, then some files cannot be copied/moved or the files are skipped due to [ConflictResolution.MERGE]
     * @param folder newly moved/copied file
     * @param success `true` if the process is not canceled and no error during copy/move
     * @param totalFilesToCopy total files, not folders
     * @param totalCopiedFiles total files, not folders
     */
    data class Completed(val folder: DocumentFile, val totalFilesToCopy: Int, val totalCopiedFiles: Int, val success: Boolean) : FolderResult()
    data class Error(val errorCode: FolderErrorCode, val message: String? = null, val completedData: Completed? = null) : FolderResult()
}

enum class FolderErrorCode {
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