package com.anggrayudi.storage.media

import com.anggrayudi.storage.file.MimeType

/**
 * Created on 05/09/20
 * @author Anggrayudi H
 */
data class FileDescription(
    val name: String,
    val subFolder: String = "",
    val mimeType: String = MimeType.UNKNOWN
)