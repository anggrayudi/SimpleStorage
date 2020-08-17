package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */
interface FolderPickerCallback {

    fun onUserCancelledFolderPicker()

    fun onStoragePermissionDenied()

    fun onFolderSelected(folder: DocumentFile)
}