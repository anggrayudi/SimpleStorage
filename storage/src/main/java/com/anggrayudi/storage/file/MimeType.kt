package com.anggrayudi.storage.file

import android.webkit.MimeTypeMap
import com.anggrayudi.storage.extension.normalizeFileName

/**
 * See [mime type list](https://www.freeformatter.com/mime-types-list.html)
 *
 * Created on 03/06/21
 *
 * @author Anggrayudi H
 */
public object MimeType {
  public const val UNKNOWN: String = "*/*"
  public const val BINARY_FILE: String = "application/octet-stream"
  public const val ZIP: String = "application/zip"
  public const val IMAGE: String = "image/*"
  public const val AUDIO: String = "audio/*"
  public const val VIDEO: String = "video/*"
  public const val TEXT: String = "text/*"
  public const val FONT: String = "font/*"
  public const val APPLICATION: String = "application/*"
  public const val CHEMICAL: String = "chemical/*"
  public const val MODEL: String = "model/*"

  /**
   * * Given `name` = `ABC` AND `mimeType` = `video/mp4`, then return `ABC.mp4`
   * * Given `name` = `ABC` AND `mimeType` = `null`, then return `ABC`
   * * Given `name` = `ABC.mp4` AND `mimeType` = `video/mp4`, then return `ABC.mp4`
   *
   * @param name can have file extension or not
   */
  @JvmStatic
  public fun getFullFileName(name: String, mimeType: String?): String {
    // Prior to API 29, MimeType.BINARY_FILE has no file extension
    val cleanName = name.normalizeFileName()
    if (mimeType == BINARY_FILE || mimeType == UNKNOWN) {
      val extension = getExtensionFromFileName(cleanName)
      if (extension.isNotEmpty()) {
        return cleanName
      }
    }
    return getExtensionFromMimeType(mimeType).let {
      if (it.isEmpty() || cleanName.endsWith(".$it")) cleanName else "$cleanName.$it".trimEnd('.')
    }
  }

  /**
   * Some mime types return no file extension on older API levels. This function adds compatibility
   * across API levels. Since API 29, [MimeType.BINARY_FILE] has extension `*.bin`
   *
   * @see getExtensionFromMimeTypeOrFileName
   */
  @JvmStatic
  public fun getExtensionFromMimeType(mimeType: String?): String {
    return mimeType
      ?.let {
        if (it == BINARY_FILE) "bin" else MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
      }
      .orEmpty()
  }

  @JvmStatic
  public fun getBaseFileName(filename: String?): String {
    return if (hasExtension(filename)) filename.orEmpty().substringBeforeLast('.')
    else filename.orEmpty()
  }

  @JvmStatic
  public fun getExtensionFromFileName(filename: String?): String {
    return if (hasExtension(filename)) filename.orEmpty().substringAfterLast('.', "") else ""
  }

  /**
   * File extensions must be alphanumeric. The following filenames are considered as have no
   * extension:
   * * `abc.pq rs`
   * * `abc.あん`
   */
  @JvmStatic
  public fun hasExtension(filename: String?): Boolean = filename?.matches(Regex("(.*?)\\.[a-zA-Z0-9]+")) == true

  /** @see getExtensionFromMimeType */
  @JvmStatic
  public fun getExtensionFromMimeTypeOrFileName(mimeType: String?, filename: String): String {
    return if (mimeType == null || mimeType == UNKNOWN) getExtensionFromFileName(filename)
    else getExtensionFromMimeType(mimeType)
  }

  /**
   * Some file types return no mime type on older API levels. This function adds compatibility
   * accross API levels.
   */
  @JvmStatic
  public fun getMimeTypeFromExtension(fileExtension: String): String {
    return if (fileExtension.equals("bin", ignoreCase = true)) BINARY_FILE
    else MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension) ?: UNKNOWN
  }

  @JvmStatic
  public fun getMimeTypeFromFileName(filename: String?): String {
    return getMimeTypeFromExtension(getExtensionFromFileName(filename))
  }
}
