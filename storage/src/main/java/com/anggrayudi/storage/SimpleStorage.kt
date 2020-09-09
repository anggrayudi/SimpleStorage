package com.anggrayudi.storage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.anggrayudi.storage.callback.FilePickerCallback
import com.anggrayudi.storage.callback.FolderPickerCallback
import com.anggrayudi.storage.callback.StorageAccessCallback
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageType
import com.anggrayudi.storage.file.isModifiable

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version SimpleStorage, v 0.0.1 09/08/20 19.08 by Anggrayudi Hardiannico A.
 */
class SimpleStorage private constructor(private val wrapper: ComponentWrapper) {

    constructor(activity: FragmentActivity) : this(ActivityWrapper(activity))

    constructor(fragment: Fragment) : this(FragmentWrapper(fragment))

    var storageAccessCallback: StorageAccessCallback? = null

    var folderPickerCallback: FolderPickerCallback? = null

    var filePickerCallback: FilePickerCallback? = null

    private var requestCodeStorageAccess = 0
    private var requestCodeFolderPicker = 0
    private var requestCodeFilePicker = 0

    /**
     * It returns an intent to be dispatched via [Activity.startActivityForResult]
     */
    private val externalStorageRootAccessIntent: Intent
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sm = wrapper.context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.primaryStorageVolume.createOpenDocumentTreeIntent()
        } else {
            defaultExternalStorageIntent
        }

    /**
     * It returns an intent to be dispatched via [Activity.startActivityForResult] to access to
     * the first removable no primary storage. This method requires at least Nougat
     * because on previous Android versions there's no reliable way to get the
     * volume/path of SdCard, and no, SdCard != External Storage.
     */
    private val sdCardRootAccessIntent: Intent
        @Suppress("DEPRECATION")
        @RequiresApi(api = Build.VERSION_CODES.N)
        get() {
            val sm = wrapper.context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            return sm.storageVolumes.firstOrNull { it.isRemovable }?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.createOpenDocumentTreeIntent()
                } else {
                    //Access to the entire volume is only available for non-primary volumes
                    if (it.isPrimary) {
                        defaultExternalStorageIntent
                    } else {
                        it.createAccessIntent(null)
                    }
                }
            } ?: defaultExternalStorageIntent
        }

    /**
     * Even though storage permission has been granted via [hasStoragePermission], read and write access may have not been granted yet.
     *
     * @param storageId Use [DocumentFileCompat.PRIMARY] for external storage. Or use SD Card storage ID.
     * @return `true` if storage pemissions and URI permissions are granted for read and write access.
     * @see [DocumentFileCompat.getStorageIds]
     */
    fun isStorageAccessGranted(storageId: String) = DocumentFileCompat.isAccessGranted(wrapper.context, storageId)

    /**
     * Managing files in direct storage requires root access. Thus we need to make sure users select root path.
     *
     * @param initialRootPath It will open [StorageType.EXTERNAL] instead for API 23 and lower, and when no SD Card inserted.
     */
    fun requestStorageAccess(requestCode: Int, initialRootPath: StorageType = StorageType.EXTERNAL) {
        if (initialRootPath == StorageType.EXTERNAL && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !isSdCardPresent) {
            val root = DocumentFileCompat.getRootDocumentFile(wrapper.context, DocumentFileCompat.PRIMARY) ?: return
            saveUriPermission(root.uri)
            storageAccessCallback?.onRootPathPermissionGranted(root)
            return
        }

        val intent = if (initialRootPath == StorageType.SD_CARD && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sdCardRootAccessIntent
        } else {
            externalStorageRootAccessIntent
        }
        wrapper.startActivityForResult(requestCode, intent)
        this.requestCodeStorageAccess = requestCode
    }

    fun openFolderPicker(requestCode: Int) {
        requestCodeFolderPicker = requestCode
        if (hasStoragePermission(wrapper.context)) {
            wrapper.startActivityForResult(requestCode, defaultExternalStorageIntent)
        } else {
            folderPickerCallback?.onStoragePermissionDenied(requestCode)
        }
    }

    fun openFilePicker(requestCode: Int, filterMimeType: String = "*/*") {
        requestCodeFilePicker = requestCode
        if (hasStorageReadPermission(wrapper.context)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType(filterMimeType)
            wrapper.startActivityForResult(requestCode, intent)
        } else {
            filePickerCallback?.onStoragePermissionDenied(requestCode, null)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == requestCodeStorageAccess) {
            if (resultCode != Activity.RESULT_OK) {
                storageAccessCallback?.onCancelledByUser()
                return
            }
            val uri = data?.data ?: return
            if (uri.authority != DocumentFileCompat.EXTERNAL_STORAGE_AUTHORITY) {
                storageAccessCallback?.onRootPathNotSelected(externalStoragePath, StorageType.EXTERNAL)
                return
            }
            val storageId = DocumentFileCompat.getStorageId(uri)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && storageId == DocumentFileCompat.PRIMARY) {
                saveUriPermission(uri)
                storageAccessCallback?.onRootPathPermissionGranted(DocumentFile.fromTreeUri(wrapper.context, uri) ?: return)
                return
            }
            if (DocumentFileCompat.isRootUri(uri)) {
                if (saveUriPermission(uri)) {
                    storageAccessCallback?.onRootPathPermissionGranted(DocumentFile.fromTreeUri(wrapper.context, uri) ?: return)
                } else {
                    storageAccessCallback?.onStoragePermissionDenied()
                }
            } else {
                if (storageId == DocumentFileCompat.PRIMARY) {
                    storageAccessCallback?.onRootPathNotSelected(externalStoragePath, StorageType.EXTERNAL)
                } else {
                    storageAccessCallback?.onRootPathNotSelected("$storageId:", StorageType.SD_CARD)
                }
            }
        } else if (requestCode == requestCodeFolderPicker) {
            if (resultCode != Activity.RESULT_OK) {
                folderPickerCallback?.onCancelledByUser(requestCode)
                return
            }
            val uri = data?.data ?: return
            val folder = try {
                DocumentFile.fromTreeUri(wrapper.context, uri)
            } catch (e: SecurityException) {
                null
            }
            val storageId = DocumentFileCompat.getStorageId(uri)
            val storageType = when (storageId) {
                "" -> null
                DocumentFileCompat.PRIMARY -> StorageType.EXTERNAL
                else -> StorageType.SD_CARD
            }
            if (folder != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && storageType == StorageType.EXTERNAL
                        || uri.authority != DocumentFileCompat.EXTERNAL_STORAGE_AUTHORITY && folder.isModifiable
                        || DocumentFileCompat.isStorageUriPermissionGranted(wrapper.context, storageId))
            ) {
                folderPickerCallback?.onFolderSelected(requestCode, folder)
            } else {
                folderPickerCallback?.onStorageAccessDenied(requestCode, folder, storageType)
            }
        } else if (requestCode == requestCodeFilePicker) {
            if (resultCode != Activity.RESULT_OK) {
                filePickerCallback?.onCancelledByUser(requestCode)
                return
            }
            val uri = data?.data ?: return
            val file = try {
                DocumentFile.fromSingleUri(wrapper.context, uri)
            } catch (e: SecurityException) {
                null
            }
            if (file == null || !file.canRead()) {
                filePickerCallback?.onStoragePermissionDenied(requestCode, file)
            } else {
                filePickerCallback?.onFileSelected(requestCode, file)
            }
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(REQUEST_CODE_STORAGE_ACCESS, requestCodeStorageAccess)
        outState.putInt(REQUEST_CODE_FOLDER_PICKER, requestCodeFolderPicker)
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        requestCodeStorageAccess = savedInstanceState.getInt(REQUEST_CODE_STORAGE_ACCESS)
        requestCodeFolderPicker = savedInstanceState.getInt(REQUEST_CODE_FOLDER_PICKER)
    }

    private fun saveUriPermission(root: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            wrapper.context.contentResolver.takePersistableUriPermission(root, takeFlags)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {

        private const val REQUEST_CODE_STORAGE_ACCESS = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeStorageAccess"
        private const val REQUEST_CODE_FOLDER_PICKER = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeFolderPicker"

        @Suppress("DEPRECATION"
        val externalStoragePath: String
            get() = Environment.getExternalStorageDirectory().absolutePath

        val isSdCardPresent: Boolean
            get() = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

        val defaultExternalStorageIntent: Intent
            get() = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                if (Build.VERSION.SDK_INT >= 26) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentFileCompat.createDocumentUri(DocumentFileCompat.PRIMARY))
                }
            }

        /**
         * For read and write permissions
         */
        fun hasStoragePermission(context: Context): Boolean {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && hasStorageReadPermission(context)
        }

        /**
         * For read permission only
         */
        fun hasStorageReadPermission(context: Context): Boolean {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}