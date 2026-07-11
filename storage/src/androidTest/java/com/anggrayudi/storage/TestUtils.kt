package com.anggrayudi.storage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.anggrayudi.storage.media.FileDescription
import com.anggrayudi.storage.media.MediaFile
import com.anggrayudi.storage.media.MediaStoreCompat
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

/**
 * Shared helpers for the v3 device test pass (`V3_TEST_CASES.md`, Groups 1-6). Every test uses
 * `context.getExternalFilesDir(null)` as its playground so no SAF grant or runtime permission is
 * required, per the instructions in `V3_TEST_CASES.md`.
 */
internal fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

/** A fresh, uniquely-named directory under app-external storage for one test to work in. */
internal fun newPlaygroundDir(prefix: String): File {
  val dir = File(targetContext().getExternalFilesDir(null), "${prefix}_${UUID.randomUUID()}")
  check(dir.mkdirs()) { "Could not create playground dir $dir" }
  return dir
}

internal fun File.md5(): String = inputStream().use { it.md5() }

internal fun java.io.InputStream.md5(): String {
  val digest = MessageDigest.getInstance("MD5")
  val buffer = ByteArray(8 * 1024)
  while (true) {
    val read = read(buffer)
    if (read < 0) break
    digest.update(buffer, 0, read)
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun StorageFile.md5(): String =
  openInputStream()?.use { it.md5() } ?: error("Cannot open input stream for $name")

/** Writes [sizeBytes] of deterministic pseudo-random content to this file. */
internal fun File.writeRandomBytes(sizeBytes: Int, seed: Long = 42L) {
  val random = Random(seed)
  outputStream().use { out ->
    val buffer = ByteArray(64 * 1024)
    var remaining = sizeBytes
    while (remaining > 0) {
      val chunk = minOf(buffer.size, remaining)
      random.nextBytes(buffer, 0, chunk)
      out.write(buffer, 0, chunk)
      remaining -= chunk
    }
  }
}

internal fun File.deleteRecursivelyOrThrow() {
  if (exists() && !deleteRecursively()) {
    error("Could not delete $this")
  }
}

/**
 * Inserts a new row into `MediaStore.Downloads` scoped to this app's own files (no permission
 * required) and writes [content] into it. Caller is responsible for calling `MediaFile.delete()`.
 */
internal fun insertDownloadsMedia(name: String, content: ByteArray): MediaFile {
  val media =
    MediaStoreCompat.createDownload(targetContext(), FileDescription(name, "SimpleStorageTest"))
      ?: error("Could not insert MediaStore row for $name")
  media.openOutputStream(append = false)?.use { it.write(content) }
    ?: error("Could not open output stream for $name")
  return media
}
