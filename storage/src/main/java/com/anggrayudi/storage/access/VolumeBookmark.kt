package com.anggrayudi.storage.access

import com.anggrayudi.storage.ExperimentalSimpleStorageApi
import com.anggrayudi.storage.StorageFile
import com.anggrayudi.storage.StoragePath

/**
 * A remembered location on a removable volume (SD card, USB OTG drive) that can be re-resolved
 * after the volume is unplugged and replugged, or even after its volume ID changed.
 *
 * On mainline Android the volume ID is the filesystem UUID and is **stable across replugs**, so
 * [storageId] alone re-resolves the bookmark with no user interaction. [volumeLabel] exists as a
 * fallback matcher for the OEM/ChromeOS cases where IDs are not stable: when no mounted volume has
 * [storageId] but one carries the same label, [StorageAccessManager.resolveBookmark] asks the user
 * to re-grant access once and returns an updated bookmark.
 *
 * The class is a plain value holder — persist it yourself (DataStore, SharedPreferences, DB).
 *
 * @param volumeLabel [android.os.storage.StorageVolume.getDescription] at grant time; used only as
 *   a fallback matcher, blank disables the fallback
 * @param storageId the filesystem UUID that was granted
 * @param basePath path relative to the volume root that the app wants to reopen
 * @author Anggrayudi H
 */
@ExperimentalSimpleStorageApi
public data class VolumeBookmark(
  val volumeLabel: String,
  val storageId: String,
  val basePath: String = "",
) {
  public fun toStoragePath(): StoragePath = StoragePath(storageId, basePath)
}

/** The outcome of [StorageAccessManager.resolveBookmark]. */
@ExperimentalSimpleStorageApi
public sealed interface BookmarkResult {

  /**
   * Access is available. [bookmark] may differ from the input when the volume was re-granted
   * under a new ID — persist the updated value.
   */
  public data class Granted(val folder: StorageFile, val bookmark: VolumeBookmark) : BookmarkResult

  /** No mounted volume matches the bookmark by ID or label. Plugging the drive in may fix it. */
  public data object VolumeNotMounted : BookmarkResult

  /** The user granted a root that does not cover the bookmarked volume. */
  public data class WrongRootSelected(val grantedRoot: StorageFile?) : BookmarkResult

  public data object CanceledByUser : BookmarkResult

  public data object PermissionDenied : BookmarkResult
}
