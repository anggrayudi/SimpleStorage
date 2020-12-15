package com.anggrayudi.storage.extension

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import com.anggrayudi.storage.file.DocumentFileCompat
import java.io.*

/**
 * Created on 12/15/20
 * @author Anggrayudi H
 */

@JvmOverloads
@WorkerThread
fun Uri.openOutputStream(context: Context, append: Boolean = true): OutputStream? {
    return try {
        if (scheme == ContentResolver.SCHEME_FILE) {
            FileOutputStream(path, append)
        } else {
            val isExternalStorageDocument = authority == DocumentFileCompat.EXTERNAL_STORAGE_AUTHORITY
            context.contentResolver.openOutputStream(this, if (append && isExternalStorageDocument) "wa" else "w")
        }
    } catch (e: FileNotFoundException) {
        null
    }
}

@WorkerThread
fun Uri.openInputStream(context: Context): InputStream? {
    return try {
        if (scheme == ContentResolver.SCHEME_FILE) {
            // handle file from external storage
            FileInputStream(path)
        } else {
            context.contentResolver.openInputStream(this)
        }
    } catch (e: FileNotFoundException) {
        null
    }
}