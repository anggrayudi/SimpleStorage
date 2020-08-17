package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version StoragePermissionCallback, v 0.0.1 10/08/20 01.32 by Anggrayudi Hardiannico A.
 */
interface StoragePermissionCallback {

    fun onRootPathNotSelected(rootPath: String)

    fun onStoragePermissionDenied()

    fun onRootPathPermissionGranted(root: DocumentFile)
}