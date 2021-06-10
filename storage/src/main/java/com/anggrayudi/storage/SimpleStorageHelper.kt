package com.anggrayudi.storage

import android.Manifest
import android.content.Context
import android.content.Intent
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

    private var originalRequestCode = 0
    private var pickerToOpenOnceGranted = 0
    private var filterMimeTypes: Set<String>? = null

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

    var onStorageAccessGranted: ((requestCode: Int, root: DocumentFile) -> Unit)? = null
    var onFolderSelected: ((requestCode: Int, folder: DocumentFile) -> Unit)? = null
    var onFileSelected: ((requestCode: Int, file: DocumentFile) -> Unit)? = null

    private fun init() {
        storage.storageAccessCallback = object : StorageAccessCallback {
            override fun onRootPathNotSelected(requestCode: Int, rootPath: String, rootStorageType: StorageType, uri: Uri) {
                AlertDialog.Builder(storage.context)
                    .setCancelable(false)
                    .setMessage(if (rootStorageType == StorageType.SD_CARD) R.string.ss_please_select_root_storage_sdcard else R.string.ss_please_select_root_storage_primary)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> reset() }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        storage.requestStorageAccess(initialRootPath = rootStorageType)
                    }.show()
            }

            override fun onRootPathPermissionGranted(requestCode: Int, root: DocumentFile) {
                // if the original request was only asking for storage access, then stop here
                if (requestCode == originalRequestCode) {
                    reset()
                    onStorageAccessGranted?.invoke(requestCode, root)
                    return
                }

                val context = storage.context
                val toastFilePicker: () -> Unit = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.ss_selecting_root_path_success_with_open_folder_picker, root.getAbsolutePath(context)),
                        Toast.LENGTH_LONG
                    ).show()
                }

                when (pickerToOpenOnceGranted) {
                    TYPE_FILE_PICKER -> {
                        storage.openFilePicker(filterMimeTypes = filterMimeTypes.orEmpty().toTypedArray())
                        toastFilePicker()
                    }
                    TYPE_FOLDER_PICKER -> {
                        storage.openFolderPicker()
                        toastFilePicker()
                    }
                    else -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.ss_selecting_root_path_success_without_open_folder_picker, root.getAbsolutePath(context)),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                reset()
            }

            override fun onStoragePermissionDenied(requestCode: Int) {
                requestStoragePermission(storage.context) { if (it) storage.openFolderPicker() else reset() }
            }

            override fun onCanceledByUser(requestCode: Int) {
                reset()
            }

            override fun onActivityHandlerNotFound(intent: Intent) {
                handleMissingActivityHandler()
            }
        }

        storage.folderPickerCallback = object : FolderPickerCallback {
            override fun onFolderSelected(requestCode: Int, folder: DocumentFile) {
                reset()
                onFolderSelected?.invoke(requestCode, folder)
            }

            override fun onStorageAccessDenied(requestCode: Int, folder: DocumentFile?, storageType: StorageType) {
                if (storageType == StorageType.UNKNOWN) {
                    onStoragePermissionDenied(requestCode)
                    return
                }
                AlertDialog.Builder(storage.context)
                    .setCancelable(false)
                    .setMessage(R.string.ss_storage_access_denied_confirm)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> reset() }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        storage.requestStorageAccess(initialRootPath = storageType)
                    }.show()
            }

            override fun onStoragePermissionDenied(requestCode: Int) {
                requestStoragePermission(storage.context) { if (it) storage.openFolderPicker() else reset() }
            }

            override fun onCanceledByUser(requestCode: Int) {
                reset()
            }

            override fun onActivityHandlerNotFound(intent: Intent) {
                handleMissingActivityHandler()
            }
        }

        storage.filePickerCallback = object : FilePickerCallback {
            override fun onStoragePermissionDenied(requestCode: Int, file: DocumentFile?) {
                requestStoragePermission(storage.context) { if (it) storage.openFilePicker() else reset() }
            }

            override fun onFileSelected(requestCode: Int, file: DocumentFile) {
                reset()
                onFileSelected?.invoke(requestCode, file)
            }

            override fun onCanceledByUser(requestCode: Int) {
                reset()
            }

            override fun onActivityHandlerNotFound(intent: Intent) {
                handleMissingActivityHandler()
            }
        }
    }

    private fun reset() {
        pickerToOpenOnceGranted = 0
        originalRequestCode = 0
        filterMimeTypes = null
    }

    private fun handleMissingActivityHandler() {
        reset()
        Toast.makeText(storage.context, R.string.ss_missing_saf_activity_handler, Toast.LENGTH_SHORT).show()
    }

    @JvmOverloads
    fun openFolderPicker(requestCode: Int = storage.requestCodeFolderPicker) {
        pickerToOpenOnceGranted = TYPE_FOLDER_PICKER
        originalRequestCode = requestCode
        storage.openFolderPicker(requestCode)
    }

    @JvmOverloads
    fun openFilePicker(requestCode: Int = storage.requestCodeFilePicker, vararg filterMimeTypes: String) {
        pickerToOpenOnceGranted = TYPE_FILE_PICKER
        originalRequestCode = requestCode
        filterMimeTypes.toSet().let {
            this.filterMimeTypes = it
            storage.openFilePicker(requestCode, *it.toTypedArray())
        }
    }

    @JvmOverloads
    fun requestStorageAccess(requestCode: Int = storage.requestCodeStorageAccess) {
        pickerToOpenOnceGranted = 0
        originalRequestCode = requestCode
        storage.requestStorageAccess(requestCode)
    }

    fun onSaveInstanceState(outState: Bundle) {
        storage.onSaveInstanceState(outState)
        outState.putInt(KEY_ORIGINAL_REQUEST_CODE, originalRequestCode)
        outState.putInt(KEY_OPEN_FOLDER_PICKER_ONCE_GRANTED, pickerToOpenOnceGranted)
        filterMimeTypes?.let { outState.putStringArray(KEY_FILTER_MIME_TYPES, it.toTypedArray()) }
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        storage.onRestoreInstanceState(savedInstanceState)
        originalRequestCode = savedInstanceState.getInt(KEY_ORIGINAL_REQUEST_CODE)
        pickerToOpenOnceGranted = savedInstanceState.getInt(KEY_OPEN_FOLDER_PICKER_ONCE_GRANTED)
        filterMimeTypes = savedInstanceState.getStringArray(KEY_FILTER_MIME_TYPES)?.toSet()
    }

    companion object {
        const val TYPE_FILE_PICKER = 1
        const val TYPE_FOLDER_PICKER = 2

        private const val KEY_OPEN_FOLDER_PICKER_ONCE_GRANTED = BuildConfig.LIBRARY_PACKAGE_NAME + ".pickerToOpenOnceGranted"
        private const val KEY_ORIGINAL_REQUEST_CODE = BuildConfig.LIBRARY_PACKAGE_NAME + ".originalRequestCode"
        private const val KEY_FILTER_MIME_TYPES = BuildConfig.LIBRARY_PACKAGE_NAME + ".filterMimeTypes"

        @JvmStatic
        fun requestStoragePermission(context: Context, onPermissionsResult: (Boolean) -> Unit) {
            Dexter.withContext(context)
                .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(object : BaseMultiplePermissionsListener() {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        val granted = report.areAllPermissionsGranted()
                        if (!granted) {
                            Toast.makeText(context, R.string.ss_please_grant_storage_permission, Toast.LENGTH_SHORT).show()
                        }
                        onPermissionsResult(granted)
                    }
                }).check()
        }
    }
}