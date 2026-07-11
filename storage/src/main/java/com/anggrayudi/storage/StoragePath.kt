package com.anggrayudi.storage

import android.content.Context
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageId
import java.io.File

/**
 * Identifies a location on a storage volume without holding a [Context].
 *
 * A path is a pair of [storageId] (e.g. [StorageId.PRIMARY] or an SD card ID like `AAAA-BBBB`) and
 * a [basePath] relative to that volume's root (e.g. `Download/MyMovie.mp4`). This replaces the v2
 * combination of `FileFullPath` and "simple path" strings.
 *
 * @author Anggrayudi H
 */
data class StoragePath(val storageId: String, val basePath: String = "") {

  /** Resolves this path to an absolute path like `/storage/emulated/0/Download/MyMovie.mp4`. */
  fun toAbsolutePath(context: Context): String =
    DocumentFileCompat.buildAbsolutePath(context, storageId, basePath)

  override fun toString(): String = "$storageId:$basePath"

  companion object {
    /** Creates a path on the primary/external storage volume. */
    @JvmStatic fun primary(basePath: String = ""): StoragePath = StoragePath(StorageId.PRIMARY, basePath)

    /** Parses an absolute path like `/storage/AAAA-BBBB/Download` or a `storageId:basePath` string. */
    @JvmStatic
    fun fromAbsolutePath(context: Context, fullPath: String): StoragePath =
      StoragePath(
        DocumentFileCompat.getStorageId(context, fullPath),
        DocumentFileCompat.getBasePath(context, fullPath),
      )

    @JvmStatic
    fun from(context: Context, file: File): StoragePath = fromAbsolutePath(context, file.absolutePath)
  }
}
