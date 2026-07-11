package com.anggrayudi.storage.transfer

import com.anggrayudi.storage.media.FileDescription

/**
 * Optional knobs for transfer operations, configured through a lambda:
 * ```kotlin
 * val result = file.copyTo(target) {
 *   onConflict { ConflictResolution.REPLACE }
 *   onProgress { progressBar.progress = it.percent.toInt() }
 *   updateInterval = 250
 * }
 * ```
 *
 * @author Anggrayudi H
 */
class TransferSpec {

  /** Interval between [TransferEvent.Progress] emissions, in milliseconds. */
  var updateInterval: Long = 500

  /** Fail fast with [TransferErrorCode.NO_SPACE_LEFT_ON_TARGET] when the target volume is full. */
  var checkAvailableSpace: Boolean = true

  /** Skip zero-length files when transferring folders. */
  var skipEmptyFiles: Boolean = true

  /** Renames the file (and optionally its MIME type or sub folder) in the destination. */
  var fileDescription: FileDescription? = null

  /** Zip only: delete the source files after the archive is written successfully. */
  var deleteSourceOnSuccess: Boolean = false

  internal var conflictResolver: ConflictResolver = ConflictResolver {
    ConflictResolution.CREATE_NEW
  }

  internal var progressListener: (suspend (TransferEvent.Progress) -> Unit)? = null

  /** Called when the destination already contains the file/folder. Default: [ConflictResolution.CREATE_NEW]. */
  fun onConflict(resolver: ConflictResolver) {
    conflictResolver = resolver
  }

  /** Progress callback for the one-shot suspend operations, invoked every [updateInterval] ms. */
  fun onProgress(listener: suspend (TransferEvent.Progress) -> Unit) {
    progressListener = listener
  }
}
