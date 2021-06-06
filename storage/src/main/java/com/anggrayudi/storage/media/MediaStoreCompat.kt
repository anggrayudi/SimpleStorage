package com.anggrayudi.storage.media

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.extension.trimFileSeparator
import com.anggrayudi.storage.file.*
import com.anggrayudi.storage.file.DocumentFileCompat.removeForbiddenCharsFromFilename
import java.io.File

/**
 * Created on 05/09/20
 * @author Anggrayudi H
 */
object MediaStoreCompat {

    @JvmStatic
    val volumeName: String
        @SuppressLint("InlinedApi")
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL else MediaStore.VOLUME_EXTERNAL_PRIMARY

    @JvmStatic
    @JvmOverloads
    fun createDownload(context: Context, file: FileDescription, mode: CreateMode = CreateMode.CREATE_NEW): MediaFile? {
        return createMedia(context, MediaType.DOWNLOADS, Environment.DIRECTORY_DOWNLOADS, file, mode)
    }

    @JvmOverloads
    @JvmStatic
    fun createImage(
        context: Context,
        file: FileDescription,
        relativeParentDirectory: ImageMediaDirectory = ImageMediaDirectory.PICTURES,
        mode: CreateMode = CreateMode.CREATE_NEW
    ): MediaFile? {
        return createMedia(context, MediaType.IMAGE, relativeParentDirectory.folderName, file, mode)
    }

    @JvmOverloads
    @JvmStatic
    fun createAudio(
        context: Context,
        file: FileDescription,
        relativeParentDirectory: AudioMediaDirectory = AudioMediaDirectory.MUSIC,
        mode: CreateMode = CreateMode.CREATE_NEW
    ): MediaFile? {
        return createMedia(context, MediaType.AUDIO, relativeParentDirectory.folderName, file, mode)
    }

    @JvmOverloads
    @JvmStatic
    fun createVideo(
        context: Context,
        file: FileDescription,
        relativeParentDirectory: VideoMediaDirectory = VideoMediaDirectory.MOVIES,
        mode: CreateMode = CreateMode.CREATE_NEW
    ): MediaFile? {
        return createMedia(context, MediaType.VIDEO, relativeParentDirectory.folderName, file, mode)
    }

    private fun createMedia(context: Context, mediaType: MediaType, folderName: String, file: FileDescription, mode: CreateMode): MediaFile? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dateCreated = System.currentTimeMillis()
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
                put(MediaStore.MediaColumns.DATE_ADDED, dateCreated)
                put(MediaStore.MediaColumns.DATE_MODIFIED, dateCreated)
            }
            val relativePath = "$folderName/${file.subFolder}".trimFileSeparator()
            contentValues.apply {
                put(MediaStore.MediaColumns.OWNER_PACKAGE_NAME, context.packageName)
                if (relativePath.isNotBlank()) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
            }
            var existingMedia = fromBasePath(context, mediaType, "$relativePath/${file.name}")
            when {
                existingMedia?.isEmpty == true -> existingMedia
                existingMedia?.exists == true -> {
                    if (mode == CreateMode.REUSE) {
                        return existingMedia
                    }
                    if (mode == CreateMode.REPLACE) {
                        existingMedia.delete()
                        return MediaFile(context, context.contentResolver.insert(mediaType.writeUri!!, contentValues) ?: return null)
                    }

                    /*
                    We use this file duplicate handler because it is better than the system's.
                    This handler also fixes Android 10's media file duplicate handler. Here's how to reproduce:
                    1) Use Android 10. Let's say there's a file named Pictures/profile.png with media ID 25.
                    2) Create an image file with ContentValues using the same name (profile) & mime type (image/png), under Pictures directory too.
                    3) A new media file is created into the file database with ID 26, but it uses the old file,
                       instead of creating a new file named profile (1).png. On Android 11, it will be profile (1).png.
                     */
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(file.mimeType) ?: file.name.substringAfterLast('.', "")
                    val baseName = file.name.substringBeforeLast('.')
                    val prefix = "$baseName ("
                    val lastFile = fromFileNameContains(context, mediaType, baseName)
                        .filter { relativePath.isBlank() || relativePath == it.relativePath.removeSuffix("/") }
                        .mapNotNull { it.name }
                        .filter {
                            it.startsWith(prefix) && (DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITH_EXTENSION.matches(it)
                                    || DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(it))
                        }
                        .maxOfOrNull { it }
                        .orEmpty()
                    var count = lastFile.substringAfterLast('(', "")
                        .substringBefore(')', "")
                        .toIntOrNull() ?: 0

                    existingMedia = fromFileName(context, mediaType, "$baseName ($count).$ext".trimEnd('.'))
                    // Check if file exists, but has zero length
                    if (existingMedia?.openInputStream()?.use { it.available() == 0 } == true) {
                        existingMedia
                    } else {
                        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName (${++count}).$ext".trimEnd('.'))
                        MediaFile(context, context.contentResolver.insert(mediaType.writeUri!!, contentValues) ?: return null)
                    }
                }
                else -> MediaFile(context, context.contentResolver.insert(mediaType.writeUri!!, contentValues) ?: return null)
            }
        } else {
            @Suppress("DEPRECATION")
            val publicDirectory = Environment.getExternalStoragePublicDirectory(folderName)
            if (publicDirectory.canModify(context)) {
                val filename = file.fullName
                var media = File("$publicDirectory/${file.subFolder}", filename)
                val parentFile = media.parentFile ?: return null
                parentFile.mkdirs()
                if (media.exists() && mode == CreateMode.CREATE_NEW) {
                    media = parentFile.child(parentFile.autoIncrementFileName(filename))
                }
                if (mode == CreateMode.REPLACE && !media.recreateFile()) {
                    return null
                }
                if (media.createNewFileIfPossible()) {
                    if (media.canRead()) MediaFile(context, media) else null
                } else null
            } else {
                null
            }
        }
    }

    @JvmStatic
    fun fromMediaId(context: Context, mediaType: MediaType, id: String): MediaFile? {
        return mediaType.writeUri?.let { MediaFile(context, it.buildUpon().appendPath(id).build()) }
    }

    @JvmStatic
    fun fromMediaId(context: Context, mediaType: MediaType, id: Long): MediaFile? {
        return fromMediaId(context, mediaType, id.toString())
    }

    @JvmStatic
    fun fromFileName(context: Context, mediaType: MediaType, name: String): MediaFile? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name).let {
                if (it.isFile && it.canRead()) MediaFile(context, it) else null
            }
        } else {
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            context.contentResolver.query(mediaType.readUri ?: return null, arrayOf(BaseColumns._ID), selection, arrayOf(name), null)?.use {
                fromCursorToMediaFile(context, mediaType, it)
            }
        }
    }

    /**
     * @param basePath is relative path + filename
     * @return `null` if base path does not contain relative path or the media is not found
     */
    @JvmStatic
    fun fromBasePath(context: Context, mediaType: MediaType, basePath: String): MediaFile? {
        val cleanBasePath = basePath.removeForbiddenCharsFromFilename().trimFileSeparator()
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStorageDirectory(), cleanBasePath).let { if (it.isFile && it.canRead()) MediaFile(context, it) else null }
        } else {
            val relativePath = cleanBasePath.substringBeforeLast('/', "")
            if (relativePath.isEmpty()) {
                return null
            }
            val filename = cleanBasePath.substringAfterLast('/')
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            context.contentResolver.query(mediaType.readUri ?: return null, arrayOf(BaseColumns._ID), selection, arrayOf(filename, "$relativePath/"), null)
                ?.use { fromCursorToMediaFile(context, mediaType, it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaTypeFromRelativePath(cleanRelativePath: String) = when (cleanRelativePath) {
        Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES -> MediaType.IMAGE
        Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DCIM -> MediaType.VIDEO
        Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_PODCASTS, Environment.DIRECTORY_RINGTONES,
        Environment.DIRECTORY_ALARMS, Environment.DIRECTORY_NOTIFICATIONS -> MediaType.AUDIO
        Environment.DIRECTORY_DOWNLOADS -> MediaType.DOWNLOADS
        else -> null
    }

    /**
     * @see MediaStore.MediaColumns.RELATIVE_PATH
     */
    @JvmStatic
    fun fromRelativePath(context: Context, publicDirectory: PublicDirectory) = fromRelativePath(context, publicDirectory.folderName)

    /**
     * @see MediaStore.MediaColumns.RELATIVE_PATH
     */
    @JvmStatic
    fun fromRelativePath(context: Context, relativePath: String): List<MediaFile> {
        val cleanRelativePath = relativePath.trimFileSeparator()
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            DocumentFile.fromFile(File(Environment.getExternalStorageDirectory(), cleanRelativePath))
                .search(true, documentType = DocumentFileType.FILE)
                .map { MediaFile(context, File(it.uri.path!!)) }
        } else {
            val mediaType = mediaTypeFromRelativePath(cleanRelativePath) ?: return emptyList()
            val relativePathWithSlashSuffix = relativePath.trimEnd('/') + '/'
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} IN(?, ?)"
            val selectionArgs = arrayOf(relativePathWithSlashSuffix, cleanRelativePath)
            return context.contentResolver.query(mediaType.readUri ?: return emptyList(), arrayOf(BaseColumns._ID), selection, selectionArgs, null)?.use {
                fromCursorToMediaFiles(context, mediaType, it)
            }.orEmpty()
        }
    }

    /**
     * @see MediaStore.MediaColumns.RELATIVE_PATH
     */
    @JvmStatic
    fun fromRelativePath(context: Context, relativePath: String, name: String): MediaFile? {
        val cleanRelativePath = relativePath.trimFileSeparator()
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            DocumentFile.fromFile(File(Environment.getExternalStorageDirectory(), cleanRelativePath))
                .search(true, documentType = DocumentFileType.FILE, name = name)
                .map { MediaFile(context, File(it.uri.path!!)) }
                .firstOrNull()
        } else {
            val mediaType = mediaTypeFromRelativePath(cleanRelativePath) ?: return null
            val relativePathWithSlashSuffix = relativePath.trimEnd('/') + '/'
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} IN(?, ?)"
            val selectionArgs = arrayOf(name, relativePathWithSlashSuffix, cleanRelativePath)
            return context.contentResolver.query(mediaType.readUri ?: return null, arrayOf(BaseColumns._ID), selection, selectionArgs, null)?.use {
                fromCursorToMediaFile(context, mediaType, it)
            }
        }
    }

    @JvmStatic
    fun fromFileNameContains(context: Context, mediaType: MediaType, containsName: String): List<MediaFile> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mediaType.directories.map { directory ->
                @Suppress("DEPRECATION")
                DocumentFile.fromFile(directory)
                    .search(true, regex = Regex("^.*$containsName.*\$"), mimeTypes = arrayOf(mediaType.mimeType))
                    .map { MediaFile(context, File(it.uri.path!!)) }
            }.flatten()
        } else {
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%$containsName%'"
            return context.contentResolver.query(mediaType.readUri ?: return emptyList(), arrayOf(BaseColumns._ID), selection, null, null)?.use {
                fromCursorToMediaFiles(context, mediaType, it)
            }.orEmpty()
        }
    }

    @JvmStatic
    fun fromMimeType(context: Context, mediaType: MediaType, mimeType: String): List<MediaFile> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mediaType.directories.map { directory ->
                @Suppress("DEPRECATION")
                DocumentFile.fromFile(directory)
                    .search(true, documentType = DocumentFileType.FILE, mimeTypes = arrayOf(mimeType))
                    .map { MediaFile(context, File(it.uri.path!!)) }
            }.flatten()
        } else {
            val selection = "${MediaStore.MediaColumns.MIME_TYPE} = ?"
            return context.contentResolver.query(mediaType.readUri ?: return emptyList(), arrayOf(BaseColumns._ID), selection, arrayOf(mimeType), null)?.use {
                fromCursorToMediaFiles(context, mediaType, it)
            }.orEmpty()
        }
    }

    @JvmStatic
    fun fromMediaType(context: Context, mediaType: MediaType): List<MediaFile> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mediaType.directories.map { directory ->
                @Suppress("DEPRECATION")
                DocumentFile.fromFile(directory)
                    .search(true, mimeTypes = arrayOf(mediaType.mimeType))
                    .map { MediaFile(context, File(it.uri.path!!)) }
            }.flatten()
        } else {
            return context.contentResolver.query(mediaType.readUri ?: return emptyList(), arrayOf(BaseColumns._ID), null, null, null)?.use {
                fromCursorToMediaFiles(context, mediaType, it)
            }.orEmpty()
        }
    }

    private fun fromCursorToMediaFiles(context: Context, mediaType: MediaType, cursor: Cursor): List<MediaFile> {
        if (cursor.moveToFirst()) {
            val columnId = cursor.getColumnIndex(BaseColumns._ID)
            val mediaFiles = ArrayList<MediaFile>(cursor.count)
            do {
                val mediaId = cursor.getString(columnId)
                fromMediaId(context, mediaType, mediaId)?.let { mediaFiles.add(it) }
            } while (cursor.moveToNext())
            return mediaFiles
        }
        return emptyList()
    }

    private fun fromCursorToMediaFile(context: Context, mediaType: MediaType, cursor: Cursor): MediaFile? {
        return if (cursor.moveToFirst()) {
            val mediaId = cursor.getString(cursor.getColumnIndex(BaseColumns._ID))
            fromMediaId(context, mediaType, mediaId)
        } else null
    }
}