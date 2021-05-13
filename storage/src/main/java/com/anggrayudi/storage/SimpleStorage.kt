package com.anggrayudi.storage

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.anggrayudi.storage.callback.FilePickerCallback
import com.anggrayudi.storage.callback.FolderPickerCallback
import com.anggrayudi.storage.callback.StorageAccessCallback
import com.anggrayudi.storage.extension.*
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageType
import com.anggrayudi.storage.file.canModify
import timber.log.Timber
import kotlin.concurrent.thread

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version SimpleStorage, v 0.0.1 09/08/20 19.08 by Anggrayudi Hardiannico A.
 */
class SimpleStorage private constructor(private val wrapper: ComponentWrapper) {

    constructor(activity: FragmentActivity, savedState: Bundle? = null) : this(ActivityWrapper(activity)) {
        savedState?.let { onRestoreInstanceState(it) }
    }

    constructor(fragment: Fragment, savedState: Bundle? = null) : this(FragmentWrapper(fragment)) {
        savedState?.let { onRestoreInstanceState(it) }
        (wrapper as FragmentWrapper).storage = this
    }

    var storageAccessCallback: StorageAccessCallback? = null

    var folderPickerCallback: FolderPickerCallback? = null

    var filePickerCallback: FilePickerCallback? = null

    private var requestCodeStorageAccess = 0
    private var requestCodeFolderPicker = 0
    private var requestCodeFilePicker = 0

    val context: Context
        get() = wrapper.context

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
     * the first removable no primary storage. This function requires at least Nougat
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
        if (!hasStoragePermission(wrapper.context)) {
            storageAccessCallback?.onStoragePermissionDenied(requestCode)
            return
        }
        if (initialRootPath == StorageType.EXTERNAL && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !isSdCardPresent) {
            val root = DocumentFileCompat.getRootDocumentFile(wrapper.context, DocumentFileCompat.PRIMARY, true) ?: return
            saveUriPermission(root.uri)
            storageAccessCallback?.onRootPathPermissionGranted(requestCode, root)
            return
        }

        val intent = if (initialRootPath == StorageType.SD_CARD && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sdCardRootAccessIntent
        } else {
            externalStorageRootAccessIntent
        }
        wrapper.startActivityForResult(intent, requestCode)
        requestCodeStorageAccess = requestCode
    }

    /**
     * Makes your app can access [direct file path](https://developer.android.com/training/data-storage/shared/media#direct-file-paths)
     *
     * See [Manage all files on a storage device](https://developer.android.com/training/data-storage/manage-all-files)
     */
    @RequiresPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
    @RequiresApi(Build.VERSION_CODES.R)
    fun requestFullStorageAccess() {
        wrapper.context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    @SuppressLint("InlinedApi")
    fun openFolderPicker(requestCode: Int) {
        requestCodeFolderPicker = requestCode
        if (hasStoragePermission(wrapper.context)) {
            val intent = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            } else {
                externalStorageRootAccessIntent
            }
            wrapper.startActivityForResult(intent, requestCode)
        } else {
            folderPickerCallback?.onStoragePermissionDenied(requestCode)
        }
    }

    fun openFilePicker(requestCode: Int, filterMimeType: String = DocumentFileCompat.MIME_TYPE_UNKNOWN) {
        requestCodeFilePicker = requestCode
        if (hasStorageReadPermission(wrapper.context)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType(filterMimeType)
            wrapper.startActivityForResult(intent, requestCode)
        } else {
            filePickerCallback?.onStoragePermissionDenied(requestCode, null)
        }
    }

    @Suppress("DEPRECATION")
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            requestCodeStorageAccess -> {
                if (resultCode != Activity.RESULT_OK) {
                    storageAccessCallback?.onCanceledByUser(requestCode)
                    return
                }
                val uri = data?.data ?: return
                if (uri.isDownloadsDocument) {
                    if (uri.toString() == DocumentFileCompat.DOWNLOADS_TREE_URI) {
                        saveUriPermission(uri)
                        storageAccessCallback?.onRootPathPermissionGranted(requestCode, wrapper.context.fromTreeUri(uri) ?: return)
                    } else {
                        storageAccessCallback?.onRootPathNotSelected(
                            requestCode,
                            "$externalStoragePath/${Environment.DIRECTORY_DOWNLOADS}",
                            StorageType.EXTERNAL,
                            uri
                        )
                    }
                    return
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !uri.isExternalStorageDocument) {
                    storageAccessCallback?.onRootPathNotSelected(requestCode, externalStoragePath, StorageType.EXTERNAL, uri)
                    return
                }
                val storageId = uri.storageId
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && storageId == DocumentFileCompat.PRIMARY) {
                    saveUriPermission(uri)
                    storageAccessCallback?.onRootPathPermissionGranted(requestCode, wrapper.context.fromTreeUri(uri) ?: return)
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || DocumentFileCompat.isRootUri(uri)) {
                    if (saveUriPermission(uri)) {
                        storageAccessCallback?.onRootPathPermissionGranted(requestCode, wrapper.context.fromTreeUri(uri) ?: return)
                    } else {
                        storageAccessCallback?.onStoragePermissionDenied(requestCode)
                    }
                } else {
                    if (storageId == DocumentFileCompat.PRIMARY) {
                        storageAccessCallback?.onRootPathNotSelected(requestCode, externalStoragePath, StorageType.EXTERNAL, uri)
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            val sm = wrapper.context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                            sm.storageVolumes.firstOrNull { it.isRemovable }?.createAccessIntent(null)?.let {
                                wrapper.startActivityForResult(it, requestCode)
                                return
                            }
                        }
                        storageAccessCallback?.onRootPathNotSelected(requestCode, "/storage/$storageId", StorageType.SD_CARD, uri)
                    }
                }
            }

            requestCodeFolderPicker -> {
                if (resultCode != Activity.RESULT_OK) {
                    folderPickerCallback?.onCanceledByUser(requestCode)
                    return
                }
                val uri = data?.data ?: return
                val folder = wrapper.context.fromTreeUri(uri)
                val storageId = uri.storageId
                val storageType = when (storageId) {
                    "" -> null
                    DocumentFileCompat.PRIMARY -> StorageType.EXTERNAL
                    else -> StorageType.SD_CARD
                }
                if (folder == null || !folder.canModify) {
                    folderPickerCallback?.onStorageAccessDenied(requestCode, null, storageType)
                    return
                }
                if (uri.toString() == DocumentFileCompat.DOWNLOADS_TREE_URI
                    || uri.isExternalStorageDocument
                    && Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                    && storageType == StorageType.SD_CARD
                    && DocumentFileCompat.isRootUri(uri)
                    && !DocumentFileCompat.isStorageUriPermissionGranted(wrapper.context, storageId)
                ) {
                    saveUriPermission(uri)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && storageType == StorageType.EXTERNAL
                    || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && saveUriPermission(uri)
                    || !uri.isExternalStorageDocument && folder.canModify
                    || DocumentFileCompat.isStorageUriPermissionGranted(wrapper.context, storageId)
                ) {
                    folderPickerCallback?.onFolderSelected(requestCode, folder)
                } else {
                    folderPickerCallback?.onStorageAccessDenied(requestCode, folder, storageType)
                }
            }

            requestCodeFilePicker -> {
                if (resultCode != Activity.RESULT_OK) {
                    filePickerCallback?.onCanceledByUser(requestCode)
                    return
                }
                val file = wrapper.context.fromSingleUri(data?.data ?: return)
                if (file == null || !file.canRead()) {
                    filePickerCallback?.onStoragePermissionDenied(requestCode, file)
                } else {
                    filePickerCallback?.onFileSelected(requestCode, file)
                }
            }
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_REQUEST_CODE_STORAGE_ACCESS, requestCodeStorageAccess)
        outState.putInt(KEY_REQUEST_CODE_FOLDER_PICKER, requestCodeFolderPicker)
        outState.putInt(KEY_REQUEST_CODE_FILE_PICKER, requestCodeFilePicker)
        if (wrapper is FragmentWrapper) {
            wrapper.requestCode?.let { outState.putInt(KEY_REQUEST_CODE_FRAGMENT_PICKER, it) }
        }
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        requestCodeStorageAccess = savedInstanceState.getInt(KEY_REQUEST_CODE_STORAGE_ACCESS)
        requestCodeFolderPicker = savedInstanceState.getInt(KEY_REQUEST_CODE_FOLDER_PICKER)
        requestCodeFilePicker = savedInstanceState.getInt(KEY_REQUEST_CODE_FILE_PICKER)
        if (wrapper is FragmentWrapper && savedInstanceState.containsKey(KEY_REQUEST_CODE_FRAGMENT_PICKER)) {
            wrapper.requestCode = savedInstanceState.getInt(KEY_REQUEST_CODE_FRAGMENT_PICKER)
        }
    }

    private fun saveUriPermission(root: Uri) = try {
        val writeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resolver = wrapper.context.contentResolver
        resolver.takePersistableUriPermission(root, writeFlags)
        cleanupRedundantUriPermissions(resolver)
        true
    } catch (e: SecurityException) {
        false
    }

    companion object {

        private const val KEY_REQUEST_CODE_STORAGE_ACCESS = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeStorageAccess"
        private const val KEY_REQUEST_CODE_FOLDER_PICKER = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeFolderPicker"
        private const val KEY_REQUEST_CODE_FILE_PICKER = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeFilePicker"
        private const val KEY_REQUEST_CODE_FRAGMENT_PICKER = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeFragmentPicker"

        @JvmStatic
        @Suppress("DEPRECATION")
        val externalStoragePath: String
            get() = Environment.getExternalStorageDirectory().absolutePath

        @JvmStatic
        val isSdCardPresent: Boolean
            get() = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

        @JvmStatic
        val defaultExternalStorageIntent: Intent
            @SuppressLint("InlinedApi")
            get() = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                if (Build.VERSION.SDK_INT >= 26) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentFileCompat.createDocumentUri(DocumentFileCompat.PRIMARY))
                }
            }

        /**
         * For read and write permissions
         */
        @JvmStatic
        fun hasStoragePermission(context: Context): Boolean {
            return checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && hasStorageReadPermission(context)
        }

        /**
         * For read permission only
         */
        @JvmStatic
        fun hasStorageReadPermission(context: Context): Boolean {
            return checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        @JvmStatic
        fun hasFullDiskAccess(context: Context, storageId: String): Boolean {
            return hasStorageAccess(context, DocumentFileCompat.buildAbsolutePath(storageId, ""))
        }

        /**
         * In API 29+, `/storage/emulated/0` may not be granted for URI permission,
         * but all directories under `/storage/emulated/0/Download` are granted and accessible.
         *
         * @param requiresWriteAccess `true` if you expect this path should be writable
         * @return `true` if you have URI access to this path
         * @see [DocumentFileCompat.buildAbsolutePath]
         * @see [DocumentFileCompat.buildSimplePath]
         */
        @JvmStatic
        @JvmOverloads
        fun hasStorageAccess(context: Context, fullPath: String, requiresWriteAccess: Boolean = true): Boolean {
            return (requiresWriteAccess && hasStoragePermission(context) || !requiresWriteAccess && hasStorageReadPermission(context))
                    && DocumentFileCompat.getAccessibleRootDocumentFile(context, fullPath, requiresWriteAccess) != null
        }

        /**
         * Max persistable URI per app is 128, so cleanup redundant URI permissions. Given the following URIs:
         * 1) `content://com.android.externalstorage.documents/tree/primary%3AMovies`
         * 2) `content://com.android.externalstorage.documents/tree/primary%3AMovies%2FHorror`
         *
         * Then remove the second URI, because it has been covered by the first URI.
         *
         * Read [Count Your SAF Uri Persisted Permissions!](https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html)
         */
        @JvmStatic
        fun cleanupRedundantUriPermissions(resolver: ContentResolver) {
            thread {
                // e.g. content://com.android.externalstorage.documents/tree/primary%3AMusic
                val persistedUris = resolver.persistedUriPermissions
                    .filter { it.isReadPermission && it.isWritePermission && it.uri.isExternalStorageDocument }
                    .map { it.uri }
                val writeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                val uniqueUriParents = DocumentFileCompat.findUniqueParents(persistedUris.mapNotNull { it.path?.substringAfter("/tree/") })
                persistedUris.forEach {
                    if (DocumentFileCompat.buildAbsolutePath(it.path.orEmpty().substringAfter("/tree/")) !in uniqueUriParents) {
                        resolver.releasePersistableUriPermission(it, writeFlags)
                        Timber.d("Removed redundant URI permission => $it")
                    }
                }
            }
        }
    }
}