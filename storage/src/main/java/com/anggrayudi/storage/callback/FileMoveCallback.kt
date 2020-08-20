package com.anggrayudi.storage.callback

import androidx.documentfile.provider.DocumentFile

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
interface FileMoveCallback : FileCallback {
    /**
     * Given `freeSpace` and `fileSize`, then you decide whether the copy process will be continued or not.
     * You can give space tolerant here, e.g. 100MB.
     * Only called when you to move the file into different storage ID.
     *
     * @param freeSpace of target path
     * @return `true` to continue process
     */
    override fun onCheckFreeSpace(freeSpace: Long, fileSize: Long): Boolean

    /**
     * @return Time interval to watch copy progress in milliseconds, otherwise `0` if you don't want to watch at all.
     */
    fun onStartMoving(): Long {
        return 0
    }

    /**
     * Only called if the returned [.onStartMoving] ()} greater than `0`
     *
     * @param progress   in percent
     * @param writeSpeed in bytes
     */
    override fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int) {
        // default implementation
    }

    /**
     * @param file newly moved file
     */
    fun onCompleted(file: DocumentFile) {
        // default implementation
    }
}