package com.anggrayudi.storage

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.anggrayudi.storage.callback.FilePickerCallback
import com.anggrayudi.storage.callback.FolderPickerCallback
import com.anggrayudi.storage.callback.StorageAccessCallback
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageType
import com.anggrayudi.storage.file.getAbsolutePath
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.listener.multi.BaseMultiplePermissionsListener

/**
 * Helper class to ease you using file & folder picker.
 *
 * @author Anggrayudi H.
 */
class SimpleStorageHelper {

    val storage: SimpleStorage

    private var openFolderPickerOnceGranted = false

    @JvmOverloads
    constructor(activity: FragmentActivity, savedState: Bundle? = null) {
        storage = SimpleStorage(activity)
        savedState?.let { onRestoreInstanceState(it) }
        init()
    }

    @JvmOverloads
    constructor(fragment: Fragment, savedState: Bundle? = null) {
        storage = SimpleStorage(fragment)
        savedState?.let { onRestoreInstanceState(it) }
        init()
    }

    var requestCodeStorageAccess = REQUEST_CODE_STORAGE_ACCESS
    var requestCodeFolderPicker = REQUEST_CODE_STORAGE_GET_FOLDER
    var requestCodeFilePicker = REQUEST_CODE_STORAGE_GET_FILE

    var onFolderSelected: ((requestCode: Int, folder: DocumentFile) -> Unit)? = null
    var onFileSelected: ((requestCode: Int, file: DocumentFile) -> Unit)? = null

    private fun init() {
        storage.storageAccessCallback = object : StorageAccessCallback {
            override fun onRootPathNotSelected(requestCode: Int, rootPath: String, rootStorageType: StorageType, uri: Uri) {
                AlertDialog.Builder(storage.context)
                    .setMessage(if (rootStorageType == StorageType.SD_CARD) R.string.ss_please_select_root_storage_sdcard else R.string.ss_please_select_root_storage_primary)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        storage.requestStorageAccess(requestCodeStorageAccess, rootStorageType)
                    }.show()
            }

            override fun onRootPathPermissionGranted(requestCode: Int, root: DocumentFile) {
                val context = storage.context
                if (openFolderPickerOnceGranted) {
                    storage.openFolderPicker(requestCodeFolderPicker)
                    Toast.makeText(
                        context,
                        context.getString(R.string.ss_selecting_root_path_success_with_open_folder_picker, root.getAbsolutePath(context)),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.ss_selecting_root_path_success_without_open_folder_picker, root.getAbsolutePath(context)),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                openFolderPickerOnceGranted = false
            }

            override fun onStoragePermissionDenied(requestCode: Int) {
                requestStoragePermission(storage.context) { storage.openFolderPicker(requestCodeFolderPicker) }
            }

            override fun onCanceledByUser(requestCode: Int) {
                openFolderPickerOnceGranted = false
            }
        }

        storage.folderPickerCallback = object : FolderPickerCallback {
            override fun onFolderSelected(requestCode: Int, folder: DocumentFile) {
                onFolderSelected?.invoke(requestCode, folder)
            }

            override fun onStorageAccessDenied(requestCode: Int, folder: DocumentFile?, storageType: StorageType?) {
                if (storageType == null) {
                    onStoragePermissionDenied(requestCode)
                    return
                }
                AlertDialog.Builder(storage.context)
                    .setMessage(R.string.ss_storage_access_denied_confirm)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        openFolderPickerOnceGranted = true
                        storage.requestStorageAccess(requestCodeStorageAccess, storageType)
                    }.show()
            }

            override fun onStoragePermissionDenied(requestCode: Int) {
                requestStoragePermission(storage.context) { storage.openFolderPicker(requestCode) }
            }

            override fun onCanceledByUser(requestCode: Int) {
                openFolderPickerOnceGranted = false
            }
        }

        storage.filePickerCallback = object : FilePickerCallback {
            override fun onStoragePermissionDenied(requestCode: Int, file: DocumentFile?) {
                requestStoragePermission(storage.context) { storage.openFolderPicker(requestCode) }
            }

            override fun onFileSelected(requestCode: Int, file: DocumentFile) {
                onFileSelected?.invoke(requestCode, file)
            }
        }
    }

    fun openFolderPicker(requestCodeFolderPicker: Int = this.requestCodeFolderPicker) {
        this.requestCodeFolderPicker = requestCodeFolderPicker
        storage.openFolderPicker(requestCodeFolderPicker)
    }

    fun openFilePicker(requestCodeFilePicker: Int = this.requestCodeFilePicker, filterMimeType: String = DocumentFileCompat.MIME_TYPE_UNKNOWN) {
        this.requestCodeFilePicker = requestCodeFilePicker
        storage.openFilePicker(requestCodeFilePicker, filterMimeType)
    }

    fun requestStorageAccess(requestCodeStorageAccess: Int = this.requestCodeStorageAccess, openFolderPickerOnceGranted: Boolean = false) {
        this.openFolderPickerOnceGranted = openFolderPickerOnceGranted
        this.requestCodeStorageAccess = requestCodeStorageAccess
        storage.requestStorageAccess(requestCodeStorageAccess)
    }

    fun onSaveInstanceState(outState: Bundle) {
        storage.onSaveInstanceState(outState)
        outState.putBoolean(KEY_OPEN_FOLDER_PICKER_ONCE_GRANTED, openFolderPickerOnceGranted)
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        storage.onRestoreInstanceState(savedInstanceState)
        openFolderPickerOnceGranted = savedInstanceState.getBoolean(KEY_OPEN_FOLDER_PICKER_ONCE_GRANTED)
    }

    companion object {
        const val REQUEST_CODE_STORAGE_ACCESS = 11
        const val REQUEST_CODE_STORAGE_GET_FOLDER = 12
        const val REQUEST_CODE_STORAGE_GET_FILE = 13

        private const val KEY_OPEN_FOLDER_PICKER_ONCE_GRANTED = BuildConfig.LIBRARY_PACKAGE_NAME + ".openFolderPickerOnceGranted"

        @JvmStatic
        fun requestStoragePermission(context: Context, onPermissionsGranted: () -> Unit) {
            Dexter.withContext(context)
                .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(object : BaseMultiplePermissionsListener() {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if (report.areAllPermissionsGranted()) {
                            onPermissionsGranted()
                        } else {
                            Toast.makeText(context, R.string.ss_please_grant_storage_permission, Toast.LENGTH_SHORT).show()
                        }
                    }
                }).check()
        }
    }
}