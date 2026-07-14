package com.anggrayudi.storage.result

import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.media.MediaFile

/**
 * Created on 7/6/24
 *
 * @author Anggrayudi Hardiannico A.
 */
public sealed class ZipDecompressionResult {
  public data object Validating : ZipDecompressionResult()

  public data class Decompressing(val bytesDecompressed: Long, val writeSpeed: Int, val fileCount: Int) :
    ZipDecompressionResult()

  /** @param zipFile can be [DocumentFile] or [MediaFile] */
  public data class Completed(
    val zipFile: Any,
    val targetFolder: DocumentFile,
    val bytesDecompressed: Long,
    val skippedDecompressedBytes: Long,
    val totalFilesDecompressed: Int,
    val decompressionRate: Float,
  ) : ZipDecompressionResult()

  /** @param cause the exception that triggered this error, if any */
  public data class Error(
    val errorCode: ZipDecompressionErrorCode,
    val message: String? = null,
    val cause: Throwable? = null,
  ) : ZipDecompressionResult()
}

public enum class ZipDecompressionErrorCode {
  STORAGE_PERMISSION_DENIED,
  CANNOT_CREATE_FILE_IN_TARGET,
  MISSING_ZIP_FILE,
  NOT_A_ZIP_FILE,
  UNKNOWN_IO_ERROR,
  CANCELED,
  NO_SPACE_LEFT_ON_TARGET_PATH,
}
