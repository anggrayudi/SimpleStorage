package com.anggrayudi.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.PublicDirectory
import com.anggrayudi.storage.extension.openInputStream
import com.anggrayudi.storage.extension.openOutputStream
import com.anggrayudi.storage.file.child
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.getBasePath
import com.anggrayudi.storage.file.getStorageId
import com.anggrayudi.storage.file.isWritable
import com.anggrayudi.storage.file.toMediaFile
import com.anggrayudi.storage.file.toRawFile
import com.anggrayudi.storage.media.MediaFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * A single abstraction over the three file worlds on Android — SAF ([DocumentFile]), MediaStore
 * ([MediaFile]), and direct paths ([java.io.File]) — so callers no longer need to know which one
 * they are holding. Obtain instances via the factories in [StorageFile.Companion], then operate
 * with the extension functions in `StorageFileTransfer.kt` (`copyTo`, `moveTo`, `zipTo`, …).
 *
 * Implementations hold an application [Context] internally, so no function here asks for one.
 *
 * @author Anggrayudi H
 */
sealed interface StorageFile {

  val uri: Uri
  val name: String

  /** MIME type, or `null` for folders and unknown types. */
  val mimeType: String?

  val length: Long
  val isDirectory: Boolean
  val isFile: Boolean
  val exists: Boolean

  /** Milliseconds since epoch, or `0` when unknown. */
  val lastModified: Long

  /**
   * Absolute filesystem path like `/storage/emulated/0/Download/movie.mp4`, or `null` when the
   * file has no resolvable physical path (e.g. a `SingleDocumentFile` from the downloads
   * provider). This replaces v2's empty-string convention.
   */
  val absolutePath: String?

  /** [StoragePath] form of [absolutePath], or `null` for the same reason. */
  val path: StoragePath?

  val canRead: Boolean
  val canWrite: Boolean

  fun openInputStream(): InputStream?

  fun openOutputStream(append: Boolean = false): OutputStream?

  /** Direct children when [isDirectory], empty otherwise. */
  fun list(): List<StorageFile>

  /** Resolves a direct or nested child by path like `docs/report.pdf`. */
  fun child(path: String, requiresWriteAccess: Boolean = false): StorageFile?

  fun delete(): Boolean

  // Escape hatches to the underlying worlds:
  fun asDocumentFile(): DocumentFile?

  fun asMediaFile(): MediaFile?

  fun asRawFile(): File?

  companion object {
    /**
     * Wraps any URI this library understands: SAF tree/single URIs, `file://` URIs, and
     * MediaStore URIs (`content://media/...`).
     */
    @JvmStatic
    fun from(context: Context, uri: Uri): StorageFile? {
      val appContext = context.applicationContext
      if (uri.authority == MEDIA_AUTHORITY) {
        return MediaStorageFile(appContext, MediaFile(appContext, uri))
      }
      return DocumentFileCompat.fromUri(appContext, uri)?.let { DocumentStorageFile(appContext, it) }
    }

    @JvmStatic
    fun from(context: Context, file: File): StorageFile =
      DocumentStorageFile(context.applicationContext, DocumentFile.fromFile(file))

    /** Resolves a [StoragePath]; returns `null` when the path is not accessible. */
    @JvmStatic
    @JvmOverloads
    fun fromPath(
      context: Context,
      path: StoragePath,
      requiresWriteAccess: Boolean = false,
    ): StorageFile? {
      val appContext = context.applicationContext
      return DocumentFileCompat.fromSimplePath(
          appContext,
          path.storageId,
          path.basePath,
          requiresWriteAccess = requiresWriteAccess,
        )
        ?.let { DocumentStorageFile(appContext, it) }
    }

    /** Resolves an absolute path like `/storage/emulated/0/Download/movie.mp4`. */
    @JvmStatic
    @JvmOverloads
    fun fromPath(
      context: Context,
      absolutePath: String,
      requiresWriteAccess: Boolean = false,
    ): StorageFile? {
      val appContext = context.applicationContext
      return DocumentFileCompat.fromFullPath(
          appContext,
          absolutePath,
          requiresWriteAccess = requiresWriteAccess,
        )
        ?.let { DocumentStorageFile(appContext, it) }
    }

    @JvmStatic
    @JvmOverloads
    fun fromPublicDirectory(
      context: Context,
      type: PublicDirectory,
      subFile: String = "",
      requiresWriteAccess: Boolean = false,
    ): StorageFile? {
      val appContext = context.applicationContext
      return DocumentFileCompat.fromPublicFolder(appContext, type, subFile, requiresWriteAccess)
        ?.let { DocumentStorageFile(appContext, it) }
    }

    private const val MEDIA_AUTHORITY = "media"
  }
}

fun DocumentFile.toStorageFile(context: Context): StorageFile =
  DocumentStorageFile(context.applicationContext, this)

fun MediaFile.toStorageFile(context: Context): StorageFile =
  MediaStorageFile(context.applicationContext, this)

fun File.toStorageFile(context: Context): StorageFile = StorageFile.from(context, this)

fun Uri.toStorageFile(context: Context): StorageFile? = StorageFile.from(context, this)

internal class DocumentStorageFile(
  internal val context: Context,
  internal val doc: DocumentFile,
) : StorageFile {

  override val uri: Uri
    get() = doc.uri

  override val name: String
    get() = doc.name.orEmpty()

  override val mimeType: String?
    get() = doc.type

  override val length: Long
    get() = doc.length()

  override val isDirectory: Boolean
    get() = doc.isDirectory

  override val isFile: Boolean
    get() = doc.isFile

  override val exists: Boolean
    get() = doc.exists()

  override val lastModified: Long
    get() = doc.lastModified()

  override val absolutePath: String?
    get() = doc.getAbsolutePath(context).takeIf { it.isNotEmpty() }

  override val path: StoragePath?
    get() =
      if (absolutePath == null) null
      else StoragePath(doc.getStorageId(context), doc.getBasePath(context))

  override val canRead: Boolean
    get() = doc.canRead()

  override val canWrite: Boolean
    get() = doc.isWritable(context)

  override fun openInputStream(): InputStream? = doc.uri.openInputStream(context)

  override fun openOutputStream(append: Boolean): OutputStream? =
    doc.uri.openOutputStream(context, append)

  override fun list(): List<StorageFile> =
    if (isDirectory) doc.listFiles().map { DocumentStorageFile(context, it) } else emptyList()

  override fun child(path: String, requiresWriteAccess: Boolean): StorageFile? =
    doc.child(context, path, requiresWriteAccess)?.let { DocumentStorageFile(context, it) }

  override fun delete(): Boolean = doc.delete()

  override fun asDocumentFile(): DocumentFile = doc

  override fun asMediaFile(): MediaFile? = doc.toMediaFile(context)

  override fun asRawFile(): File? = doc.toRawFile(context)

  override fun equals(other: Any?): Boolean =
    other is DocumentStorageFile && other.uri == uri

  override fun hashCode(): Int = uri.hashCode()

  override fun toString(): String = uri.toString()
}

internal class MediaStorageFile(
  internal val context: Context,
  internal val media: MediaFile,
) : StorageFile {

  override val uri: Uri
    get() = media.uri

  override val name: String
    get() = media.fullName

  override val mimeType: String?
    get() = media.type

  override val length: Long
    get() = media.length

  override val isDirectory: Boolean
    get() = false

  override val isFile: Boolean
    get() = true

  override val exists: Boolean
    get() = media.toRawFile()?.exists() ?: (media.length > 0 || media.presentsInSafDatabase)

  override val lastModified: Long
    get() = media.lastModified

  override val absolutePath: String?
    get() = media.absolutePath.takeIf { it.isNotEmpty() }

  override val path: StoragePath?
    get() = absolutePath?.let { StoragePath.fromAbsolutePath(context, it) }

  override val canRead: Boolean
    get() = media.toRawFile()?.canRead() ?: true

  override val canWrite: Boolean
    get() = media.toRawFile()?.canWrite() ?: media.isMine

  override fun openInputStream(): InputStream? = media.openInputStream()

  override fun openOutputStream(append: Boolean): OutputStream? = media.openOutputStream(append)

  override fun list(): List<StorageFile> = emptyList()

  override fun child(path: String, requiresWriteAccess: Boolean): StorageFile? = null

  override fun delete(): Boolean = media.delete()

  override fun asDocumentFile(): DocumentFile? = media.toDocumentFile()

  override fun asMediaFile(): MediaFile = media

  override fun asRawFile(): File? = media.toRawFile()

  override fun equals(other: Any?): Boolean = other is MediaStorageFile && other.uri == uri

  override fun hashCode(): Int = uri.hashCode()

  override fun toString(): String = uri.toString()
}
