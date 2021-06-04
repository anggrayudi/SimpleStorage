package com.anggrayudi.storage.file

import android.webkit.MimeTypeMap

/**
 * See [mime type list](https://www.freeformatter.com/mime-types-list.html)
 *
 * Created on 03/06/21
 * @author Anggrayudi H
 */
object MimeType {
    const val UNKNOWN = "*/*"
    const val BINARY_FILE = "application/octet-stream"
    const val IMAGE = "image/*"
    const val AUDIO = "audio/*"
    const val VIDEO = "video/*"
    const val TEXT = "text/*"
    const val FONT = "font/*"
    const val APPLICATION = "application/*"
    const val CHEMICAL = "chemical/*"
    const val MODEL = "model/*"

    /**
     * * Given `name` = `ABC` AND `mimeType` = `video/mp4`, then return `ABC.mp4`
     * * Given `name` = `ABC` AND `mimeType` = `null`, then return `ABC`
     * * Given `name` = `ABC.mp4` AND `mimeType` = `video/mp4`, then return `ABC.mp4`
     * @param name can have file extension or not
     */
    @JvmStatic
    fun getFullFileName(name: String, mimeType: String?): String {
        // Prior to API 29, MimeType.BINARY_FILE has no file extension
        return getExtensionFromMimeType(mimeType).let { if (it.isEmpty() || name.endsWith(".$it")) name else "$name.$it".trimEnd('.') }
    }

    /**
     * Some mime types return no file extension on older API levels. This function adds compatibility accross API levels.
     * @see getExtensionFromMimeTypeOrFileName
     */
    @JvmStatic
    fun getExtensionFromMimeType(mimeType: String?): String {
        return mimeType?.let { if (it == BINARY_FILE) "bin" else MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }.orEmpty()
    }

    /**
     * @see getExtensionFromMimeType
     */
    @JvmStatic
    fun getExtensionFromMimeTypeOrFileName(mimeType: String?, filename: String): String {
        return if (mimeType == null || mimeType == UNKNOWN) filename.substringAfterLast('.', "") else getExtensionFromMimeType(mimeType)
    }

    /**
     * Some file types return no mime type on older API levels. This function adds compatibility accross API levels.
     */
    @JvmStatic
    fun getMimeTypeFromExtension(fileExtension: String): String {
        return if (fileExtension == "bin") BINARY_FILE else MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension) ?: UNKNOWN
    }
}