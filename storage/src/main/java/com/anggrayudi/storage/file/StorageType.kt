package com.anggrayudi.storage.file

import android.content.Context
import android.net.Uri
import com.anggrayudi.storage.SimpleStorage

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
public enum class StorageType {
  /**
   * Equals to primary storage.
   *
   * @see [SimpleStorage.externalStoragePath]
   */
  EXTERNAL,
  DATA,
  SD_CARD,
  UNKNOWN;

  public fun isExpected(actualStorageType: StorageType): Boolean = this == UNKNOWN || this == actualStorageType

  public companion object {

    /**
     * Format-based classification. Prefer the [Context] overload, which also recognizes mounted
     * volumes whose ID format is unknown.
     *
     * @param storageId get it from [Uri.getStorageId]
     */
    @JvmStatic
    public fun fromStorageId(storageId: String): StorageType =
      when {
        storageId == StorageId.PRIMARY -> EXTERNAL
        storageId == StorageId.DATA -> DATA
        storageId.matches(DocumentFileCompat.SD_CARD_STORAGE_ID_REGEX) -> SD_CARD
        else -> UNKNOWN
      }

    /**
     * Like [fromStorageId], but treats any currently mounted volume as [SD_CARD] (i.e. a
     * removable volume — SD card, USB OTG drive, or ChromeOS external media) even when its ID
     * format is not recognized.
     */
    @JvmStatic
    public fun fromStorageId(context: Context, storageId: String): StorageType =
      when {
        storageId == StorageId.PRIMARY -> EXTERNAL
        storageId == StorageId.DATA -> DATA
        DocumentFileCompat.isMountedVolumeId(context, storageId) ||
          storageId.matches(DocumentFileCompat.SD_CARD_STORAGE_ID_REGEX) -> SD_CARD
        else -> UNKNOWN
      }
  }
}
