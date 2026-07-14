@file:JvmName("UriUtils")

package com.anggrayudi.storage.extension

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageId.PRIMARY
import com.anggrayudi.storage.file.getStorageId
import com.anggrayudi.storage.media.MediaFile
import java.io.*

/**
 * Created on 12/15/20
 *
 * @author Anggrayudi H
 */

/** If given [Uri] with path `/tree/primary:Downloads/MyVideo.mp4`, then return `primary`. */
public fun Uri.getStorageId(context: Context): String {
  val path = path.orEmpty()
  return if (isRawFile) {
    File(path).getStorageId(context)
  } else
    when {
      isDownloadsDocument || isDocumentsDocument -> PRIMARY
      isExternalStorageDocument -> path.substringBefore(':', "").substringAfterLast('/')
      else -> ""
    }
}

public val Uri.isTreeDocumentFile: Boolean
  get() = path?.startsWith("/tree/") == true

public val Uri.isExternalStorageDocument: Boolean
  get() = authority == DocumentFileCompat.EXTERNAL_STORAGE_AUTHORITY

public val Uri.isDownloadsDocument: Boolean
  get() = authority == DocumentFileCompat.DOWNLOADS_FOLDER_AUTHORITY

/** For URI [DocumentFileCompat.DOCUMENTS_TREE_URI] */
public val Uri.isDocumentsDocument: Boolean
  get() =
    isExternalStorageDocument &&
      path?.let { it.startsWith("/tree/home:") || it.startsWith("/document/home:") } == true

public val Uri.isMediaDocument: Boolean
  get() = authority == DocumentFileCompat.MEDIA_FOLDER_AUTHORITY

public val Uri.isRawFile: Boolean
  get() = scheme == ContentResolver.SCHEME_FILE

public val Uri.isMediaFile: Boolean
  get() = authority == MediaStore.AUTHORITY

public fun Uri.toMediaFile(context: Context): MediaFile? = if (isMediaFile) MediaFile(context, this) else null

public fun Uri.toDocumentFile(context: Context): DocumentFile? = DocumentFileCompat.fromUri(context, this)

@JvmOverloads
@WorkerThread
public fun Uri.openOutputStream(context: Context, append: Boolean = true): OutputStream? {
  return try {
    if (isRawFile) {
      FileOutputStream(File(path ?: return null), append)
    } else {
      context.contentResolver.openOutputStream(
        this,
        if (append && isTreeDocumentFile) "wa" else "w",
      )
    }
  } catch (_: IOException) {
    null
  }
}

@WorkerThread
public fun Uri.openInputStream(context: Context): InputStream? {
  return try {
    if (isRawFile) {
      // handle file from external storage
      FileInputStream(File(path ?: return null))
    } else {
      context.contentResolver.openInputStream(this)
    }
  } catch (_: IOException) {
    null
  }
}
