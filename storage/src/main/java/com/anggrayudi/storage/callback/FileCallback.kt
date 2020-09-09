package com.anggrayudi.storage.callback

import com.anggrayudi.storage.file.ErrorCode

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
interface FileCallback {

    /**
     * Given `freeSpace` and `fileSize`, then you decide whether the process will be continued or not.
     * You can give space tolerant here, e.g. 100MB
     *
     * @param freeSpace of target path
     * @return `true` to continue process
     */
    fun onCheckFreeSpace(freeSpace: Long, fileSize: Long): Boolean {
        // default implementation
        return true
    }

    fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int) {
        // default implementation
    }

    fun onFailed(errorCode: ErrorCode) {
        // default implementation
    }
}