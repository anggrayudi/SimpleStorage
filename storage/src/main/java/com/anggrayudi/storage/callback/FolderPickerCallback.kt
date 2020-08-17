package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.DocumentFileCompat

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */
interface FolderPickerCallback {

    fun onCancelledByUser()

    fun onStoragePermissionDenied()

    /**
     * Called when storage permissions are granted, but [DocumentFileCompat.isStorageUriPermissionGranted] returns `false`
     *
     * @param folder selected folder that has no read and write permission
     */
    fun onStorageAccessDenied(folder: DocumentFile?)

    fun onFolderSelected(folder: DocumentFile)
}