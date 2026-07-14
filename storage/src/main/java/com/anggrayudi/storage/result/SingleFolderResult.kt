package com.anggrayudi.storage.result

import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.callback.SingleFileConflictCallback
import com.anggrayudi.storage.callback.SingleFolderConflictCallback
import com.anggrayudi.storage.callback.SingleFolderConflictCallback.ConflictResolution

/**
 * Created on 7/6/24
 *
 * @author Anggrayudi Hardiannico A.
 */
public sealed class SingleFolderResult {
  public data object Validating : SingleFolderResult()

  public data object Preparing : SingleFolderResult()

  public data object CountingFiles : SingleFolderResult()

  /**
   * Called after the user chooses [SingleFolderConflictCallback.ConflictResolution.REPLACE] or
   * [SingleFileConflictCallback.ConflictResolution.REPLACE]
   */
  public data object DeletingConflictedFiles : SingleFolderResult()

  /** A good state to start showing notification */
  public data class Starting(val files: List<DocumentFile>, val totalFilesToCopy: Int) :
    SingleFolderResult()

  /** @param fileCount total files/folders that are successfully copied/moved */
  public data class InProgress(
    val progress: Float,
    val bytesMoved: Long,
    val writeSpeed: Int,
    val fileCount: Int,
  ) : SingleFolderResult()

  /**
   * If `totalCopiedFiles` are less than `totalFilesToCopy`, then some files cannot be copied/moved
   * or the files are skipped due to [ConflictResolution.MERGE]
   *
   * @param folder newly moved/copied file
   * @param success `true` if the process is not canceled and no error during copy/move
   * @param totalFilesToCopy total files, not folders
   * @param totalCopiedFiles total files, not folders
   */
  public data class Completed(
    val folder: DocumentFile,
    val totalFilesToCopy: Int,
    val totalCopiedFiles: Int,
    val success: Boolean,
  ) : SingleFolderResult()

  /** @param cause the exception that triggered this error, if any */
  public data class Error(
    val errorCode: FolderErrorCode,
    val message: String? = null,
    val completedData: Completed? = null,
    val cause: Throwable? = null,
  ) : SingleFolderResult()
}

public enum class FolderErrorCode {
  STORAGE_PERMISSION_DENIED,
  CANNOT_CREATE_FILE_IN_TARGET,
  SOURCE_FOLDER_NOT_FOUND,
  SOURCE_FILE_NOT_FOUND,
  INVALID_TARGET_FOLDER,
  UNKNOWN_IO_ERROR,
  CANCELED,
  TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER,
  NO_SPACE_LEFT_ON_TARGET_PATH,
}
