package com.anggrayudi.storage

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.callback.SingleFileConflictCallback
import com.anggrayudi.storage.callback.SingleFolderConflictCallback
import com.anggrayudi.storage.file.CheckFileSize
import com.anggrayudi.storage.file.DocumentFileType
import com.anggrayudi.storage.file.compressToZip
import com.anggrayudi.storage.file.copyFileTo
import com.anggrayudi.storage.file.copyFolderTo
import com.anggrayudi.storage.file.decompressZip
import com.anggrayudi.storage.file.defaultFileSizeChecker
import com.anggrayudi.storage.file.deleteRecursively
import com.anggrayudi.storage.file.moveFileTo
import com.anggrayudi.storage.file.moveFolderTo
import com.anggrayudi.storage.file.search
import com.anggrayudi.storage.media.MediaFile
import com.anggrayudi.storage.media.decompressZip
import com.anggrayudi.storage.result.FolderErrorCode
import com.anggrayudi.storage.result.SingleFileErrorCode
import com.anggrayudi.storage.result.SingleFileResult
import com.anggrayudi.storage.result.SingleFolderResult
import com.anggrayudi.storage.result.ZipCompressionErrorCode
import com.anggrayudi.storage.result.ZipCompressionResult
import com.anggrayudi.storage.result.ZipDecompressionErrorCode
import com.anggrayudi.storage.result.ZipDecompressionResult
import com.anggrayudi.storage.transfer.Conflict
import com.anggrayudi.storage.transfer.ConflictResolution
import com.anggrayudi.storage.transfer.TransferErrorCode
import com.anggrayudi.storage.transfer.TransferEvent
import com.anggrayudi.storage.transfer.TransferPhase
import com.anggrayudi.storage.transfer.TransferResult
import com.anggrayudi.storage.transfer.TransferSpec
import com.anggrayudi.storage.transfer.TransferStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transfer operations for [StorageFile]. Every operation exists in two forms:
 * - a **one-shot suspend function** (`copyTo`, `moveTo`, `zipTo`, `unzipTo`) that is main-safe and
 *   returns a [TransferResult], with optional progress/conflict callbacks via [TransferSpec];
 * - a **Flow form** (`copyToAsFlow`, …) that emits every [TransferEvent] for callers who need the
 *   full stream (e.g. WorkManager notifications).
 *
 * @author Anggrayudi H
 */

// region One-shot operations

/**
 * Copies this file or folder into [targetFolder].
 *
 * ```kotlin
 * val result = file.copyTo(downloads) {
 *   onConflict { ConflictResolution.REPLACE }
 *   onProgress { progressBar.progress = it.percent.toInt() }
 * }
 * ```
 */
suspend fun StorageFile.copyTo(
  targetFolder: StorageFile,
  configure: TransferSpec.() -> Unit = {},
): TransferResult<StorageFile> {
  val spec = TransferSpec().apply(configure)
  return spec.await(copyToAsFlow(targetFolder, spec))
}

/** Moves this file or folder into [targetFolder]. */
suspend fun StorageFile.moveTo(
  targetFolder: StorageFile,
  configure: TransferSpec.() -> Unit = {},
): TransferResult<StorageFile> {
  val spec = TransferSpec().apply(configure)
  return spec.await(moveToAsFlow(targetFolder, spec))
}

/** Compresses these files/folders into [targetZipFile], which must already exist. */
suspend fun List<StorageFile>.zipTo(
  targetZipFile: StorageFile,
  configure: TransferSpec.() -> Unit = {},
): TransferResult<StorageFile> {
  val spec = TransferSpec().apply(configure)
  return spec.await(zipToAsFlow(targetZipFile, spec))
}

/** Extracts this ZIP file into [targetFolder]. */
suspend fun StorageFile.unzipTo(
  targetFolder: StorageFile,
  configure: TransferSpec.() -> Unit = {},
): TransferResult<StorageFile> {
  val spec = TransferSpec().apply(configure)
  return spec.await(unzipToAsFlow(targetFolder, spec))
}

/** Recursively deletes this file or folder. Main-safe. */
suspend fun StorageFile.deleteRecursively(childrenOnly: Boolean = false): Boolean =
  withContext(Dispatchers.IO) {
    when (this@deleteRecursively) {
      is DocumentStorageFile -> doc.deleteRecursively(context, childrenOnly)
      is MediaStorageFile -> media.delete()
    }
  }

// endregion

// region Flow operations

fun StorageFile.copyToAsFlow(
  targetFolder: StorageFile,
  spec: TransferSpec = TransferSpec(),
): Flow<TransferEvent> = transferFlow(this, targetFolder, spec, move = false)

fun StorageFile.moveToAsFlow(
  targetFolder: StorageFile,
  spec: TransferSpec = TransferSpec(),
): Flow<TransferEvent> = transferFlow(this, targetFolder, spec, move = true)

fun List<StorageFile>.zipToAsFlow(
  targetZipFile: StorageFile,
  spec: TransferSpec = TransferSpec(),
): Flow<TransferEvent> = channelFlow {
  val context = firstOrNull()?.appContext
  if (context == null) {
    send(failure(TransferErrorCode.SOURCE_NOT_FOUND, "No files to compress"))
    return@channelFlow
  }
  val sources = ArrayList<DocumentFile>(size)
  for (file in this@zipToAsFlow) {
    val doc = file.asDocumentFile()
    if (doc == null) {
      send(failure(TransferErrorCode.SOURCE_NOT_FOUND, "Cannot resolve ${file.name}"))
      return@channelFlow
    }
    sources.add(doc)
  }
  val targetDoc = targetZipFile.asDocumentFile()
  if (targetDoc == null) {
    send(failure(TransferErrorCode.INVALID_TARGET, "Target ZIP file is not accessible"))
    return@channelFlow
  }
  sources
    .compressToZip(
      context,
      targetDoc,
      spec.deleteSourceOnSuccess,
      spec.updateInterval,
      spec.sizeChecker(),
    )
    .collect { send(it.toTransferEvent(context, spec)) }
}

fun StorageFile.unzipToAsFlow(
  targetFolder: StorageFile,
  spec: TransferSpec = TransferSpec(),
): Flow<TransferEvent> = channelFlow {
  val context = appContext
  val targetDoc = targetFolder.asDocumentFile()
  if (targetDoc == null || !targetDoc.isDirectory) {
    send(failure(TransferErrorCode.INVALID_TARGET, "Target must be an accessible folder"))
    return@channelFlow
  }
  when (val source = this@unzipToAsFlow) {
    is DocumentStorageFile ->
      source.doc
        .decompressZip(
          context,
          targetDoc,
          spec.updateInterval,
          spec.sizeChecker(),
          fileConflictAdapter(context, spec, this),
        )
        .collect { send(it.toTransferEvent(context)) }
    is MediaStorageFile ->
      source.media.decompressZip(context, targetDoc, spec.updateInterval).collect {
        send(it.toTransferEvent(context))
      }
  }
}

/** Searches inside this folder. Emits snapshots every [updateInterval] ms when it is > 0. */
@JvmOverloads
fun StorageFile.search(
  recursive: Boolean = true,
  documentType: DocumentFileType = DocumentFileType.ANY,
  mimeTypes: Array<String>? = null,
  name: String = "",
  regex: Regex? = null,
  updateInterval: Long = 0,
): Flow<List<StorageFile>> =
  when (this) {
    is DocumentStorageFile ->
      doc.search(recursive, documentType, mimeTypes, name, regex, updateInterval).map { files ->
        files.map { it.toStorageFile(context) }
      }
    is MediaStorageFile -> flowOf(emptyList())
  }

// endregion

// region Internals

internal val StorageFile.appContext: Context
  get() =
    when (this) {
      is DocumentStorageFile -> context
      is MediaStorageFile -> context
    }

private fun transferFlow(
  source: StorageFile,
  target: StorageFile,
  spec: TransferSpec,
  move: Boolean,
): Flow<TransferEvent> = channelFlow {
  val context = source.appContext
  val targetDoc = target.asDocumentFile()
  if (targetDoc == null || !targetDoc.isDirectory) {
    send(failure(TransferErrorCode.INVALID_TARGET, "Target must be an accessible folder"))
    return@channelFlow
  }
  val checker = spec.sizeChecker()
  when (source) {
    is DocumentStorageFile -> {
      if (source.doc.isDirectory) {
        val flow =
          if (move) {
            source.doc.moveFolderTo(
              context,
              targetDoc,
              spec.skipEmptyFiles,
              spec.fileDescription?.name,
              spec.updateInterval,
              checker,
              folderConflictAdapter(context, spec, this),
            )
          } else {
            source.doc.copyFolderTo(
              context,
              targetDoc,
              spec.skipEmptyFiles,
              spec.fileDescription?.name,
              spec.updateInterval,
              checker,
              folderConflictAdapter(context, spec, this),
            )
          }
        flow.collect { send(it.toTransferEvent(context)) }
      } else {
        val flow =
          if (move) {
            source.doc.moveFileTo(
              context,
              targetDoc,
              spec.fileDescription,
              spec.updateInterval,
              checker,
              fileConflictAdapter(context, spec, this),
            )
          } else {
            source.doc.copyFileTo(
              context,
              targetDoc,
              spec.fileDescription,
              spec.updateInterval,
              checker,
              fileConflictAdapter(context, spec, this),
            )
          }
        flow.collect { send(it.toTransferEvent(context, spec)) }
      }
    }
    is MediaStorageFile -> {
      val flow =
        if (move) {
          source.media.moveTo(
            targetDoc,
            spec.fileDescription,
            spec.updateInterval,
            checker,
            fileConflictAdapter(context, spec, this),
          )
        } else {
          source.media.copyTo(
            targetDoc,
            spec.fileDescription,
            spec.updateInterval,
            checker,
            fileConflictAdapter(context, spec, this),
          )
        }
      flow.collect { send(it.toTransferEvent(context, spec)) }
    }
  }
}

private suspend fun TransferSpec.await(flow: Flow<TransferEvent>): TransferResult<StorageFile> {
  var terminal: TransferResult<StorageFile>? = null
  flow.collect { event ->
    when (event) {
      is TransferEvent.Progress -> progressListener?.invoke(event)
      is TransferEvent.Completed<*> -> {
        @Suppress("UNCHECKED_CAST")
        terminal = event.result as TransferResult<StorageFile>
      }
      is TransferEvent.PhaseChanged -> Unit
    }
  }
  return terminal
    ?: TransferResult.Failure(
      TransferErrorCode.UNKNOWN_IO_ERROR,
      "Transfer finished without a terminal event",
    )
}

private fun TransferSpec.sizeChecker(): CheckFileSize =
  if (checkAvailableSpace) defaultFileSizeChecker else { _, _ -> true }

private fun failure(code: TransferErrorCode, message: String? = null) =
  TransferEvent.Completed<StorageFile>(TransferResult.Failure(code, message))

private fun phase(phase: TransferPhase) = TransferEvent.PhaseChanged(phase)

private fun fileConflictAdapter(
  context: Context,
  spec: TransferSpec,
  scope: CoroutineScope,
): SingleFileConflictCallback<DocumentFile> =
  object : SingleFileConflictCallback<DocumentFile>(scope) {
    override fun onFileConflict(destinationFile: DocumentFile, action: FileConflictAction) {
      scope.launch {
        val resolution =
          spec.conflictResolver.resolve(Conflict.TargetFile(destinationFile.toStorageFile(context)))
        action.confirmResolution(resolution.toV2FileResolution())
      }
    }
  }

private fun folderConflictAdapter(
  context: Context,
  spec: TransferSpec,
  scope: CoroutineScope,
): SingleFolderConflictCallback =
  object : SingleFolderConflictCallback(scope) {
    override fun onParentConflict(
      destinationFolder: DocumentFile,
      action: ParentFolderConflictAction,
      canMerge: Boolean,
    ) {
      scope.launch {
        val resolution =
          spec.conflictResolver.resolve(
            Conflict.TargetFolder(destinationFolder.toStorageFile(context), canMerge)
          )
        val v2 =
          when (resolution) {
            com.anggrayudi.storage.transfer.ConflictResolution.REPLACE ->
              SingleFolderConflictCallback.ConflictResolution.REPLACE
            com.anggrayudi.storage.transfer.ConflictResolution.MERGE ->
              if (canMerge) SingleFolderConflictCallback.ConflictResolution.MERGE
              else SingleFolderConflictCallback.ConflictResolution.CREATE_NEW
            com.anggrayudi.storage.transfer.ConflictResolution.CREATE_NEW ->
              SingleFolderConflictCallback.ConflictResolution.CREATE_NEW
            com.anggrayudi.storage.transfer.ConflictResolution.SKIP ->
              SingleFolderConflictCallback.ConflictResolution.SKIP
          }
        action.confirmResolution(v2)
      }
    }

    override fun onContentConflict(
      destinationFolder: DocumentFile,
      conflictedFiles: MutableList<FileConflict>,
      action: FolderContentConflictAction,
    ) {
      scope.launch {
        conflictedFiles.forEach { conflict ->
          conflict.solution =
            spec.conflictResolver
              .resolve(Conflict.TargetFile(conflict.target.toStorageFile(context)))
              .toV2FileResolution()
        }
        action.confirmResolution(conflictedFiles)
      }
    }
  }

private fun ConflictResolution.toV2FileResolution(): SingleFileConflictCallback.ConflictResolution =
  when (this) {
    ConflictResolution.REPLACE -> SingleFileConflictCallback.ConflictResolution.REPLACE
    ConflictResolution.MERGE,
    ConflictResolution.CREATE_NEW -> SingleFileConflictCallback.ConflictResolution.CREATE_NEW
    ConflictResolution.SKIP -> SingleFileConflictCallback.ConflictResolution.SKIP
  }

private fun bytesPerSecond(bytesPerInterval: Int, updateInterval: Long): Long =
  if (updateInterval <= 0) 0 else bytesPerInterval * 1000L / updateInterval

private fun Any.wrapResult(context: Context): TransferResult<StorageFile> =
  when (this) {
    is DocumentFile ->
      TransferResult.Success(toStorageFile(context), TransferStats(1, 1, length()))
    is MediaFile -> TransferResult.Success(toStorageFile(context), TransferStats(1, 1, length))
    else ->
      TransferResult.Failure(
        TransferErrorCode.UNKNOWN_IO_ERROR,
        "Unexpected result type: ${this::class.qualifiedName}",
      )
  }

private fun SingleFileResult.toTransferEvent(context: Context, spec: TransferSpec): TransferEvent =
  when (this) {
    SingleFileResult.Validating -> phase(TransferPhase.VALIDATING)
    SingleFileResult.Preparing -> phase(TransferPhase.PREPARING)
    SingleFileResult.CountingFiles -> phase(TransferPhase.COUNTING_FILES)
    SingleFileResult.DeletingConflictedFile -> phase(TransferPhase.DELETING_CONFLICTED_FILES)
    is SingleFileResult.Starting -> phase(TransferPhase.STARTING)
    is SingleFileResult.InProgress ->
      TransferEvent.Progress(
        progress,
        bytesMoved,
        bytesPerSecond(writeSpeed, spec.updateInterval),
        filesCompleted = 0,
        totalFiles = 1,
      )
    is SingleFileResult.Completed -> TransferEvent.Completed(result.wrapResult(context))
    is SingleFileResult.Error ->
      TransferEvent.Completed<StorageFile>(
        TransferResult.Failure(errorCode.toTransferError(), message, cause)
      )
  }

private fun SingleFolderResult.toTransferEvent(context: Context): TransferEvent =
  when (this) {
    SingleFolderResult.Validating -> phase(TransferPhase.VALIDATING)
    SingleFolderResult.Preparing -> phase(TransferPhase.PREPARING)
    SingleFolderResult.CountingFiles -> phase(TransferPhase.COUNTING_FILES)
    SingleFolderResult.DeletingConflictedFiles -> phase(TransferPhase.DELETING_CONFLICTED_FILES)
    is SingleFolderResult.Starting -> phase(TransferPhase.STARTING)
    is SingleFolderResult.InProgress ->
      TransferEvent.Progress(progress, bytesMoved, writeSpeed.toLong(), fileCount, 0)
    is SingleFolderResult.Completed -> {
      val stats = TransferStats(totalFilesToCopy, totalCopiedFiles, 0)
      if (success) {
        TransferEvent.Completed(TransferResult.Success(folder.toStorageFile(context), stats))
      } else {
        TransferEvent.Completed<StorageFile>(
          TransferResult.Failure(
            TransferErrorCode.UNKNOWN_IO_ERROR,
            "Some files could not be transferred",
            partialStats = stats,
          )
        )
      }
    }
    is SingleFolderResult.Error ->
      TransferEvent.Completed<StorageFile>(
        TransferResult.Failure(
          errorCode.toTransferError(),
          message,
          cause,
          completedData?.let { TransferStats(it.totalFilesToCopy, it.totalCopiedFiles, 0) },
        )
      )
  }

private fun ZipCompressionResult.toTransferEvent(
  context: Context,
  spec: TransferSpec,
): TransferEvent =
  when (this) {
    ZipCompressionResult.CountingFiles -> phase(TransferPhase.COUNTING_FILES)
    ZipCompressionResult.DeletingEntryFiles -> phase(TransferPhase.DELETING_SOURCE_FILES)
    is ZipCompressionResult.Compressing ->
      TransferEvent.Progress(
        progress,
        bytesCompressed,
        bytesPerSecond(writeSpeed, spec.updateInterval),
        fileCount,
        0,
      )
    is ZipCompressionResult.Completed ->
      TransferEvent.Completed(
        TransferResult.Success(
          zipFile.toStorageFile(context),
          TransferStats(totalFilesCompressed, totalFilesCompressed, bytesCompressed),
        )
      )
    is ZipCompressionResult.Error ->
      TransferEvent.Completed<StorageFile>(
        TransferResult.Failure(errorCode.toTransferError(), message, cause)
      )
  }

private fun ZipDecompressionResult.toTransferEvent(context: Context): TransferEvent =
  when (this) {
    ZipDecompressionResult.Validating -> phase(TransferPhase.VALIDATING)
    is ZipDecompressionResult.Decompressing ->
      // ZIP entries don't expose a total size up front, so percent is indeterminate (-1).
      TransferEvent.Progress(-1f, bytesDecompressed, writeSpeed.toLong(), fileCount, 0)
    is ZipDecompressionResult.Completed ->
      TransferEvent.Completed(
        TransferResult.Success(
          targetFolder.toStorageFile(context),
          TransferStats(totalFilesDecompressed, totalFilesDecompressed, bytesDecompressed),
        )
      )
    is ZipDecompressionResult.Error ->
      TransferEvent.Completed<StorageFile>(
        TransferResult.Failure(errorCode.toTransferError(), message, cause)
      )
  }

private fun SingleFileErrorCode.toTransferError(): TransferErrorCode =
  when (this) {
    SingleFileErrorCode.STORAGE_PERMISSION_DENIED -> TransferErrorCode.STORAGE_PERMISSION_DENIED
    SingleFileErrorCode.CANNOT_CREATE_FILE_IN_TARGET ->
      TransferErrorCode.CANNOT_CREATE_FILE_IN_TARGET
    SingleFileErrorCode.SOURCE_FILE_NOT_FOUND -> TransferErrorCode.SOURCE_NOT_FOUND
    SingleFileErrorCode.TARGET_FILE_NOT_FOUND,
    SingleFileErrorCode.TARGET_FOLDER_NOT_FOUND -> TransferErrorCode.TARGET_NOT_FOUND
    SingleFileErrorCode.UNKNOWN_IO_ERROR -> TransferErrorCode.UNKNOWN_IO_ERROR
    SingleFileErrorCode.CANCELED -> TransferErrorCode.CANCELED
    SingleFileErrorCode.TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER ->
      TransferErrorCode.TARGET_SAME_AS_SOURCE
    SingleFileErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH -> TransferErrorCode.NO_SPACE_LEFT_ON_TARGET
  }

private fun FolderErrorCode.toTransferError(): TransferErrorCode =
  when (this) {
    FolderErrorCode.STORAGE_PERMISSION_DENIED -> TransferErrorCode.STORAGE_PERMISSION_DENIED
    FolderErrorCode.CANNOT_CREATE_FILE_IN_TARGET -> TransferErrorCode.CANNOT_CREATE_FILE_IN_TARGET
    FolderErrorCode.SOURCE_FOLDER_NOT_FOUND,
    FolderErrorCode.SOURCE_FILE_NOT_FOUND -> TransferErrorCode.SOURCE_NOT_FOUND
    FolderErrorCode.INVALID_TARGET_FOLDER -> TransferErrorCode.INVALID_TARGET
    FolderErrorCode.UNKNOWN_IO_ERROR -> TransferErrorCode.UNKNOWN_IO_ERROR
    FolderErrorCode.CANCELED -> TransferErrorCode.CANCELED
    FolderErrorCode.TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER ->
      TransferErrorCode.TARGET_SAME_AS_SOURCE
    FolderErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH -> TransferErrorCode.NO_SPACE_LEFT_ON_TARGET
  }

private fun ZipCompressionErrorCode.toTransferError(): TransferErrorCode =
  when (this) {
    ZipCompressionErrorCode.STORAGE_PERMISSION_DENIED ->
      TransferErrorCode.STORAGE_PERMISSION_DENIED
    ZipCompressionErrorCode.CANNOT_CREATE_FILE_IN_TARGET ->
      TransferErrorCode.CANNOT_CREATE_FILE_IN_TARGET
    ZipCompressionErrorCode.MISSING_ENTRY_FILE -> TransferErrorCode.MISSING_ZIP_ENTRY
    ZipCompressionErrorCode.DUPLICATE_ENTRY_FILE -> TransferErrorCode.DUPLICATE_ZIP_ENTRY
    ZipCompressionErrorCode.UNKNOWN_IO_ERROR -> TransferErrorCode.UNKNOWN_IO_ERROR
    ZipCompressionErrorCode.CANCELED -> TransferErrorCode.CANCELED
    ZipCompressionErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH ->
      TransferErrorCode.NO_SPACE_LEFT_ON_TARGET
  }

private fun ZipDecompressionErrorCode.toTransferError(): TransferErrorCode =
  when (this) {
    ZipDecompressionErrorCode.STORAGE_PERMISSION_DENIED ->
      TransferErrorCode.STORAGE_PERMISSION_DENIED
    ZipDecompressionErrorCode.CANNOT_CREATE_FILE_IN_TARGET ->
      TransferErrorCode.CANNOT_CREATE_FILE_IN_TARGET
    ZipDecompressionErrorCode.MISSING_ZIP_FILE -> TransferErrorCode.SOURCE_NOT_FOUND
    ZipDecompressionErrorCode.NOT_A_ZIP_FILE -> TransferErrorCode.NOT_A_ZIP_FILE
    ZipDecompressionErrorCode.UNKNOWN_IO_ERROR -> TransferErrorCode.UNKNOWN_IO_ERROR
    ZipDecompressionErrorCode.CANCELED -> TransferErrorCode.CANCELED
    ZipDecompressionErrorCode.NO_SPACE_LEFT_ON_TARGET_PATH ->
      TransferErrorCode.NO_SPACE_LEFT_ON_TARGET
  }

// endregion
