package com.anggrayudi.storage.sample.screen

import com.anggrayudi.storage.StorageFile
import com.anggrayudi.storage.sample.SampleScreen
import com.anggrayudi.storage.transfer.ConflictResolution
import com.anggrayudi.storage.transfer.TransferResult
import com.anggrayudi.storage.unzipTo
import com.anggrayudi.storage.zipTo

/** Zip and unzip use the same result vocabulary as copy and move. */
class ZipScreen : SampleScreen() {

  override val screenTitle = "Zip & unzip"

  override val screenSummary = "zipTo needs the archive to exist first; unzipTo takes a folder."

  override fun SampleScreen.buildScreen() {
    val root = playground(this@ZipScreen)

    button("Zip a folder") {
      val folder = root.createFolder("zip-source") ?: return@button
      folder.createFile("a.txt", "text/plain")?.openOutputStream()?.use {
        it.write("a".toByteArray())
      }
      folder.createFile("nested/b.txt", "text/plain")?.openOutputStream()?.use {
        it.write("b".toByteArray())
      }
      val archive = root.createFile("archive.zip", "application/zip") ?: return@button
      log(describe(listOf(folder).zipTo(archive) { onProgress { log("${it.percent.toInt()}%") } }))
    }

    button("Unzip it again") {
      val archive = root.child("archive.zip") ?: return@button log("Zip the folder first")
      val target = root.createFolder("unzipped") ?: return@button
      log(describe(archive.unzipTo(target) { onConflict { ConflictResolution.REPLACE } }))
      log("Extracted: ${target.list().map { it.name }}")
    }
  }

  private fun describe(result: TransferResult<StorageFile>): String =
    when (result) {
      is TransferResult.Success ->
        "Success: ${result.result.name} (${result.stats.filesTransferred} files)"
      is TransferResult.Skipped -> "Skipped"
      is TransferResult.Failure -> "Failed: ${result.errorCode} ${result.message.orEmpty()}"
    }
}
