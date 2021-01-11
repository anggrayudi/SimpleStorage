@file:JvmName("IOUtils")

package com.anggrayudi.storage.extension

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */

/**
 * Closing stream safely
 */
fun OutputStream?.closeStream() {
    try {
        this?.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

/**
 * Closing stream safely
 */
fun InputStream?.closeStream() {
    try {
        this?.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

/**
 * Closing stream safely
 */
fun Reader?.closeStream() {
    try {
        this?.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}