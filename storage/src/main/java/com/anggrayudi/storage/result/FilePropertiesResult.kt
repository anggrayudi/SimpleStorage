package com.anggrayudi.storage.result

import android.content.Context
import android.text.format.Formatter
import java.util.Date

/**
 * Created on 03/06/21
 *
 * @author Anggrayudi H
 */
public data class FileProperties(
  var name: String = "",
  var location: String = "",
  var size: Long = 0,
  var isFolder: Boolean = false,
  var folders: Int = 0,
  var files: Int = 0,
  var emptyFiles: Int = 0,
  var emptyFolders: Int = 0,
  var isVirtual: Boolean = false,
  var lastModified: Date? = null,
) {
  public fun formattedSize(context: Context): String = Formatter.formatFileSize(context, size)
}

public sealed class FilePropertiesResult {
  public data class Updating(val properties: FileProperties) : FilePropertiesResult()

  public data class Completed(val properties: FileProperties) : FilePropertiesResult()

  public data object Error : FilePropertiesResult()
}
