package com.anggrayudi.storage.access

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.anggrayudi.storage.StorageFile
import com.anggrayudi.storage.StoragePath
import com.anggrayudi.storage.contract.FileCreationContract
import com.anggrayudi.storage.contract.FileCreationResult
import com.anggrayudi.storage.contract.FilePickerResult
import com.anggrayudi.storage.contract.FolderPickerResult
import com.anggrayudi.storage.contract.OpenFilePickerContract
import com.anggrayudi.storage.contract.OpenFolderPickerContract
import com.anggrayudi.storage.contract.RequestStorageAccessContract
import com.anggrayudi.storage.contract.RequestStorageAccessResult
import com.anggrayudi.storage.contract.StoragePermissionContract
import com.anggrayudi.storage.contract.StoragePermissionDeniedException
import com.anggrayudi.storage.file.FileFullPath
import com.anggrayudi.storage.toStorageFile
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The outcome of [StorageAccessManager.ensureAccess].
 *
 * @author Anggrayudi H
 */
sealed interface AccessResult {
  /** URI permission for the requested path is held; [folder] is ready for read/write. */
  data class Granted(val folder: StorageFile) : AccessResult

  /**
   * The user granted access to [grantedRoot] (possibly `null` when nothing was resolvable), but it
   * does not cover the requested path. Callers usually explain and call
   * [StorageAccessManager.ensureAccess] again.
   */
  data class WrongRootSelected(val grantedRoot: StorageFile?) : AccessResult

  data object CanceledByUser : AccessResult

  /** Runtime storage permission was denied (only possible on API 26–29). */
  data object PermissionDenied : AccessResult
}

/**
 * Suspend-first replacement for `SimpleStorageHelper`, built purely on
 * [ActivityResultContracts]: no request codes, no `onActivityResult`, no
 * `onSaveInstanceState` plumbing, and no built-in dialogs to fight with.
 *
 * Create it during [ComponentActivity.onCreate] (launchers must be registered before the activity
 * is started), then call the suspend functions from any coroutine:
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *   private lateinit var storageAccess: StorageAccessManager
 *
 *   override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     storageAccess = StorageAccessManager(this)
 *     ...
 *     lifecycleScope.launch {
 *       when (val access = storageAccess.ensureAccess(StoragePath.primary("Documents"))) {
 *         is AccessResult.Granted -> myFile.copyTo(access.folder)
 *         else -> showError()
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * @author Anggrayudi H
 */
class StorageAccessManager(activity: ComponentActivity) {

  private val appContext: Context = activity.applicationContext
  private val mutex = Mutex()

  private var accessContinuation: CancellableContinuation<RequestStorageAccessResult>? = null
  private var folderContinuation: CancellableContinuation<FolderPickerResult>? = null
  private var fileContinuation: CancellableContinuation<FilePickerResult>? = null
  private var creationContinuation: CancellableContinuation<FileCreationResult>? = null
  private var permissionContinuation: CancellableContinuation<Map<String, Boolean>>? = null
  private var mediaContinuation: CancellableContinuation<List<android.net.Uri>>? = null

  private val accessLauncher =
    activity.registerForActivityResult(RequestStorageAccessContract(appContext)) { result ->
      accessContinuation?.resume(result)
      accessContinuation = null
    }

  private val folderLauncher =
    activity.registerForActivityResult(OpenFolderPickerContract(appContext)) { result ->
      folderContinuation?.resume(result)
      folderContinuation = null
    }

  private val fileLauncher =
    activity.registerForActivityResult(OpenFilePickerContract(appContext)) { result ->
      fileContinuation?.resume(result)
      fileContinuation = null
    }

  private val creationLauncher =
    activity.registerForActivityResult(FileCreationContract(appContext)) { result ->
      creationContinuation?.resume(result)
      creationContinuation = null
    }

  private val permissionLauncher =
    activity.registerForActivityResult(StoragePermissionContract()) { result ->
      permissionContinuation?.resume(result)
      permissionContinuation = null
    }

  private val mediaLauncher =
    activity.registerForActivityResult(
      ActivityResultContracts.PickMultipleVisualMedia(MAX_MEDIA_ITEMS)
    ) { uris ->
      mediaContinuation?.resume(uris)
      mediaContinuation = null
    }

  /**
   * Makes sure this app holds read/write URI permission for [path], asking the user through SAF
   * when it does not. Handles the runtime-permission dance on API 26–29 automatically.
   */
  suspend fun ensureAccess(
    path: StoragePath,
    requiresWriteAccess: Boolean = true,
  ): AccessResult =
    mutex.withLock {
      requestAccessLocked(path, requiresWriteAccess, retryAfterPermission = true)
    }

  private suspend fun requestAccessLocked(
    path: StoragePath,
    requiresWriteAccess: Boolean,
    retryAfterPermission: Boolean,
  ): AccessResult {
    StorageFile.fromPath(appContext, path, requiresWriteAccess)?.let {
      return AccessResult.Granted(it)
    }

    val options =
      RequestStorageAccessContract.Options(
        initialPath = FileFullPath(appContext, path.storageId, path.basePath)
      )
    val result =
      try {
        awaitResult<RequestStorageAccessContract.Options, RequestStorageAccessResult>(accessLauncher, options) { accessContinuation = it }
      } catch (_: StoragePermissionDeniedException) {
        return if (retryAfterPermission && requestStoragePermission()) {
          requestAccessLocked(path, requiresWriteAccess, retryAfterPermission = false)
        } else {
          AccessResult.PermissionDenied
        }
      } catch (_: ActivityNotFoundException) {
        return AccessResult.CanceledByUser
      }

    return when (result) {
      is RequestStorageAccessResult.RootPathPermissionGranted -> {
        StorageFile.fromPath(appContext, path, requiresWriteAccess)?.let {
          AccessResult.Granted(it)
        } ?: AccessResult.WrongRootSelected(result.root.toStorageFile(appContext))
      }
      is RequestStorageAccessResult.RootPathNotSelected ->
        AccessResult.WrongRootSelected(null)
      is RequestStorageAccessResult.ExpectedStorageNotSelected ->
        AccessResult.WrongRootSelected(result.selectedFolder.toStorageFile(appContext))
      is RequestStorageAccessResult.StoragePermissionDenied ->
        if (retryAfterPermission && requestStoragePermission()) {
          requestAccessLocked(path, requiresWriteAccess, retryAfterPermission = false)
        } else {
          AccessResult.PermissionDenied
        }
      is RequestStorageAccessResult.CanceledByUser -> AccessResult.CanceledByUser
    }
  }

  /** Opens the SAF folder picker and suspends until the user answers. */
  suspend fun pickFolder(initialPath: StoragePath? = null): FolderPickerResult =
    mutex.withLock {
      val options = OpenFolderPickerContract.Options(initialPath?.toFileFullPath())
      try {
        awaitResult<OpenFolderPickerContract.Options, FolderPickerResult>(folderLauncher, options) { folderContinuation = it }
      } catch (_: ActivityNotFoundException) {
        FolderPickerResult.CanceledByUser
      }
    }

  /** Opens the SAF file picker and suspends until the user answers. */
  suspend fun pickFiles(
    allowMultiple: Boolean = false,
    filterMimeTypes: Set<String> = emptySet(),
    initialPath: StoragePath? = null,
  ): FilePickerResult =
    mutex.withLock {
      val options =
        OpenFilePickerContract.Options(allowMultiple, initialPath?.toFileFullPath(), filterMimeTypes)
      try {
        awaitResult<OpenFilePickerContract.Options, FilePickerResult>(fileLauncher, options) { fileContinuation = it }
      } catch (_: ActivityNotFoundException) {
        FilePickerResult.CanceledByUser
      }
    }

  /** Lets the user place a new file via SAF and suspends until the user answers. */
  suspend fun createFile(
    mimeType: String,
    fileName: String? = null,
    initialPath: StoragePath? = null,
  ): FileCreationResult =
    mutex.withLock {
      val options = FileCreationContract.Options(mimeType, fileName, initialPath?.toFileFullPath())
      try {
        awaitResult<FileCreationContract.Options, FileCreationResult>(creationLauncher, options) { creationContinuation = it }
      } catch (_: ActivityNotFoundException) {
        FileCreationResult.CanceledByUser
      }
    }

  /**
   * Opens the system Photo Picker ([ActivityResultContracts.PickVisualMedia]) — no permission and
   * no SAF grant needed. Returns the picked media as [StorageFile]s, empty when canceled.
   */
  suspend fun pickMedia(
    type: ActivityResultContracts.PickVisualMedia.VisualMediaType =
      ActivityResultContracts.PickVisualMedia.ImageAndVideo
  ): List<StorageFile> =
    mutex.withLock {
      val request = PickVisualMediaRequest(type)
      val uris =
        try {
          awaitResult<PickVisualMediaRequest, List<android.net.Uri>>(mediaLauncher, request) { mediaContinuation = it }
        } catch (_: ActivityNotFoundException) {
          emptyList()
        }
      uris.mapNotNull { StorageFile.from(appContext, it) }
    }

  /** Requests READ/WRITE_EXTERNAL_STORAGE. Only meaningful on API 26–29; `true` elsewhere. */
  suspend fun requestStoragePermission(): Boolean {
    val result =
      try {
        awaitResult<Unit, Map<String, Boolean>>(permissionLauncher, Unit) { permissionContinuation = it }
      } catch (_: ActivityNotFoundException) {
        return false
      }
    return result.isNotEmpty() && result.values.all { it }
  }

  private fun StoragePath.toFileFullPath(): FileFullPath =
    FileFullPath(appContext, storageId, basePath)

  private suspend fun <I, O> awaitResult(
    launcher: ActivityResultLauncher<I>,
    input: I,
    store: (CancellableContinuation<O>?) -> Unit,
  ): O = suspendCancellableCoroutine { continuation ->
    store(continuation)
    continuation.invokeOnCancellation { store(null) }
    try {
      launcher.launch(input)
    } catch (e: Exception) {
      store(null)
      throw e
    }
  }

  private companion object {
    const val MAX_MEDIA_ITEMS = 100
  }
}
