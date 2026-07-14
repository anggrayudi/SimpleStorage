package com.anggrayudi.storage.access

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.anggrayudi.storage.ExperimentalSimpleStorageApi
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
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.FileFullPath
import com.anggrayudi.storage.toStorageFile
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The outcome of [StorageAccessManager.ensureAccess].
 *
 * @author Anggrayudi H
 */
public sealed interface AccessResult {
  /** URI permission for the requested path is held; [folder] is ready for read/write. */
  public data class Granted(val folder: StorageFile) : AccessResult

  /**
   * The user granted access to [grantedRoot] (possibly `null` when nothing was resolvable), but it
   * does not cover the requested path. Callers usually explain and call
   * [StorageAccessManager.ensureAccess] again.
   */
  public data class WrongRootSelected(val grantedRoot: StorageFile?) : AccessResult

  public data object CanceledByUser : AccessResult

  /** Runtime storage permission was denied (only possible on API 26–29). */
  public data object PermissionDenied : AccessResult
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
public class StorageAccessManager(activity: ComponentActivity) {

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
  public suspend fun ensureAccess(
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
  public suspend fun pickFolder(initialPath: StoragePath? = null): FolderPickerResult =
    mutex.withLock {
      val options = OpenFolderPickerContract.Options(initialPath?.toFileFullPath())
      try {
        awaitResult<OpenFolderPickerContract.Options, FolderPickerResult>(folderLauncher, options) { folderContinuation = it }
      } catch (_: ActivityNotFoundException) {
        FolderPickerResult.CanceledByUser
      }
    }

  /** Opens the SAF file picker and suspends until the user answers. */
  public suspend fun pickFiles(
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
  public suspend fun createFile(
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
  public suspend fun pickMedia(
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
  public suspend fun requestStoragePermission(): Boolean {
    val result =
      try {
        awaitResult<Unit, Map<String, Boolean>>(permissionLauncher, Unit) { permissionContinuation = it }
      } catch (_: ActivityNotFoundException) {
        return false
      }
    return result.isNotEmpty() && result.values.all { it }
  }

  // region Experimental: volume bookmarks

  /**
   * Re-resolves a [VolumeBookmark] created earlier with [createBookmark].
   *
   * Resolution order:
   * 1. The bookmarked [VolumeBookmark.storageId] still resolves (mainline Android: filesystem
   *    UUIDs are stable across replugs) → [BookmarkResult.Granted] with no user interaction.
   * 2. The ID is mounted but the grant is gone, or a mounted volume carries the same
   *    [VolumeBookmark.volumeLabel] under a different ID → asks the user once via [ensureAccess]
   *    and returns [BookmarkResult.Granted] with an **updated** bookmark to persist.
   * 3. Nothing matches → [BookmarkResult.VolumeNotMounted].
   */
  @ExperimentalSimpleStorageApi
  public suspend fun resolveBookmark(
    bookmark: VolumeBookmark,
    requiresWriteAccess: Boolean = true,
  ): BookmarkResult {
    StorageFile.fromPath(appContext, bookmark.toStoragePath(), requiresWriteAccess)?.let {
      return BookmarkResult.Granted(it, bookmark)
    }

    val candidateId =
      when {
        DocumentFileCompat.isMountedVolumeId(appContext, bookmark.storageId) -> bookmark.storageId
        bookmark.volumeLabel.isNotBlank() ->
          storageManager.storageVolumes
            .firstOrNull {
              !it.isPrimary && it.getDescription(appContext) == bookmark.volumeLabel
            }
            ?.uuid
        else -> null
      } ?: return BookmarkResult.VolumeNotMounted

    return when (val access =
      ensureAccess(StoragePath(candidateId, bookmark.basePath), requiresWriteAccess)) {
      is AccessResult.Granted ->
        BookmarkResult.Granted(access.folder, bookmark.copy(storageId = candidateId))
      is AccessResult.WrongRootSelected -> BookmarkResult.WrongRootSelected(access.grantedRoot)
      is AccessResult.CanceledByUser -> BookmarkResult.CanceledByUser
      is AccessResult.PermissionDenied -> BookmarkResult.PermissionDenied
    }
  }

  /**
   * Builds a [VolumeBookmark] for [folder] so it can be re-resolved later with [resolveBookmark].
   * Returns `null` when the folder has no resolvable [StorageFile.path].
   */
  @ExperimentalSimpleStorageApi
  public fun createBookmark(folder: StorageFile): VolumeBookmark? {
    val path = folder.path ?: return null
    val label =
      storageManager.storageVolumes
        .firstOrNull { it.uuid.equals(path.storageId, ignoreCase = true) }
        ?.getDescription(appContext)
        .orEmpty()
    return VolumeBookmark(label, path.storageId, path.basePath)
  }

  /**
   * Emits every [StorageVolume] that reaches the mounted state — e.g. a USB OTG drive being
   * plugged in — for as long as the flow is collected. Pair it with [resolveBookmark] to reopen
   * remembered locations automatically.
   */
  @ExperimentalSimpleStorageApi
  @RequiresApi(Build.VERSION_CODES.R)
  public fun volumeMountEvents(): Flow<StorageVolume> = callbackFlow {
    val callback =
      object : StorageManager.StorageVolumeCallback() {
        override fun onStateChanged(volume: StorageVolume) {
          if (volume.state == Environment.MEDIA_MOUNTED) {
            trySend(volume)
          }
        }
      }
    storageManager.registerStorageVolumeCallback(appContext.mainExecutor, callback)
    awaitClose { storageManager.unregisterStorageVolumeCallback(callback) }
  }

  private val storageManager: StorageManager
    get() = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager

  // endregion

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
