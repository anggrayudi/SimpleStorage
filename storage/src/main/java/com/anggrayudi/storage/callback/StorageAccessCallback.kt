package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.StorageType

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version StoragePermissionCallback, v 0.0.1 10/08/20 01.32 by Anggrayudi Hardiannico A.
 */
interface StorageAccessCallback {

    fun onCancelledByUser()

    fun onRootPathNotSelected(rootPath: String, rootStorageType: StorageType)

    fun onStoragePermissionDenied()

    fun onRootPathPermissionGranted(root: DocumentFile)
}