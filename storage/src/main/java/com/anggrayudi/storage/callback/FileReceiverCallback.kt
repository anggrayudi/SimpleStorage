package com.anggrayudi.storage.callback

import android.content.Intent
import androidx.documentfile.provider.DocumentFile

/**
 * Created on 06/08/21
 *
 * @author Anggrayudi H
 */
public interface FileReceiverCallback {

  public fun onFileReceived(files: List<DocumentFile>)

  public fun onNonFileReceived(intent: Intent)
}
