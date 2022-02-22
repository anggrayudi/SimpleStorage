@file:JvmName("IOUtils")

package com.anggrayudi.storage.extension

import android.database.Cursor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */

/**
 * Closing stream safely
 */
fun OutputStream?.closeStreamQuietly() {
    try {
        this?.close()
    } catch (e: IOException) {
        // ignore
    }
}

/**
 * Closing stream safely
 */
fun InputStream?.closeStreamQuietly() {
    try {
        this?.close()
    } catch (e: IOException) {
        // ignore
    }
}

/**
 * Closing stream safely
 */
fun Reader?.closeStreamQuietly() {
    try {
        this?.close()
    } catch (e: IOException) {
        // ignore
    }
}

fun ZipInputStream?.closeEntryQuietly() {
    try {
        this?.closeEntry()
    } catch (e: Exception) {
        // ignore
    }
}

fun ZipOutputStream?.closeEntryQuietly() {
    try {
        this?.closeEntry()
    } catch (e: IOException) {
        // ignore
    }
}

fun Cursor.getString(column: String) = try {
    getString(getColumnIndexOrThrow(column))
} catch (e: Exception) {
    null
}