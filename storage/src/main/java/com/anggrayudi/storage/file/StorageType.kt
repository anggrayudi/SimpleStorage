package com.anggrayudi.storage.file

import android.net.Uri
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.extension.getStorageId

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */
enum class StorageType {
    /**
     * Equals to primary storage.
     * @see [SimpleStorage.externalStoragePath]
     */
    EXTERNAL,
    DATA,
    SD_CARD,
    UNKNOWN;

    fun isExpected(actualStorageType: StorageType) = this == UNKNOWN || this == actualStorageType

    companion object {

        /**
         * @param storageId get it from [Uri.getStorageId]
         */
        @JvmStatic
        fun fromStorageId(storageId: String) = when {
            storageId == StorageId.PRIMARY -> EXTERNAL
            storageId == StorageId.DATA -> DATA
            storageId.matches(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}")) -> SD_CARD
            else -> UNKNOWN
        }
    }
}