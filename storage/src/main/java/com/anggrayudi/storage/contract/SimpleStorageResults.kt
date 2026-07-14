package com.anggrayudi.storage.contract

import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.StorageType

public sealed class FolderPickerResult {
  public data class Picked(val folder: DocumentFile) : FolderPickerResult()

  public data class AccessDenied(
    val folder: DocumentFile?,
    val storageType: StorageType,
    val storageId: String,
  ) : FolderPickerResult()

  public data object CanceledByUser : FolderPickerResult()
}

public sealed class FilePickerResult {
  public data class Picked(val files: List<DocumentFile>) : FilePickerResult()

  public data object CanceledByUser : FilePickerResult()

  /** @see [StoragePermissionDeniedException] */
  public data class StoragePermissionDenied(val files: List<DocumentFile>) : FilePickerResult()
}

public sealed class FileCreationResult {
  public data class Created(val file: DocumentFile) : FileCreationResult()

  public data object CanceledByUser : FileCreationResult()

  /** @see [StoragePermissionDeniedException] */
  public data object StoragePermissionDenied : FileCreationResult()
}

public sealed class RequestStorageAccessResult {
  public data object CanceledByUser : RequestStorageAccessResult()

  /** @see [StoragePermissionDeniedException] */
  public data object StoragePermissionDenied : RequestStorageAccessResult()

  /** Triggered on Android 10 and lower. */
  public data class RootPathNotSelected(
    val rootPath: String,
    val uri: Uri,
    val selectedStorageType: StorageType,
    val expectedStorageType: StorageType,
    /** Expected [Intent] to launch when the root path is not selected. */
    val expectedIntent: Intent? = null,
  ) : RequestStorageAccessResult()

  /** Triggered on Android 11 and higher. */
  public data class ExpectedStorageNotSelected(
    val selectedFolder: DocumentFile,
    val selectedStorageType: StorageType,
    val expectedBasePath: String,
    val expectedStorageType: StorageType,
  ) : RequestStorageAccessResult()

  public data class RootPathPermissionGranted(val root: DocumentFile) : RequestStorageAccessResult()
}
