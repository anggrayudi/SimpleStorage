package com.anggrayudi.storage.transfer

import com.anggrayudi.storage.StorageFile

/**
 * A single vocabulary for events emitted by every long-running operation in this library (copy,
 * move, zip, unzip), replacing the parallel v2 hierarchies (`SingleFileResult`,
 * `SingleFolderResult`, `MultipleFilesResult`, `ZipCompressionResult`, `ZipDecompressionResult`).
 *
 * @author Anggrayudi H
 */
sealed interface TransferEvent {

  /** The operation moved to a new [TransferPhase]. */
  data class PhaseChanged(val phase: TransferPhase) : TransferEvent

  /**
   * @param percent 0..100
   * @param bytesPerSecond current write speed in bytes per second
   */
  data class Progress(
    val percent: Float,
    val bytesTransferred: Long,
    val bytesPerSecond: Long,
    val filesCompleted: Int = 0,
    val totalFiles: Int = 0,
  ) : TransferEvent

  /** Terminal event: the operation finished with [result]. */
  data class Completed<T>(val result: TransferResult<T>) : TransferEvent
}

enum class TransferPhase {
  VALIDATING,
  PREPARING,
  COUNTING_FILES,
  DELETING_CONFLICTED_FILES,
  STARTING,
  DELETING_SOURCE_FILES,
}

enum class TransferErrorCode {
  STORAGE_PERMISSION_DENIED,
  CANNOT_CREATE_FILE_IN_TARGET,
  SOURCE_NOT_FOUND,
  TARGET_NOT_FOUND,
  INVALID_TARGET,
  UNKNOWN_IO_ERROR,
  CANCELED,
  TARGET_SAME_AS_SOURCE,
  NO_SPACE_LEFT_ON_TARGET,
  MISSING_ZIP_ENTRY,
  DUPLICATE_ZIP_ENTRY,
  NOT_A_ZIP_FILE,
}

/** @param filesSkipped files left alone because the conflict resolver chose [ConflictResolution.SKIP] */
data class TransferStats(
  val totalFiles: Int = 0,
  val filesTransferred: Int = 0,
  val bytesTransferred: Long = 0,
  val filesSkipped: Int = 0,
)

/** Terminal outcome of a transfer operation. */
sealed interface TransferResult<out T> {

  data class Success<T>(val result: T, val stats: TransferStats = TransferStats()) :
    TransferResult<T>

  /**
   * The conflict resolver chose [ConflictResolution.SKIP] for the top-level target: nothing was
   * written and nothing failed. Per-file skips inside a folder merge do NOT produce this — the
   * operation still ends in [Success], with the count reported via [TransferStats.filesSkipped].
   *
   * @param existingTarget what already occupies the destination, when known
   */
  data class Skipped(val existingTarget: StorageFile?) : TransferResult<Nothing>

  /**
   * @param cause the exception that triggered this failure, if any
   * @param partialStats what had been transferred before the failure, if anything
   */
  data class Failure(
    val errorCode: TransferErrorCode,
    val message: String? = null,
    val cause: Throwable? = null,
    val partialStats: TransferStats? = null,
  ) : TransferResult<Nothing>
}

val TransferResult<*>.isSuccess: Boolean
  get() = this is TransferResult.Success

val TransferResult<*>.isSkipped: Boolean
  get() = this is TransferResult.Skipped

fun <T> TransferResult<T>.getOrNull(): T? = (this as? TransferResult.Success<T>)?.result

fun TransferResult<*>.failureOrNull(): TransferResult.Failure? = this as? TransferResult.Failure
