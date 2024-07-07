package com.anggrayudi.storage.media

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.anggrayudi.storage.file.MimeType
import com.anggrayudi.storage.file.PublicDirectory
import java.io.File

/**
 * Created on 06/09/20
 * @author Anggrayudi H
 */
enum class MediaType(val readUri: Uri?, val writeUri: Uri?, val mimeType: String) {
    IMAGE(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Images.Media.getContentUri(MediaStoreCompat.volumeName),
        MimeType.IMAGE
    ),
    AUDIO(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Audio.Media.getContentUri(MediaStoreCompat.volumeName),
        MimeType.AUDIO
    ),
    VIDEO(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Video.Media.getContentUri(MediaStoreCompat.volumeName),
        MimeType.VIDEO
    ),
    DOWNLOADS(
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) null else MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) null else MediaStore.Downloads.getContentUri(
            MediaStoreCompat.volumeName
        ),
        MimeType.UNKNOWN
    );

    /**
     * Directories associated with this media type.
     */
    val directories: List<File>
        get() = when (this) {
            IMAGE -> MediaDirectory.Image.values()
                .map { Environment.getExternalStoragePublicDirectory(it.folderName) }

            AUDIO -> MediaDirectory.Audio.values()
                .map { Environment.getExternalStoragePublicDirectory(it.folderName) }

            VIDEO -> MediaDirectory.Video.values()
                .map { Environment.getExternalStoragePublicDirectory(it.folderName) }

            DOWNLOADS -> listOf(PublicDirectory.DOWNLOADS.file)
        }
}