package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */
interface FilePickerCallback {

    fun onCanceledByUser(requestCode: Int) {
        // default implementation
    }

    /**
     * Called when you have no read permission to current path
     */
    fun onStoragePermissionDenied(requestCode: Int, file: DocumentFile?)

    fun onFileSelected(requestCode: Int, file: DocumentFile)
}