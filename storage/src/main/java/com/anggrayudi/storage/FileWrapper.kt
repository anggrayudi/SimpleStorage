package com.anggrayudi.storage

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.*
import com.anggrayudi.storage.media.MediaFile
import java.io.InputStream
import java.io.OutputStream

/**
 * [DocumentFile] is developed by Google and is impossible to share the same interface with [MediaFile]
 * that is developed by me, because they have different behavior and capabilities.
 * So we need to create an abstraction by checking the class type.
 * They both may share the same functions, e.g. delete, get file name, open stream, etc.
 *
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version FileWrapper, 09/07/22 18.41
 */
class FileWrapper {

    /**
     * Either [DocumentFile], [MediaFile], or `null`
     */
    val file: Any?

    constructor(file: MediaFile?) {
        this.file = file
    }

    constructor(file: DocumentFile?) {
        this.file = file
    }

    val uri: Uri?
        get() = when (file) {
            is MediaFile -> file.uri
            is DocumentFile -> file.uri
            else -> null
        }

    val name: String?
        get() = when (file) {
            is MediaFile -> file.name
            is DocumentFile -> file.name
            else -> null
        }

    val baseName: String?
        get() = when (file) {
            is MediaFile -> file.baseName
            is DocumentFile -> file.baseName
            else -> null
        }

    val extension: String?
        get() = when (file) {
            is MediaFile -> file.extension
            is DocumentFile -> file.extension
            else -> null
        }

    val mimeType: String?
        get() = when (file) {
            is MediaFile -> file.mimeType
            is DocumentFile -> file.mimeTypeByFileName
            else -> null
        }

    fun isEmpty(context: Context): Boolean = when (file) {
        is MediaFile -> file.isEmpty
        is DocumentFile -> file.isEmpty(context)
        else -> false
    }

    fun getAbsolutePath(context: Context): String = when (file) {
        is MediaFile -> file.absolutePath
        is DocumentFile -> file.getAbsolutePath(context)
        else -> ""
    }

    fun getBasePath(context: Context): String = when (file) {
        is MediaFile -> file.basePath
        is DocumentFile -> file.getBasePath(context)
        else -> ""
    }

    fun getRelativePath(context: Context): String = when (file) {
        is MediaFile -> file.relativePath
        is DocumentFile -> file.getRelativePath(context)
        else -> ""
    }

    @WorkerThread
    fun openOutputStream(context: Context, append: Boolean = true): OutputStream? = when (file) {
        is MediaFile -> file.openOutputStream(append)
        is DocumentFile -> file.openOutputStream(context, append)
        else -> null
    }

    @WorkerThread
    fun openInputStream(context: Context): InputStream? = when (file) {
        is MediaFile -> file.openInputStream()
        is DocumentFile -> file.openInputStream(context)
        else -> null
    }

    fun delete(): Boolean = when (file) {
        is MediaFile -> file.delete()
        is DocumentFile -> file.delete()
        else -> false
    }
}