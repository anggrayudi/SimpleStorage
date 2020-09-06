package com.anggrayudi.storage.media

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

/**
 * Created on 05/09/20
 * @author Anggrayudi H
 */
object MediaStoreCompat {

    private val volumeName: String
        @SuppressLint("InlinedApi")
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL else MediaStore.VOLUME_EXTERNAL_PRIMARY

    @RequiresApi(Build.VERSION_CODES.Q)
    fun createDownload(context: Context, file: FileDescription): MediaFile? {
        return createMedia(context, MediaStore.Downloads.getContentUri(volumeName), file, Environment.DIRECTORY_DOWNLOADS)
    }

    fun createImage(context: Context, file: FileDescription, relativeParentDirectory: ImageMediaDirectory? = null): MediaFile? {
        return createMedia(context, MediaStore.Images.Media.getContentUri(volumeName), file, relativeParentDirectory?.folderName)
    }

    fun createAudio(context: Context, file: FileDescription, relativeParentDirectory: AudioMediaDirectory? = null): MediaFile? {
        return createMedia(context, MediaStore.Audio.Media.getContentUri(volumeName), file, relativeParentDirectory?.folderName)
    }

    fun createVideo(context: Context, file: FileDescription, relativeParentDirectory: ImageMediaDirectory? = null): MediaFile? {
        return createMedia(context, MediaStore.Video.Media.getContentUri(volumeName), file, relativeParentDirectory?.folderName)
    }

    @Suppress("DEPRECATION")
    @Throws(IOException::class)
    private fun createMedia(context: Context, uri: Uri, file: FileDescription, folderName: String?): MediaFile? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.apply {
                put(MediaStore.MediaColumns.OWNER_PACKAGE_NAME, context.packageName)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (folderName != null) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$folderName/${file.subFolder}")
                }
            }
            MediaFile(
                context,
                context.contentResolver.insert(uri, contentValues)
                    ?: throw IOException("Unable to create media file: ${file.name} in directory $folderName/${file.subFolder}")
            )
        } else {
            val externalDirectory = Environment.getExternalStoragePublicDirectory(folderName)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                if (Environment.MEDIA_MOUNTED_READ_ONLY == Environment.getExternalStorageState()) {
                    throw IOException("External storage not currently available")
                }
            } else if (Environment.MEDIA_MOUNTED_READ_ONLY == Environment.getExternalStorageState(externalDirectory)) {
                throw IOException("External storage not currently available")
            }

            var media = File("$externalDirectory/${file.subFolder}", file.name)
            val parentFile = media.parentFile!!
            parentFile.mkdirs()
            if (media.isFile) {
                // Tack on extension when valid MIME type provided
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(file.mimeType)
                // TODO: 06/09/20 Use Regex
                val regex = Regex(" (1)")
                val filename = if (regex.matches(file.name)) {
                    val lastFile = parentFile.listFiles { _, name -> regex.matches(name) }.orEmpty().filter { it.isFile }.toList().max()
                    var count = lastFile?.name.orEmpty().substringAfterLast('(').substringBefore(')').toInt()
                    "${file.name} (${++count})"
                } else {
                    "${file.name} (1)"
                }
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                media = File(parentFile, filename)
            }
            context.contentResolver.insert(uri, contentValues)?.let {
                media.createNewFile()
                MediaFile(context, it)
            }
        }
    }
}