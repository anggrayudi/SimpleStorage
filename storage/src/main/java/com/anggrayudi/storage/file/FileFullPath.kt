package com.anggrayudi.storage.file

import android.content.Context
import android.net.Uri
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.extension.trimFileSeparator
import java.io.File

/**
 * Created on 21/09/21
 * @author Anggrayudi H
 */
class FileFullPath {

    val absolutePath: String
    val simplePath: String
    val storageId: String
    val basePath: String

    /**
     * @param fullPath can be simple path or absolute path
     */
    constructor(context: Context, fullPath: String) {
        if (fullPath.startsWith('/')) {
            when {
                fullPath.startsWith(SimpleStorage.externalStoragePath) -> {
                    storageId = StorageId.PRIMARY
                    val externalPath = SimpleStorage.externalStoragePath
                    basePath = fullPath.substringAfter(externalPath, "").trimFileSeparator()
                    simplePath = "$storageId:$basePath"
                    absolutePath = "$externalPath/$basePath".trimEnd('/')
                }
                fullPath.startsWith(context.dataDirectory.path) -> {
                    storageId = StorageId.DATA
                    val dataPath = context.dataDirectory.path
                    basePath = fullPath.substringAfter(dataPath, "").trimFileSeparator()
                    simplePath = "$storageId:$basePath"
                    absolutePath = "$dataPath/$basePath".trimEnd('/')
                }
                else -> {
                    storageId = fullPath.substringAfter("/storage/", "").substringBefore('/')
                    basePath = fullPath.substringAfter("/storage/$storageId", "").trimFileSeparator()
                    simplePath = "$storageId:$basePath"
                    absolutePath = "/storage/$storageId/$basePath".trimEnd('/')
                }
            }
        } else {
            simplePath = fullPath
            storageId = fullPath.substringBefore(':', "").substringAfterLast('/')
            basePath = fullPath.substringAfter(':', "").trimFileSeparator()
            absolutePath = when (storageId) {
                StorageId.PRIMARY -> "${SimpleStorage.externalStoragePath}/$basePath".trimEnd('/')
                StorageId.DATA -> "${context.dataDirectory.path}/$basePath".trimEnd('/')
                else -> "/storage/$storageId/$basePath".trimEnd('/')
            }
        }
    }

    constructor(context: Context, storageId: String, basePath: String) {
        this.storageId = storageId
        this.basePath = basePath.trimFileSeparator()
        simplePath = "$storageId:$basePath"
        absolutePath = when (storageId) {
            StorageId.PRIMARY -> "${SimpleStorage.externalStoragePath}/$basePath".trimEnd('/')
            StorageId.DATA -> "${context.dataDirectory.path}/$basePath".trimEnd('/')
            else -> "/storage/$storageId/$basePath".trimEnd('/')
        }
    }

    constructor(context: Context, file: File) : this(context, file.path.orEmpty())

    val uri: Uri
        get() = DocumentFileCompat.createDocumentUri(storageId, basePath)
}