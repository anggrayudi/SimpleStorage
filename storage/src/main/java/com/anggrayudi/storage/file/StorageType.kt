package com.anggrayudi.storage.file

import com.anggrayudi.storage.SimpleStorage

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
    UNKNOWN
}