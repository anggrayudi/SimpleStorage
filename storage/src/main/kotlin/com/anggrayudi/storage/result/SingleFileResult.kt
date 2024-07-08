package com.anggrayudi.storage.result

/**
 * Created on 7/6/24
 * @author Anggrayudi Hardiannico A.
 */
sealed interface SingleFileResult {
    data object Validating : SingleFileResult
    data object Preparing : SingleFileResult
    data object DeletingConflictedFile : SingleFileResult
    data class InProgress(val progress: Float, val bytesMoved: Long, val writeSpeed: Int) :
        SingleFileResult

    interface Completed : SingleFileResult {
        @JvmInline
        value class MediaFile(val value: com.anggrayudi.storage.media.MediaFile) : Completed

        @JvmInline
        value class DocumentFile(val value: androidx.documentfile.provider.DocumentFile) : Completed

        companion object {
            internal fun get(file: Any): Completed =
                when (file) {
                    is com.anggrayudi.storage.media.MediaFile -> MediaFile(file)
                    is androidx.documentfile.provider.DocumentFile -> DocumentFile(file)
                    else -> throw IllegalArgumentException("File must be either of type ${com.anggrayudi.storage.media.MediaFile::class.java.name} or ${androidx.documentfile.provider.DocumentFile::class.java.name}")
                }
        }
    }

    data class Error(val errorCode: SingleFileErrorCode, val message: String? = null) :
        SingleFileResult
}

enum class SingleFileErrorCode {
    STORAGE_PERMISSION_DENIED,
    CANNOT_CREATE_FILE_IN_TARGET,
    SOURCE_FILE_NOT_FOUND,
    TARGET_FILE_NOT_FOUND,
    TARGET_FOLDER_NOT_FOUND,
    UNKNOWN_IO_ERROR,
    CANCELED,
    TARGET_FOLDER_CANNOT_HAVE_SAME_PATH_WITH_SOURCE_FOLDER,
    NO_SPACE_LEFT_ON_TARGET_PATH
}
