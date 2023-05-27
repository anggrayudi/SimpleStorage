package com.anggrayudi.storage

import android.Manifest
import android.annotation.SuppressLint
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
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onCancel
import com.afollestad.materialdialogs.files.folderChooser
import com.anggrayudi.storage.callback.*
import com.anggrayudi.storage.extension.*
import com.anggrayudi.storage.file.*
import com.anggrayudi.storage.file.StorageId.PRIMARY
import java.io.File
import kotlin.concurrent.thread

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version SimpleStorage, v 0.0.1 09/08/20 19.08 by Anggrayudi Hardiannico A.
 */
class SimpleStorage private constructor(private val wrapper: ComponentWrapper) {

    // For unknown Activity type
    constructor(activity: Activity, savedState: Bundle? = null) : this(ActivityWrapper(activity)) {
        savedState?.let { onRestoreInstanceState(it) }
    }

    constructor(activity: ComponentActivity, savedState: Bundle? = null) : this(ComponentActivityWrapper(activity)) {
        savedState?.let { onRestoreInstanceState(it) }
        (wrapper as ComponentActivityWrapper).storage = this
    }

    constructor(fragment: Fragment, savedState: Bundle? = null) : this(FragmentWrapper(fragment)) {
        savedState?.let { onRestoreInstanceState(it) }
        (wrapper as FragmentWrapper).storage = this
    }

    var isCleanupRedundantUriPermissions: Boolean = true

    var storageAccessCallback: StorageAccessCallback? = null

    var folderPickerCallback: FolderPickerCallback? = null

    var filePickerCallback: FilePickerCallback? = null

    var createFileCallback: CreateFileCallback? = null

    var fileReceiverCallback: FileReceiverCallback? = null

    var requestCodeStorageAccess = 1
        set(value) {
            field = value
            checkRequestCode()
        }

    var requestCodeFolderPicker = 2
        set(value) {
            field = value
            checkRequestCode()
        }

    var requestCodeFilePicker = 3
        set(value) {
            field = value
            checkRequestCode()
        }

    var requestCodeCreateFile = 4
        set(value) {
            field = value
            checkRequestCode()
        }

    val context: Context
        get() = wrapper.context

    /**
     * It returns an intent to be dispatched via [Activity.startActivityForResult]
     */
    private val externalStorageRootAccessIntent: Intent
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.primaryStorageVolume.createOpenDocumentTreeIntent()
        } else {
            getDefaultExternalStorageIntent(context)
        }

    /**
     * It returns an intent to be dispatched via [Activity.startActivityForResult] to access to
     * the first removable no primary storage. This function requires at least Nougat
     * because on previous Android versions there's no reliable way to get the
     * volume/path of SdCard, and of course, SdCard != External Storage.
     */
    private val sdCardRootAccessIntent: Intent
        @Suppress("DEPRECATION")
        @RequiresApi(api = Build.VERSION_CODES.N)
        get() {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            return sm.storageVolumes.firstOrNull { it.isRemovable }?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.createOpenDocumentTreeIntent()
                } else {
                    //Access to the entire volume is only available for non-primary volumes
                    if (it.isPrimary) {
                        getDefaultExternalStorageIntent(context)
                    } else {
                        it.createAccessIntent(null)
                    }
                }
            } ?: getDefaultExternalStorageIntent(context)
        }

    /**
     * Even though storage permission has been granted via [hasStoragePermission], read and write access may have not been granted yet.
     *
     * @param storageId Use [PRIMARY] for external storage. Or use SD Card storage ID.
     * @return `true` if storage permissions and URI permissions are granted for read and write access.
     * @see [DocumentFileCompat.getStorageIds]
     */
    fun isStorageAccessGranted(storageId: String) = DocumentFileCompat.isAccessGranted(context, storageId)

    private var expectedStorageTypeForAccessRequest = StorageType.UNKNOWN

    private var expectedBasePathForAccessRequest: String? = null

    /**
     * Managing files in direct storage requires root access. Thus we need to make sure users select root path.
     *
     * @param initialPath only takes effect on API 30+
     * @param expectedStorageType for example, if you set [StorageType.SD_CARD] but the user selects [StorageType.EXTERNAL], then
     * trigger [StorageAccessCallback.onRootPathNotSelected]. Set to [StorageType.UNKNOWN] to accept any storage type.
     * @param expectedBasePath applicable for API 30+ only, because Android 11 does not allow selecting the root path.
     */
    @RequiresApi(21)
    @JvmOverloads
    fun requestStorageAccess(
        requestCode: Int = requestCodeStorageAccess,
        initialPath: FileFullPath? = null,
        expectedStorageType: StorageType = StorageType.UNKNOWN,
        expectedBasePath: String = ""
    ) {
        initialPath?.checkIfStorageIdIsAccessibleInSafSelector()
        if (expectedStorageType == StorageType.DATA) {
            throw IllegalArgumentException("Cannot use StorageType.DATA because it is never available in Storage Access Framework's folder selector.")
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (hasStoragePermission(context)) {
                if (expectedStorageType == StorageType.EXTERNAL && !isSdCardPresent) {
                    val root = DocumentFileCompat.getRootDocumentFile(context, PRIMARY, true) ?: return
                    saveUriPermission(root.uri)
                    storageAccessCallback?.onRootPathPermissionGranted(requestCode, root)
                    return
                }
            } else {
                storageAccessCallback?.onStoragePermissionDenied(requestCode)
                return
            }
        }

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            externalStorageRootAccessIntent.also { addInitialPathToIntent(it, initialPath) }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && expectedStorageType == StorageType.SD_CARD) {
            sdCardRootAccessIntent
        } else {
            externalStorageRootAccessIntent
        }

        if (wrapper.startActivityForResult(intent, requestCode)) {
            requestCodeStorageAccess = requestCode
            expectedStorageTypeForAccessRequest = expectedStorageType
            expectedBasePathForAccessRequest = expectedBasePath
        } else {
            storageAccessCallback?.onActivityHandlerNotFound(requestCode, intent)
        }
    }

    /**
     * Makes your app can access [direct file paths](https://developer.android.com/training/data-storage/shared/media#direct-file-paths)
     *
     * See [Manage all files on a storage device](https://developer.android.com/training/data-storage/manage-all-files)
     */
    @RequiresPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
    @RequiresApi(Build.VERSION_CODES.R)
    fun requestFullStorageAccess() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    /**
     * Show interactive UI to create a file.
     * @param initialPath only takes effect on API 26+
     */
    @JvmOverloads
    fun createFile(
        mimeType: String,
        fileName: String? = null,
        initialPath: FileFullPath? = null,
        requestCode: Int = requestCodeCreateFile
    ) {
        initialPath?.checkIfStorageIdIsAccessibleInSafSelector()
        requestCodeCreateFile = requestCode
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mimeType)
        addInitialPathToIntent(intent, initialPath)
        fileName?.let { intent.putExtra(Intent.EXTRA_TITLE, it) }
        if (!wrapper.startActivityForResult(intent, requestCode))
            createFileCallback?.onActivityHandlerNotFound(requestCode, intent)
    }

    /**
     * @param initialPath only works for API 26+
     */
    @SuppressLint("InlinedApi")
    @JvmOverloads
    fun openFolderPicker(requestCode: Int = requestCodeFolderPicker, initialPath: FileFullPath? = null) {
        initialPath?.checkIfStorageIdIsAccessibleInSafSelector()
        requestCodeFolderPicker = requestCode

        if (Build.VERSION.SDK_INT < 21) {
            MaterialDialog(context).folderChooser(
                context,
                initialDirectory = initialPath?.let { File(it.absolutePath) } ?: lastVisitedFolder,
                allowFolderCreation = true,
                selection = { _, file ->
                    lastVisitedFolder = file
                    folderPickerCallback?.onFolderSelected(requestCode, DocumentFile.fromFile(file))
                }
            ).negativeButton(android.R.string.cancel, click = { it.cancel() })
                .onCancel { folderPickerCallback?.onCanceledByUser(requestCode) }
                .show()
            return
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P || hasStoragePermission(context)) {
            val intent = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            } else {
                externalStorageRootAccessIntent
            }
            addInitialPathToIntent(intent, initialPath)
            if (!wrapper.startActivityForResult(intent, requestCode))
                folderPickerCallback?.onActivityHandlerNotFound(requestCode, intent)
        } else {
            folderPickerCallback?.onStoragePermissionDenied(requestCode)
        }
    }

    @Suppress("DEPRECATION")
    private var lastVisitedFolder: File = Environment.getExternalStorageDirectory()

    /**
     * @param initialPath only takes effect on API 26+
     */
    @JvmOverloads
    fun openFilePicker(
        requestCode: Int = requestCodeFilePicker,
        allowMultiple: Boolean = false,
        initialPath: FileFullPath? = null,
        vararg filterMimeTypes: String
    ) {
        initialPath?.checkIfStorageIdIsAccessibleInSafSelector()
        requestCodeFilePicker = requestCode

        val intent = if (Build.VERSION.SDK_INT < 21) {
            Intent(Intent.ACTION_GET_CONTENT)
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT)
        }
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        if (filterMimeTypes.size > 1) {
            intent.setType(MimeType.UNKNOWN)
                .putExtra(Intent.EXTRA_MIME_TYPES, filterMimeTypes)
        } else {
            intent.type = filterMimeTypes.firstOrNull() ?: MimeType.UNKNOWN
        }
        addInitialPathToIntent(intent, initialPath)
        if (!wrapper.startActivityForResult(intent, requestCode))
            filePickerCallback?.onActivityHandlerNotFound(requestCode, intent)
    }

    private fun addInitialPathToIntent(intent: Intent, initialPath: FileFullPath?) {
        if (Build.VERSION.SDK_INT >= 26) {
            initialPath?.toDocumentUri(context)?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
    }

    private fun handleActivityResultForStorageAccess(requestCode: Int, uri: Uri) {
        val storageId = uri.getStorageId(context)
        val storageType = StorageType.fromStorageId(storageId)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            val selectedFolder = context.fromTreeUri(uri) ?: return
            if (!expectedStorageTypeForAccessRequest.isExpected(storageType) ||
                !expectedBasePathForAccessRequest.isNullOrEmpty() && selectedFolder.getBasePath(context) != expectedBasePathForAccessRequest
            ) {
                storageAccessCallback?.onExpectedStorageNotSelected(
                    requestCode,
                    selectedFolder,
                    storageType,
                    expectedBasePathForAccessRequest!!,
                    expectedStorageTypeForAccessRequest
                )
                return
            }
        } else if (!expectedStorageTypeForAccessRequest.isExpected(storageType)) {
            val rootPath = context.fromTreeUri(uri)?.getAbsolutePath(context).orEmpty()
            storageAccessCallback?.onRootPathNotSelected(requestCode, rootPath, uri, storageType, expectedStorageTypeForAccessRequest)
            return
        }

        if (uri.isDownloadsDocument) {
            if (uri.toString() == DocumentFileCompat.DOWNLOADS_TREE_URI) {
                saveUriPermission(uri)
                storageAccessCallback?.onRootPathPermissionGranted(requestCode, context.fromTreeUri(uri) ?: return)
            } else {
                storageAccessCallback?.onRootPathNotSelected(
                    requestCode,
                    PublicDirectory.DOWNLOADS.absolutePath,
                    uri,
                    StorageType.EXTERNAL,
                    expectedStorageTypeForAccessRequest
                )
            }
            return
        }

        if (uri.isDocumentsDocument) {
            if (uri.toString() == DocumentFileCompat.DOCUMENTS_TREE_URI) {
                saveUriPermission(uri)
                storageAccessCallback?.onRootPathPermissionGranted(requestCode, context.fromTreeUri(uri) ?: return)
            } else {
                storageAccessCallback?.onRootPathNotSelected(
                    requestCode,
                    PublicDirectory.DOCUMENTS.absolutePath,
                    uri,
                    StorageType.EXTERNAL,
                    expectedStorageTypeForAccessRequest
                )
            }
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !uri.isExternalStorageDocument) {
            storageAccessCallback?.onRootPathNotSelected(requestCode, externalStoragePath, uri, StorageType.EXTERNAL, expectedStorageTypeForAccessRequest)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && storageId == PRIMARY) {
            saveUriPermission(uri)
            storageAccessCallback?.onRootPathPermissionGranted(requestCode, context.fromTreeUri(uri) ?: return)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || DocumentFileCompat.isRootUri(uri)) {
            if (saveUriPermission(uri)) {
                storageAccessCallback?.onRootPathPermissionGranted(requestCode, context.fromTreeUri(uri) ?: return)
            } else {
                storageAccessCallback?.onStoragePermissionDenied(requestCode)
            }
        } else {
            if (storageId == PRIMARY) {
                storageAccessCallback?.onRootPathNotSelected(requestCode, externalStoragePath, uri, StorageType.EXTERNAL, expectedStorageTypeForAccessRequest)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                    @Suppress("DEPRECATION")
                    sm.storageVolumes.firstOrNull { !it.isPrimary }?.createAccessIntent(null)?.let {
                        if (!wrapper.startActivityForResult(it, requestCode)) {
                            storageAccessCallback?.onActivityHandlerNotFound(requestCode, it)
                        }
                        return
                    }
                }
                storageAccessCallback?.onRootPathNotSelected(requestCode, "/storage/$storageId", uri, StorageType.SD_CARD, expectedStorageTypeForAccessRequest)
            }
        }
    }

    private fun handleActivityResultForFolderPicker(requestCode: Int, uri: Uri) {
        val folder = context.fromTreeUri(uri)
        val storageId = uri.getStorageId(context)
        val storageType = StorageType.fromStorageId(storageId)

        if (folder == null || !folder.canModify(context)) {
            folderPickerCallback?.onStorageAccessDenied(requestCode, folder, storageType, storageId)
            return
        }
        if (uri.toString().let { it == DocumentFileCompat.DOWNLOADS_TREE_URI || it == DocumentFileCompat.DOCUMENTS_TREE_URI }
            || DocumentFileCompat.isRootUri(uri)
            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && storageType == StorageType.SD_CARD || Build.VERSION.SDK_INT == Build.VERSION_CODES.Q)
            && !DocumentFileCompat.isStorageUriPermissionGranted(context, storageId)
        ) {
            saveUriPermission(uri)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && storageType == StorageType.EXTERNAL
            || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && saveUriPermission(uri)
            || folder.canModify(context) && (uri.isDocumentsDocument || !uri.isExternalStorageDocument)
            || DocumentFileCompat.isStorageUriPermissionGranted(context, storageId)
        ) {
            folderPickerCallback?.onFolderSelected(requestCode, folder)
        } else {
            folderPickerCallback?.onStorageAccessDenied(requestCode, folder, storageType, storageId)
        }
    }

    private fun intentToDocumentFiles(intent: Intent?): List<DocumentFile> {
        val uris = intent?.clipData?.run {
            val list = mutableListOf<Uri>()
            for (i in 0 until itemCount) {
                list.add(getItemAt(i).uri)
            }
            list.takeIf { it.isNotEmpty() }
        } ?: listOf(intent?.data ?: return emptyList())

        return uris.mapNotNull { uri ->
            if (uri.isDownloadsDocument && Build.VERSION.SDK_INT < 28 && uri.path?.startsWith("/document/raw:") == true) {
                val fullPath = uri.path.orEmpty().substringAfterLast("/document/raw:")
                DocumentFile.fromFile(File(fullPath))
            } else context.fromSingleUri(uri)?.let { file ->
                // content://com.android.externalstorage.documents/document/15FA-160C%3Aabc.txt
                if (Build.VERSION.SDK_INT < 21 && file.getStorageId(context).matches(DocumentFileCompat.SD_CARD_STORAGE_ID_REGEX)) {
                    DocumentFile.fromFile(DocumentFileCompat.getKitkatSdCardRootFile(file.getBasePath(context)))
                } else {
                    file
                }
            }
        }.filter { it.isFile }
    }

    fun checkIfFileReceived(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val files = intentToDocumentFiles(intent)
                if (files.isEmpty()) {
                    fileReceiverCallback?.onNonFileReceived(intent)
                } else {
                    fileReceiverCallback?.onFileReceived(files)
                }
            }
        }
    }

    private fun handleActivityResultForFilePicker(requestCode: Int, data: Intent) {
        val files = intentToDocumentFiles(data)
        if (files.isNotEmpty() && files.all { it.canRead() }) {
            filePickerCallback?.onFileSelected(requestCode, files)
        } else {
            filePickerCallback?.onStoragePermissionDenied(requestCode, files)
        }
    }

    private fun handleActivityResultForCreateFile(requestCode: Int, uri: Uri) {
        DocumentFileCompat.fromUri(context, uri)?.let {
            createFileCallback?.onFileCreated(requestCode, it)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        checkRequestCode()

        when (requestCode) {
            requestCodeStorageAccess -> {
                if (resultCode == Activity.RESULT_OK) {
                    handleActivityResultForStorageAccess(requestCode, data?.data ?: return)
                } else {
                    storageAccessCallback?.onCanceledByUser(requestCode)
                }
            }

            requestCodeFolderPicker -> {
                if (resultCode == Activity.RESULT_OK) {
                    handleActivityResultForFolderPicker(requestCode, data?.data ?: return)
                } else {
                    folderPickerCallback?.onCanceledByUser(requestCode)
                }
            }

            requestCodeFilePicker -> {
                if (resultCode == Activity.RESULT_OK) {
                    handleActivityResultForFilePicker(requestCode, data ?: return)
                } else {
                    filePickerCallback?.onCanceledByUser(requestCode)
                }
            }

            requestCodeCreateFile -> {
                // resultCode is always OK for creating files
                val uri = data?.data
                if (uri != null) {
                    handleActivityResultForCreateFile(requestCode, uri)
                } else {
                    createFileCallback?.onCanceledByUser(requestCode)
                }
            }
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_LAST_VISITED_FOLDER, lastVisitedFolder.path)
        outState.putString(KEY_EXPECTED_BASE_PATH_FOR_ACCESS_REQUEST, expectedBasePathForAccessRequest)
        outState.putInt(KEY_EXPECTED_STORAGE_TYPE_FOR_ACCESS_REQUEST, expectedStorageTypeForAccessRequest.ordinal)
        outState.putInt(KEY_REQUEST_CODE_STORAGE_ACCESS, requestCodeStorageAccess)
        outState.putInt(KEY_REQUEST_CODE_FOLDER_PICKER, requestCodeFolderPicker)
        outState.putInt(KEY_REQUEST_CODE_FILE_PICKER, requestCodeFilePicker)
        outState.putInt(KEY_REQUEST_CODE_CREATE_FILE, requestCodeCreateFile)
        if (wrapper is FragmentWrapper) {
            outState.putInt(KEY_REQUEST_CODE_FRAGMENT_PICKER, wrapper.requestCode)
        }
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.getString(KEY_LAST_VISITED_FOLDER)?.let { lastVisitedFolder = File(it) }
        expectedBasePathForAccessRequest = savedInstanceState.getString(KEY_EXPECTED_BASE_PATH_FOR_ACCESS_REQUEST)
        expectedStorageTypeForAccessRequest = StorageType.values()[savedInstanceState.getInt(KEY_EXPECTED_STORAGE_TYPE_FOR_ACCESS_REQUEST)]
        requestCodeStorageAccess = savedInstanceState.getInt(KEY_REQUEST_CODE_STORAGE_ACCESS)
        requestCodeFolderPicker = savedInstanceState.getInt(KEY_REQUEST_CODE_FOLDER_PICKER)
        requestCodeFilePicker = savedInstanceState.getInt(KEY_REQUEST_CODE_FILE_PICKER)
        requestCodeCreateFile = savedInstanceState.getInt(KEY_REQUEST_CODE_CREATE_FILE)
        if (wrapper is FragmentWrapper && savedInstanceState.containsKey(KEY_REQUEST_CODE_FRAGMENT_PICKER)) {
            wrapper.requestCode = savedInstanceState.getInt(KEY_REQUEST_CODE_FRAGMENT_PICKER)
        }
    }

    private fun checkRequestCode() {
        val set = setOf(requestCodeFilePicker, requestCodeFolderPicker, requestCodeStorageAccess, requestCodeCreateFile)
        if (set.size < 4) {
            throw IllegalArgumentException(
                "Request codes must be unique. File picker=$requestCodeFilePicker, Folder picker=$requestCodeFolderPicker, " +
                        "Storage access=$requestCodeStorageAccess, Create file=$requestCodeCreateFile"
            )
        }
    }

    private fun saveUriPermission(root: Uri) = try {
        val writeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(root, writeFlags)
        if (isCleanupRedundantUriPermissions) {
            cleanupRedundantUriPermissions(context.applicationContext)
        }
        true
    } catch (e: SecurityException) {
        false
    }

    companion object {

        private const val KEY_REQUEST_CODE_STORAGE_ACCESS = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeStorageAccess"
        private const val KEY_REQUEST_CODE_FOLDER_PICKER = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeFolderPicker"
        private const val KEY_REQUEST_CODE_FILE_PICKER = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeFilePicker"
        private const val KEY_REQUEST_CODE_CREATE_FILE = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeCreateFile"
        private const val KEY_REQUEST_CODE_FRAGMENT_PICKER = BuildConfig.LIBRARY_PACKAGE_NAME + ".requestCodeFragmentPicker"
        private const val KEY_EXPECTED_STORAGE_TYPE_FOR_ACCESS_REQUEST = BuildConfig.LIBRARY_PACKAGE_NAME + ".expectedStorageTypeForAccessRequest"
        private const val KEY_EXPECTED_BASE_PATH_FOR_ACCESS_REQUEST = BuildConfig.LIBRARY_PACKAGE_NAME + ".expectedBasePathForAccessRequest"
        private const val KEY_LAST_VISITED_FOLDER = BuildConfig.LIBRARY_PACKAGE_NAME + ".lastVisitedFolder"
        private const val TAG = "SimpleStorage"

        const val KITKAT_SD_CARD_PATH = "/storage/sdcard"

        @JvmStatic
        @Suppress("DEPRECATION")
        val externalStoragePath: String
            get() = Environment.getExternalStorageDirectory().absolutePath

        @JvmStatic
        val isSdCardPresent: Boolean
            get() = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

        @JvmStatic
        @SuppressLint("InlinedApi")
        fun getDefaultExternalStorageIntent(context: Context): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                if (Build.VERSION.SDK_INT >= 26) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, context.fromTreeUri(DocumentFileCompat.createDocumentUri(PRIMARY))?.uri)
                }
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
            return hasStorageAccess(context, DocumentFileCompat.buildAbsolutePath(context, storageId, ""))
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
        fun cleanupRedundantUriPermissions(context: Context) {
            thread {
                val resolver = context.contentResolver
                // e.g. content://com.android.externalstorage.documents/tree/primary%3AMusic
                val persistedUris = resolver.persistedUriPermissions
                    .filter { it.isReadPermission && it.isWritePermission && it.uri.isExternalStorageDocument }
                    .map { it.uri }
                val writeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                val uniqueUriParents = DocumentFileCompat.findUniqueParents(context, persistedUris.mapNotNull { it.path?.substringAfter("/tree/") })
                persistedUris.forEach {
                    if (DocumentFileCompat.buildAbsolutePath(context, it.path.orEmpty().substringAfter("/tree/")) !in uniqueUriParents) {
                        resolver.releasePersistableUriPermission(it, writeFlags)
                        Log.d(TAG, "Removed redundant URI permission => $it")
                    }
                }
            }
        }
    }
}