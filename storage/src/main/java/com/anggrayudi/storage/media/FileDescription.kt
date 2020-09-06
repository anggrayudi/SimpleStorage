package com.anggrayudi.storage.media

import com.anggrayudi.storage.DocumentFileCompat

/**
 * Created on 05/09/20
 * @author Anggrayudi H
 */
data class FileDescription(
    val name: String,
    val subFolder: String = "",
    val mimeType: String = DocumentFileCompat.MIME_TYPE_UNKNOWN
)