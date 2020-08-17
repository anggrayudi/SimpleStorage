package com.anggrayudi.storage.callback

import com.anggrayudi.storage.ErrorCode

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
interface FileCallback {

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