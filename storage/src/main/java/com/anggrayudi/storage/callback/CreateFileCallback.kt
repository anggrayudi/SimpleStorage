package com.anggrayudi.storage.callback

import android.content.Intent
import androidx.documentfile.provider.DocumentFile

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
@Deprecated("Superseded in v3 by StorageAccessManager.createFile(), which returns a FileCreationResult instead of using callbacks. See MIGRATION.md.")
public interface CreateFileCallback {

  public fun onCanceledByUser(requestCode: Int) {}

  public fun onActivityHandlerNotFound(requestCode: Int, intent: Intent) {}

  public fun onFileCreated(requestCode: Int, file: DocumentFile)
}
