package com.anggrayudi.storage.result

import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.media.MediaFile

/**
 * Created on 7/6/24
 *
 * @author Anggrayudi Hardiannico A.
 */
public sealed class SingleFileResult {
  public data object Validating : SingleFileResult()

  public data object Preparing : SingleFileResult()

  public data object CountingFiles : SingleFileResult()

  public data object DeletingConflictedFile : SingleFileResult()

  public data class Starting(val files: List<DocumentFile>, val totalFilesToCopy: Int) :
    SingleFileResult()

  public data class InProgress(val progress: Float, val bytesMoved: Long, val writeSpeed: Int) :
    SingleFileResult()

  /** @param result can be [DocumentFile] or [MediaFile] */
  public data class Completed(val result: Any) : SingleFileResult()

  /** @param cause the exception that triggered this error, if any */
  public data class Error(
    val errorCode: SingleFileErrorCode,
    val message: String? = null,
    val cause: Throwable? = null,
  ) : SingleFileResult()
}

public enum class SingleFileErrorCode {
  STORAGE_PERMISSION_DENIED,
  CANNOT_CREATE_FILE_IN_TARGET,
  SOURCE_FILE_NOT_FOUND,
  TARGET_FILE_NOT_FOUND,
  TARGET_FOLDER_NOT_FOUND,
  UNKNOWN_IO_ERROR,
  CANCELED,
  TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER,
  NO_SPACE_LEFT_ON_TARGET_PATH,
}
