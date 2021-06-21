package com.anggrayudi.storage.media

import com.anggrayudi.storage.file.MimeType

/**
 * Created on 05/09/20
 * @author Anggrayudi H
 */
data class FileDescription @JvmOverloads constructor(
    var name: String,
    var subFolder: String = "",
    var mimeType: String = MimeType.UNKNOWN
) {
    val fullName: String
        get() = MimeType.getFullFileName(name, mimeType)
}