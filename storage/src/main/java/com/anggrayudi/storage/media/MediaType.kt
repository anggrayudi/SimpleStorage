package com.anggrayudi.storage.media

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/**
 * Created on 06/09/20
 * @author Anggrayudi H
 */
enum class MediaType(val readUri: Uri, val writeUri: Uri) {
    IMAGE(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.getContentUri(MediaStoreCompat.volumeName)),
    AUDIO(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.getContentUri(MediaStoreCompat.volumeName)),
    VIDEO(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.getContentUri(MediaStoreCompat.volumeName)),

    @RequiresApi(Build.VERSION_CODES.Q)
    DOWNLOADS(MediaStore.Downloads.EXTERNAL_CONTENT_URI, MediaStore.Downloads.getContentUri(MediaStoreCompat.volumeName))
}