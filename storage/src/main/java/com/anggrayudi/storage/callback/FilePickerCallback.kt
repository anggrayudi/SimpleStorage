package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */
interface FilePickerCallback {

    fun onCancelledByUser()

    /**
     * Called when you have no read permission to current path
     */
    fun onStoragePermissionDenied(file: DocumentFile?)

    fun onFileSelected(file: DocumentFile)
}