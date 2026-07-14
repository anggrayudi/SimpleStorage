package com.anggrayudi.storage.media

import com.anggrayudi.storage.file.MimeType

/**
 * Created on 05/09/20
 *
 * @author Anggrayudi H
 */
public class FileDescription @JvmOverloads constructor(public var name: String, public var subFolder: String = "") {
  private var _mimeType: String? = MimeType.BINARY_FILE

  public val mimeType: String
    get() {
      var type = _mimeType
      if (
        type.isNullOrEmpty() ||
          MimeType.hasExtension(name) && (type == MimeType.BINARY_FILE || type == MimeType.UNKNOWN)
      ) {
        type = MimeType.getMimeTypeFromFileName(name)
      }
      _mimeType = type
      return type
    }

  public constructor(name: String, subFolder: String, mimeType: String?) : this(name, subFolder) {
    _mimeType = mimeType?.takeIf { !it.contains("*") }
  }

  public val fullName: String
    get() = MimeType.getFullFileName(name, mimeType)
}
