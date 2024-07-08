package com.anggrayudi.storage.result

import androidx.documentfile.provider.DocumentFile

/**
 * Created on 7/6/24
 * @author Anggrayudi Hardiannico A.
 */
sealed interface ZipDecompressionResult {

    data object Validating : ZipDecompressionResult

    data class Decompressing(val bytesDecompressed: Long, val writeSpeed: Int, val fileCount: Int) :
        ZipDecompressionResult

    data class Completed(
        val zipFile: DecompressedZipFile,
        val targetFolder: DocumentFile,
        val bytesDecompressed: Long,
        val skippedDecompressedBytes: Long,
        val totalFilesDecompressed: Int,
        val decompressionRate: Float
    ) : ZipDecompressionResult

    data class Error(val errorCode: ZipDecompressionErrorCode, val message: String? = null) :
        ZipDecompressionResult
}

sealed interface DecompressedZipFile {

    @JvmInline
    value class MediaFile(val value: com.anggrayudi.storage.media.MediaFile) : DecompressedZipFile

    @JvmInline
    value class DocumentFile(val value: androidx.documentfile.provider.DocumentFile) :
        DecompressedZipFile
}

enum class ZipDecompressionErrorCode {
    STORAGE_PERMISSION_DENIED,
    CANNOT_CREATE_FILE_IN_TARGET,
    MISSING_ZIP_FILE,
    NOT_A_ZIP_FILE,
    UNKNOWN_IO_ERROR,
    CANCELED,
    NO_SPACE_LEFT_ON_TARGET_PATH
}