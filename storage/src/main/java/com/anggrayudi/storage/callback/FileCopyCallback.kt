package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.media.MediaFile

/**
 * Created on 16/08/20
 *
 * @author Anggrayudi H
 */
interface FileCopyCallback : FileCallback {
    /**
     * Given `freeSpace` and `fileSize`, then you decide whether the copy process will be continued or not.
     * You can give space tolerant here, e.g. 100MB
     *
     * @param freeSpace of target path
     * @return `true` to continue process
     */
    override fun onCheckFreeSpace(freeSpace: Long, fileSize: Long): Boolean

    /**
     * @param file can be [DocumentFile] or [MediaFile]
     * @return Time interval to watch copy progress in milliseconds, otherwise `0` if you don't want to watch at all.
     */
    fun onStartCopying(file: Any): Long {
        // default implementation
        return 0
    }

    /**
     * Only called if the returned [.onStartCopying] greater than `0`
     *
     * @param progress   in percent
     * @param writeSpeed in bytes
     */
    override fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int) {
        // default implementation
    }

    /**
     * @param file can be [DocumentFile] or [MediaFile]
     * @return `true` if you want to delete previous file
     */
    fun onCompleted(file: Any): Boolean {
        // default implementation
        return false
    }
}