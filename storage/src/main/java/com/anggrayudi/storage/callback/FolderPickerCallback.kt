package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.DocumentFileCompat
import com.anggrayudi.storage.StorageType

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */
interface FolderPickerCallback {

    fun onCancelledByUser(requestCode: Int)

    fun onStoragePermissionDenied(requestCode: Int)

    /**
     * Called when storage permissions are granted, but [DocumentFileCompat.isStorageUriPermissionGranted] returns `false`
     *
     * @param folder selected folder that has no read and write permission
     */
    fun onStorageAccessDenied(requestCode: Int, folder: DocumentFile?, storageType: StorageType)

    fun onFolderSelected(requestCode: Int, folder: DocumentFile)
}