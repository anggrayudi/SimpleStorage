package com.anggrayudi.storage

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import com.anggrayudi.storage.SimpleStorage.Companion.hasStoragePermission
import com.anggrayudi.storage.callback.CreateFileCallback
import com.anggrayudi.storage.callback.FilePickerCallback
import com.anggrayudi.storage.callback.FileReceiverCallback
import com.anggrayudi.storage.callback.FolderPickerCallback
import com.anggrayudi.storage.callback.StorageAccessCallback
import com.anggrayudi.storage.contract.FileCreationContract
import com.anggrayudi.storage.contract.FileCreationResult
import com.anggrayudi.storage.contract.FilePickerResult
import com.anggrayudi.storage.contract.FolderPickerResult
import com.anggrayudi.storage.contract.OpenFilePickerContract
import com.anggrayudi.storage.contract.OpenFolderPickerContract
import com.anggrayudi.storage.contract.RequestStorageAccessContract
import com.anggrayudi.storage.contract.RequestStorageAccessResult
import com.anggrayudi.storage.contract.StoragePermissionDeniedException
import com.anggrayudi.storage.contract.intentToDocumentFiles
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.FileFullPath
import com.anggrayudi.storage.file.StorageId.PRIMARY
import com.anggrayudi.storage.file.StorageType
import java.io.File

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version SimpleStorage, v 0.0.1 09/08/20 19.08 by Anggrayudi Hardiannico A.
 */
@Deprecated(
  "Superseded in v3 by StorageAccessManager and the contracts in com.anggrayudi.storage.contract. This class still relies on startActivityForResult and request codes. See MIGRATION.md.",
  ReplaceWith("com.anggrayudi.storage.access.StorageAccessManager"),
)
public class SimpleStorage private constructor(private val wrapper: ComponentWrapper) {

  // For unknown Activity type
  public constructor(
    activity: Activity,
    savedState: Bundle? = null,
  ) : this(ActivityWrapper(activity)) {
    savedState?.let { onRestoreInstanceState(it) }
  }

  public constructor(
    activity: ComponentActivity,
    savedState: Bundle? = null,
  ) : this(ComponentActivityWrapper(activity)) {
    savedState?.let { onRestoreInstanceState(it) }
    (wrapper as ComponentActivityWrapper).storage = this
  }

  public constructor(
    fragment: Fragment,
    savedState: Bundle? = null,
  ) : this(FragmentWrapper(fragment)) {
    savedState?.let { onRestoreInstanceState(it) }
    (wrapper as FragmentWrapper).storage = this
  }

  public var storageAccessCallback: StorageAccessCallback? = null

  public var folderPickerCallback: FolderPickerCallback? = null

  public var filePickerCallback: FilePickerCallback? = null

  public var createFileCallback: CreateFileCallback? = null

  public var requestCodeStorageAccess: Int = DEFAULT_REQUEST_CODE_STORAGE_ACCESS
    set(value) {
      field = value
      checkRequestCode()
    }

  public var requestCodeFolderPicker: Int = DEFAULT_REQUEST_CODE_FOLDER_PICKER
    set(value) {
      field = value
      checkRequestCode()
    }

  public var requestCodeFilePicker: Int = DEFAULT_REQUEST_CODE_FILE_PICKER
    set(value) {
      field = value
      checkRequestCode()
    }

  public var requestCodeCreateFile: Int = DEFAULT_REQUEST_CODE_CREATE_FILE
    set(value) {
      field = value
      checkRequestCode()
    }

  public val context: Context
    get() = wrapper.context

  /**
   * Even though storage permission has been granted via [hasStoragePermission], read and write
   * access may have not been granted yet.
   *
   * @param storageId Use [PRIMARY] for external storage. Or use SD Card storage ID.
   * @return `true` if storage permissions and URI permissions are granted for read and write
   *   access.
   * @see [DocumentFileCompat.getStorageIds]
   */
  public fun isStorageAccessGranted(storageId: String): Boolean =
    DocumentFileCompat.isAccessGranted(context, storageId)

  private var expectedStorageTypeForAccessRequest = StorageType.UNKNOWN

  private var expectedBasePathForAccessRequest: String? = null

  /**
   * Managing files in direct storage requires root access. Thus we need to make sure users select
   * root path.
   *
   * @param initialPath only takes effect on API 30+
   * @param expectedStorageType for example, if you set [StorageType.SD_CARD] but the user selects
   *   [StorageType.EXTERNAL], then trigger [StorageAccessCallback.onRootPathNotSelected]. Set to
   *   [StorageType.UNKNOWN] to accept any storage type.
   * @param expectedBasePath applicable for API 30+ only, because Android 11 does not allow
   *   selecting the root path.
   */
  @Deprecated(
    "This function doesn't follow Google's latest method, because it still uses startActivityForResult() manually.",
    ReplaceWith("RequestStorageAccessContract() with ActivityResultLauncher"),
  )
  @JvmOverloads
  public fun requestStorageAccess(
    requestCode: Int = requestCodeStorageAccess,
    initialPath: FileFullPath? = null,
    expectedStorageType: StorageType = StorageType.UNKNOWN,
    expectedBasePath: String = "",
  ) {
    val options = RequestStorageAccessContract.Options(initialPath)
    if (wrapper is ComponentActivityWrapper) {
      try {
        wrapper.storageAccessContract.expectedStorageType = expectedStorageType
        wrapper.storageAccessContract.expectedBasePath = expectedBasePath
        wrapper.requestStorageAccessLauncher.launch(options)
        requestCodeStorageAccess = requestCode
        expectedStorageTypeForAccessRequest = expectedStorageType
        expectedBasePathForAccessRequest = expectedBasePath
      } catch (_: ActivityNotFoundException) {
        storageAccessCallback?.onActivityHandlerNotFound(requestCode, Intent())
      }
      return
    }

    val contract =
      RequestStorageAccessContract(wrapper.context, expectedStorageType, expectedBasePath)
    val intent =
      try {
        contract.createIntent(wrapper.context, options)
      } catch (_: StoragePermissionDeniedException) {
        storageAccessCallback?.onStoragePermissionDenied(requestCode)
        return
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
   * Makes your app can access
   * [direct file paths](https://developer.android.com/training/data-storage/shared/media#direct-file-paths)
   *
   * See
   * [Manage all files on a storage device](https://developer.android.com/training/data-storage/manage-all-files)
   */
  @RequiresPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
  @RequiresApi(Build.VERSION_CODES.R)
  public fun requestFullStorageAccess() {
    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
  }

  /** Show interactive UI to create a file. */
  @Deprecated(
    "This function doesn't follow Google's latest method, because it still uses startActivityForResult() manually.",
    ReplaceWith("FileCreationContract() with ActivityResultLauncher"),
  )
  @JvmOverloads
  public fun createFile(
    mimeType: String,
    fileName: String? = null,
    initialPath: FileFullPath? = null,
    requestCode: Int = requestCodeCreateFile,
  ) {
    val options = FileCreationContract.Options(mimeType, fileName, initialPath)
    if (wrapper is ComponentActivityWrapper) {
      try {
        wrapper.requestFileCreationLauncher.launch(options)
        requestCodeCreateFile = requestCode
      } catch (_: ActivityNotFoundException) {
        createFileCallback?.onActivityHandlerNotFound(requestCode, Intent())
      }
      return
    }

    val contract = FileCreationContract(wrapper.context)
    val intent = contract.createIntent(wrapper.context, options)
    requestCodeCreateFile = requestCode
    if (!wrapper.startActivityForResult(intent, requestCode))
      createFileCallback?.onActivityHandlerNotFound(requestCode, intent)
  }

  @Deprecated(
    "This function doesn't follow Google's latest method, because it still uses startActivityForResult() manually.",
    ReplaceWith("OpenFolderPickerContract() with ActivityResultLauncher"),
  )
  @SuppressLint("InlinedApi")
  @JvmOverloads
  public fun openFolderPicker(
    requestCode: Int = requestCodeFolderPicker,
    initialPath: FileFullPath? = null,
  ) {
    val options = OpenFolderPickerContract.Options(initialPath)
    if (wrapper is ComponentActivityWrapper) {
      try {
        wrapper.requestFolderPickerLauncher.launch(options)
        requestCodeFolderPicker = requestCode
      } catch (_: ActivityNotFoundException) {
        folderPickerCallback?.onActivityHandlerNotFound(requestCode, Intent())
      }
      return
    }

    val contract = OpenFolderPickerContract(wrapper.context)
    val intent =
      try {
        contract.createIntent(wrapper.context, options)
      } catch (_: StoragePermissionDeniedException) {
        folderPickerCallback?.onStoragePermissionDenied(requestCode)
        return
      }
    requestCodeFolderPicker = requestCode
    if (!wrapper.startActivityForResult(intent, requestCode))
      folderPickerCallback?.onActivityHandlerNotFound(requestCode, intent)
  }

  private var lastVisitedFolder: File = Environment.getExternalStorageDirectory()

  @Deprecated(
    "This function doesn't follow Google's latest method, because it still uses startActivityForResult() manually.",
    ReplaceWith("OpenFilePickerContract() with ActivityResultLauncher"),
  )
  @JvmOverloads
  public fun openFilePicker(
    requestCode: Int = requestCodeFilePicker,
    allowMultiple: Boolean = false,
    initialPath: FileFullPath? = null,
    vararg filterMimeTypes: String,
  ) {
    val options =
      OpenFilePickerContract.Options(allowMultiple, initialPath, filterMimeTypes.toSet())
    if (wrapper is ComponentActivityWrapper) {
      try {
        wrapper.requestFilePickerLauncher.launch(options)
        requestCodeFilePicker = requestCode
      } catch (_: ActivityNotFoundException) {
        filePickerCallback?.onActivityHandlerNotFound(requestCode, Intent())
      }
      return
    }

    val contract = OpenFilePickerContract(wrapper.context)
    val intent = contract.createIntent(wrapper.context, options)
    requestCodeFilePicker = requestCode
    if (!wrapper.startActivityForResult(intent, requestCode))
      filePickerCallback?.onActivityHandlerNotFound(requestCode, intent)
  }

  public fun checkIfFileReceived(intent: Intent?, callback: FileReceiverCallback?) {
    when (intent?.action) {
      Intent.ACTION_SEND,
      Intent.ACTION_SEND_MULTIPLE -> {
        val files = intentToDocumentFiles(context, intent)
        if (files.isEmpty()) {
          callback?.onNonFileReceived(intent)
        } else {
          callback?.onFileReceived(files)
        }
      }
    }
  }

  public fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    checkRequestCode()

    when (requestCode) {
      requestCodeStorageAccess -> {
        if (resultCode == Activity.RESULT_OK) {
          val contract =
            RequestStorageAccessContract(
              wrapper.context,
              expectedStorageTypeForAccessRequest,
              expectedBasePathForAccessRequest.orEmpty(),
            )
          onRequestStorageAccessResult(contract.parseResult(resultCode, data))
        } else {
          storageAccessCallback?.onCanceledByUser(requestCode)
        }
      }

      requestCodeFolderPicker -> {
        if (resultCode == Activity.RESULT_OK) {
          val contract = OpenFolderPickerContract(wrapper.context)
          onFolderPickedResult(contract.parseResult(resultCode, data))
        } else {
          folderPickerCallback?.onCanceledByUser(requestCode)
        }
      }

      requestCodeFilePicker -> {
        if (resultCode == Activity.RESULT_OK) {
          val contract = OpenFilePickerContract(wrapper.context)
          onFilePickedResult(contract.parseResult(resultCode, data))
        } else {
          filePickerCallback?.onCanceledByUser(requestCode)
        }
      }

      requestCodeCreateFile -> {
        // resultCode is always OK for creating files
        val uri = data?.data
        if (uri != null) {
          val contract = FileCreationContract(wrapper.context)
          onFileCreationResult(contract.parseResult(resultCode, data))
        } else {
          createFileCallback?.onCanceledByUser(requestCode)
        }
      }
    }
  }

  internal fun onRequestStorageAccessResult(result: RequestStorageAccessResult) {
    when (result) {
      is RequestStorageAccessResult.CanceledByUser -> {
        storageAccessCallback?.onCanceledByUser(requestCodeStorageAccess)
      }

      is RequestStorageAccessResult.StoragePermissionDenied -> {
        storageAccessCallback?.onStoragePermissionDenied(requestCodeStorageAccess)
      }

      is RequestStorageAccessResult.RootPathNotSelected -> {
        if (result.expectedIntent != null) {
          if (!wrapper.startActivityForResult(result.expectedIntent, requestCodeStorageAccess)) {
            storageAccessCallback?.onActivityHandlerNotFound(
              requestCodeStorageAccess,
              result.expectedIntent,
            )
          }
          return
        }
        storageAccessCallback?.onRootPathNotSelected(
          requestCodeStorageAccess,
          result.rootPath,
          result.uri,
          result.selectedStorageType,
          expectedStorageTypeForAccessRequest,
        )
      }

      is RequestStorageAccessResult.ExpectedStorageNotSelected -> {
        storageAccessCallback?.onExpectedStorageNotSelected(
          requestCodeStorageAccess,
          result.selectedFolder,
          result.selectedStorageType,
          result.expectedBasePath,
          expectedStorageTypeForAccessRequest,
        )
      }

      is RequestStorageAccessResult.RootPathPermissionGranted -> {
        storageAccessCallback?.onRootPathPermissionGranted(requestCodeStorageAccess, result.root)
      }
    }
  }

  internal fun onFolderPickedResult(result: FolderPickerResult) {
    when (result) {
      is FolderPickerResult.Picked -> {
        folderPickerCallback?.onFolderSelected(requestCodeFolderPicker, result.folder)
      }

      is FolderPickerResult.AccessDenied -> {
        folderPickerCallback?.onStorageAccessDenied(
          requestCodeFolderPicker,
          result.folder,
          result.storageType,
          result.storageId,
        )
      }

      FolderPickerResult.CanceledByUser -> {
        folderPickerCallback?.onCanceledByUser(requestCodeFolderPicker)
      }
    }
  }

  internal fun onFilePickedResult(result: FilePickerResult) {
    when (result) {
      is FilePickerResult.Picked -> {
        filePickerCallback?.onFileSelected(requestCodeFilePicker, result.files)
      }

      is FilePickerResult.CanceledByUser -> {
        filePickerCallback?.onCanceledByUser(requestCodeFilePicker)
      }

      is FilePickerResult.StoragePermissionDenied -> {
        filePickerCallback?.onStoragePermissionDenied(requestCodeFilePicker, result.files)
      }
    }
  }

  internal fun onFileCreationResult(result: FileCreationResult) {
    when (result) {
      is FileCreationResult.Created -> {
        createFileCallback?.onFileCreated(requestCodeCreateFile, result.file)
      }

      is FileCreationResult.CanceledByUser -> {
        createFileCallback?.onCanceledByUser(requestCodeCreateFile)
      }

      is FileCreationResult.StoragePermissionDenied -> {
        Log.e(TAG, "Unexpected result for file creation: $result")
      }
    }
  }

  public fun onSaveInstanceState(outState: Bundle) {
    outState.putString(KEY_LAST_VISITED_FOLDER, lastVisitedFolder.path)
    outState.putString(KEY_EXPECTED_BASE_PATH_FOR_ACCESS_REQUEST, expectedBasePathForAccessRequest)
    outState.putInt(
      KEY_EXPECTED_STORAGE_TYPE_FOR_ACCESS_REQUEST,
      expectedStorageTypeForAccessRequest.ordinal,
    )
    outState.putInt(KEY_REQUEST_CODE_STORAGE_ACCESS, requestCodeStorageAccess)
    outState.putInt(KEY_REQUEST_CODE_FOLDER_PICKER, requestCodeFolderPicker)
    outState.putInt(KEY_REQUEST_CODE_FILE_PICKER, requestCodeFilePicker)
    outState.putInt(KEY_REQUEST_CODE_CREATE_FILE, requestCodeCreateFile)
    if (wrapper is FragmentWrapper) {
      outState.putInt(KEY_REQUEST_CODE_FRAGMENT_PICKER, wrapper.requestCode)
    }
  }

  public fun onRestoreInstanceState(savedInstanceState: Bundle) {
    savedInstanceState.getString(KEY_LAST_VISITED_FOLDER)?.let { lastVisitedFolder = File(it) }
    expectedBasePathForAccessRequest =
      savedInstanceState.getString(KEY_EXPECTED_BASE_PATH_FOR_ACCESS_REQUEST)
    expectedStorageTypeForAccessRequest =
      StorageType.entries
        .toTypedArray()[savedInstanceState.getInt(KEY_EXPECTED_STORAGE_TYPE_FOR_ACCESS_REQUEST)]
    requestCodeStorageAccess =
      savedInstanceState.getInt(
        KEY_REQUEST_CODE_STORAGE_ACCESS,
        DEFAULT_REQUEST_CODE_STORAGE_ACCESS,
      )
    requestCodeFolderPicker =
      savedInstanceState.getInt(KEY_REQUEST_CODE_FOLDER_PICKER, DEFAULT_REQUEST_CODE_FOLDER_PICKER)
    requestCodeFilePicker =
      savedInstanceState.getInt(KEY_REQUEST_CODE_FILE_PICKER, DEFAULT_REQUEST_CODE_FILE_PICKER)
    requestCodeCreateFile =
      savedInstanceState.getInt(KEY_REQUEST_CODE_CREATE_FILE, DEFAULT_REQUEST_CODE_CREATE_FILE)
    if (
      wrapper is FragmentWrapper && savedInstanceState.containsKey(KEY_REQUEST_CODE_FRAGMENT_PICKER)
    ) {
      wrapper.requestCode = savedInstanceState.getInt(KEY_REQUEST_CODE_FRAGMENT_PICKER)
    }
  }

  private fun checkRequestCode() {
    if (wrapper is ComponentActivityWrapper) {
      return
    }

    if (requestCodeFilePicker == 0) {
      requestCodeFilePicker = DEFAULT_REQUEST_CODE_FILE_PICKER
    }

    if (requestCodeFolderPicker == 0) {
      requestCodeFolderPicker = DEFAULT_REQUEST_CODE_FOLDER_PICKER
    }

    if (requestCodeStorageAccess == 0) {
      requestCodeStorageAccess = DEFAULT_REQUEST_CODE_STORAGE_ACCESS
    }

    if (requestCodeCreateFile == 0) {
      requestCodeCreateFile = DEFAULT_REQUEST_CODE_CREATE_FILE
    }

    if (
      setOf(
          requestCodeFilePicker,
          requestCodeFolderPicker,
          requestCodeStorageAccess,
          requestCodeCreateFile,
        )
        .size < 4
    ) {
      throw IllegalArgumentException(
        "Request codes must be unique. File picker=$requestCodeFilePicker, Folder picker=$requestCodeFolderPicker, " +
          "Storage access=$requestCodeStorageAccess, Create file=$requestCodeCreateFile"
      )
    }
  }

  public companion object {
    public const val LIBRARY_PACKAGE_NAME: String = "com.anggrayudi.storage"
    private const val KEY_REQUEST_CODE_STORAGE_ACCESS =
      LIBRARY_PACKAGE_NAME + ".requestCodeStorageAccess"
    private const val KEY_REQUEST_CODE_FOLDER_PICKER =
      LIBRARY_PACKAGE_NAME + ".requestCodeFolderPicker"
    private const val KEY_REQUEST_CODE_FILE_PICKER = LIBRARY_PACKAGE_NAME + ".requestCodeFilePicker"
    private const val KEY_REQUEST_CODE_CREATE_FILE = LIBRARY_PACKAGE_NAME + ".requestCodeCreateFile"
    private const val KEY_REQUEST_CODE_FRAGMENT_PICKER =
      LIBRARY_PACKAGE_NAME + ".requestCodeFragmentPicker"
    private const val KEY_EXPECTED_STORAGE_TYPE_FOR_ACCESS_REQUEST =
      LIBRARY_PACKAGE_NAME + ".expectedStorageTypeForAccessRequest"
    private const val KEY_EXPECTED_BASE_PATH_FOR_ACCESS_REQUEST =
      LIBRARY_PACKAGE_NAME + ".expectedBasePathForAccessRequest"
    private const val KEY_LAST_VISITED_FOLDER = LIBRARY_PACKAGE_NAME + ".lastVisitedFolder"
    private const val TAG = "SimpleStorage"

    private const val DEFAULT_REQUEST_CODE_STORAGE_ACCESS: Int = 1
    private const val DEFAULT_REQUEST_CODE_FOLDER_PICKER: Int = 2
    private const val DEFAULT_REQUEST_CODE_FILE_PICKER: Int = 3
    private const val DEFAULT_REQUEST_CODE_CREATE_FILE: Int = 4

    @Deprecated(
      "Moved to DocumentFileCompat, which is not going away in 4.0.",
      ReplaceWith(
        "DocumentFileCompat.externalStoragePath",
        "com.anggrayudi.storage.file.DocumentFileCompat",
      ),
    )
    @JvmStatic
    public val externalStoragePath: String
      get() = DocumentFileCompat.externalStoragePath

    @Deprecated(
      "Moved to DocumentFileCompat, which is not going away in 4.0.",
      ReplaceWith(
        "DocumentFileCompat.isSdCardPresent",
        "com.anggrayudi.storage.file.DocumentFileCompat",
      ),
    )
    @JvmStatic
    public val isSdCardPresent: Boolean
      get() = DocumentFileCompat.isSdCardPresent

    @Deprecated(
      "Moved to DocumentFileCompat, which is not going away in 4.0.",
      ReplaceWith(
        "DocumentFileCompat.getDefaultExternalStorageIntent(context)",
        "com.anggrayudi.storage.file.DocumentFileCompat",
      ),
    )
    @JvmStatic
    public fun getDefaultExternalStorageIntent(context: Context): Intent =
      DocumentFileCompat.getDefaultExternalStorageIntent(context)

    @Deprecated(
      "Moved to DocumentFileCompat, which is not going away in 4.0.",
      ReplaceWith(
        "DocumentFileCompat.hasStoragePermission(context)",
        "com.anggrayudi.storage.file.DocumentFileCompat",
      ),
    )
    @JvmStatic
    public fun hasStoragePermission(context: Context): Boolean =
      DocumentFileCompat.hasStoragePermission(context)

    @Deprecated(
      "Moved to DocumentFileCompat, which is not going away in 4.0.",
      ReplaceWith(
        "DocumentFileCompat.hasStorageReadPermission(context)",
        "com.anggrayudi.storage.file.DocumentFileCompat",
      ),
    )
    @JvmStatic
    public fun hasStorageReadPermission(context: Context): Boolean =
      DocumentFileCompat.hasStorageReadPermission(context)

    @Deprecated(
      "Moved to DocumentFileCompat, which is not going away in 4.0.",
      ReplaceWith(
        "DocumentFileCompat.hasFullDiskAccess(context, storageId)",
        "com.anggrayudi.storage.file.DocumentFileCompat",
      ),
    )
    @JvmStatic
    public fun hasFullDiskAccess(context: Context, storageId: String): Boolean =
      DocumentFileCompat.hasFullDiskAccess(context, storageId)

    @Deprecated(
      "Moved to DocumentFileCompat, which is not going away in 4.0.",
      ReplaceWith(
        "DocumentFileCompat.hasStorageAccess(context, fullPath, requiresWriteAccess)",
        "com.anggrayudi.storage.file.DocumentFileCompat",
      ),
    )
    @JvmStatic
    @JvmOverloads
    public fun hasStorageAccess(
      context: Context,
      fullPath: String,
      requiresWriteAccess: Boolean = true,
    ): Boolean = DocumentFileCompat.hasStorageAccess(context, fullPath, requiresWriteAccess)

    @Deprecated(
      "Moved to DocumentFileCompat, which is not going away in 4.0.",
      ReplaceWith(
        "DocumentFileCompat.cleanupRedundantUriPermissions(context)",
        "com.anggrayudi.storage.file.DocumentFileCompat",
      ),
    )
    @JvmStatic
    @WorkerThread
    public fun cleanupRedundantUriPermissions(context: Context): Unit =
      DocumentFileCompat.cleanupRedundantUriPermissions(context)
  }
}
