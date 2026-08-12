package com.anggrayudi.storage.sample.screen

import com.anggrayudi.storage.StorageFile
import com.anggrayudi.storage.copyTo
import com.anggrayudi.storage.copyToFile
import com.anggrayudi.storage.moveTo
import com.anggrayudi.storage.sample.SampleScreen
import com.anggrayudi.storage.transfer.ConflictResolution
import com.anggrayudi.storage.transfer.TransferResult

/** Copy and move: one suspend call, one result type, and a suspend lambda for conflicts. */
class TransferScreen : SampleScreen() {

  override val screenTitle = "Copy, move & multi-source"

  override val screenSummary =
    "copyTo/moveTo take a folder; copyToFile targets a file that already exists."

  override fun SampleScreen.buildScreen() {
    val root = playground(this@TransferScreen)

    button("Copy one file, with progress") {
      val source = sourceFile(root, "single.txt", "copy me")
      val target = root.createFolder("target-single") ?: return@button
      val result =
        source.copyTo(target) {
          updateInterval = 100
          onProgress { log("${it.percent.toInt()}% — ${it.bytesPerSecond} B/s") }
          onConflict { ConflictResolution.REPLACE }
        }
      log(describe(result))
    }

    button("Copy a folder recursively") {
      val folder = root.createFolder("tree") ?: return@button
      folder.createFile("a.txt", "text/plain")?.openOutputStream()?.use {
        it.write("a".toByteArray())
      }
      folder.createFile("sub/b.txt", "text/plain")?.openOutputStream()?.use {
        it.write("b".toByteArray())
      }
      val target = root.createFolder("target-tree") ?: return@button
      log(describe(folder.copyTo(target) { onConflict { ConflictResolution.MERGE } }))
    }

    button("Copy several sources at once") {
      val one = sourceFile(root, "one.txt", "1")
      val two = sourceFile(root, "two.txt", "2")
      val target = root.createFolder("target-multi") ?: return@button
      when (
        val result = listOf(one, two).copyTo(target) { onConflict { ConflictResolution.REPLACE } }
      ) {
        is TransferResult.Success -> log("Copied ${result.result.map { it.name }}")
        is TransferResult.Skipped -> log("Skipped")
        is TransferResult.Failure -> log("Failed: ${result.errorCode}")
      }
    }

    button("Move a file into an existing file") {
      val source = sourceFile(root, "moving.txt", "moved content")
      val target = root.createFile("existing-target.txt", "text/plain") ?: return@button
      log(describe(source.copyToFile(target)))
      log("Target now holds: ${target.openInputStream()?.use { String(it.readBytes()) }}")
    }

    button("Move a file into a folder") {
      val source = sourceFile(root, "to-move.txt", "bye")
      val target = root.createFolder("target-move") ?: return@button
      log(describe(source.moveTo(target) { onConflict { ConflictResolution.REPLACE } }))
    }
  }

  private fun sourceFile(root: StorageFile, name: String, content: String): StorageFile {
    val file = root.createFile("sources/$name", "text/plain")!!
    file.openOutputStream()?.use { it.write(content.toByteArray()) }
    return file
  }

  private fun describe(result: TransferResult<StorageFile>): String =
    when (result) {
      is TransferResult.Success ->
        "Success: ${result.result.name} (${result.stats.filesTransferred} files)"
      is TransferResult.Skipped -> "Skipped — ${result.existingTarget?.name} already exists"
      is TransferResult.Failure -> "Failed: ${result.errorCode} ${result.message.orEmpty()}"
    }
}
