package com.anggrayudi.storage.callback

import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.StorageType

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version StoragePermissionCallback, v 0.0.1 10/08/20 01.32 by Anggrayudi Hardiannico A.
 */
interface StorageAccessCallback {

    fun onCanceledByUser(requestCode: Int) {
        // default implementation
    }

    fun onActivityHandlerNotFound(requestCode: Int, intent: Intent) {
        // default implementation
    }

    fun onRootPathNotSelected(requestCode: Int, rootPath: String, uri: Uri, selectedStorageType: StorageType, expectedStorageType: StorageType)

    fun onStoragePermissionDenied(requestCode: Int)

    fun onRootPathPermissionGranted(requestCode: Int, root: DocumentFile)
}