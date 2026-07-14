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
 *
 * @author Anggrayudi H
 */

/** Closing stream safely */
public fun OutputStream?.closeStreamQuietly() {
  try {
    this?.close()
  } catch (_: IOException) {
    // ignore
  }
}

/** Closing stream safely */
public fun InputStream?.closeStreamQuietly() {
  try {
    this?.close()
  } catch (_: IOException) {
    // ignore
  }
}

/** Closing stream safely */
public fun Reader?.closeStreamQuietly() {
  try {
    this?.close()
  } catch (_: IOException) {
    // ignore
  }
}

public fun ZipInputStream?.closeEntryQuietly() {
  try {
    this?.closeEntry()
  } catch (_: Exception) {
    // ignore
  }
}

public fun ZipOutputStream?.closeEntryQuietly() {
  try {
    this?.closeEntry()
  } catch (_: IOException) {
    // ignore
  }
}

public fun Cursor.getString(column: String): String? =
  try {
    getString(getColumnIndexOrThrow(column))
  } catch (_: Exception) {
    null
  }
