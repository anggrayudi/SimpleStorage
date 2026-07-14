package com.anggrayudi.storage.callback

import android.content.Intent
import androidx.documentfile.provider.DocumentFile

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
@Deprecated("Superseded in v3 by StorageAccessManager.pickFiles(), which returns a FilePickerResult instead of using callbacks. See MIGRATION.md.")
public interface FilePickerCallback {

  public fun onCanceledByUser(requestCode: Int) {
    // default implementation
  }

  public fun onActivityHandlerNotFound(requestCode: Int, intent: Intent) {
    // default implementation
  }

  /** Called when you have no read permission to current path */
  public fun onStoragePermissionDenied(requestCode: Int, files: List<DocumentFile>?)

  /** @param files non-empty list */
  public fun onFileSelected(requestCode: Int, files: List<DocumentFile>)
}
