package com.anggrayudi.storage.callback

import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageType

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
@Deprecated("Superseded in v3 by StorageAccessManager.pickFolder(), which returns a FolderPickerResult instead of using callbacks. See MIGRATION.md.")
public interface FolderPickerCallback {

  public fun onCanceledByUser(requestCode: Int) {}

  public fun onActivityHandlerNotFound(requestCode: Int, intent: Intent) {}

  public fun onStoragePermissionDenied(requestCode: Int)

  /**
   * Called when storage permissions are granted, but
   * [DocumentFileCompat.isStorageUriPermissionGranted] returns `false`
   *
   * @param folder selected folder that has no read and write permission
   * @param storageType `null` if `folder`'s authority is not
   *   [DocumentFileCompat.EXTERNAL_STORAGE_AUTHORITY]
   */
  public fun onStorageAccessDenied(
    requestCode: Int,
    folder: DocumentFile?,
    storageType: StorageType,
    storageId: String,
  )

  public fun onFolderSelected(requestCode: Int, folder: DocumentFile)
}
