package com.anggrayudi.storage

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.callback.MultipleFilesConflictCallback
import com.anggrayudi.storage.callback.SingleFileConflictCallback
import com.anggrayudi.storage.callback.SingleFolderConflictCallback
import com.anggrayudi.storage.file.copyTo
import com.anggrayudi.storage.file.moveTo
import com.anggrayudi.storage.result.MultipleFilesResult
import com.anggrayudi.storage.media.FileDescription
import com.anggrayudi.storage.media.MediaStoreCompat
import com.anggrayudi.storage.transfer.Conflict
import com.anggrayudi.storage.transfer.ConflictResolution
import com.anggrayudi.storage.transfer.TransferErrorCode
import com.anggrayudi.storage.transfer.TransferResult
import com.anggrayudi.storage.transfer.isSuccess
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 3b - the 2.x multi-file engine (`List<DocumentFile>.copyTo/moveTo`), which v3 does not wrap
 * yet but which ships as public API. It carries the same finalize()/conflictedFiles pattern that
 * silently swallowed the terminal event in `copyFolderTo` (fixed in 449d90e).
 */
@RunWith(AndroidJUnit4::class)
class MultipleFilesEngineTest {

  private val context = targetContext()
  private lateinit var playground: File

  @Before
  fun setUp() {
    playground = newPlaygroundDir("tc28")
  }

  @After
  fun tearDown() {
    playground.deleteRecursivelyOrThrow()
  }

  private fun mergeAndReplaceCallback() =
    object : MultipleFilesConflictCallback(CoroutineScope(Dispatchers.Main)) {
      override fun onParentConflict(
        destinationParentFolder: DocumentFile,
        conflictedFolders: MutableList<ParentConflict>,
        conflictedFiles: MutableList<ParentConflict>,
        action: ParentFolderConflictAction,
      ) {
        conflictedFolders.forEach {
          it.solution = SingleFolderConflictCallback.ConflictResolution.MERGE
        }
        action.confirmResolution(conflictedFolders + conflictedFiles)
      }

      override fun onContentConflict(
        destinationParentFolder: DocumentFile,
        conflictedFiles: MutableList<SingleFolderConflictCallback.FileConflict>,
        action: SingleFolderConflictCallback.FolderContentConflictAction,
      ) {
        conflictedFiles.forEach {
          it.solution = SingleFileConflictCallback.ConflictResolution.REPLACE
        }
        action.confirmResolution(conflictedFiles)
      }
    }

  // TC-28: a resolved per-file conflict must still end the flow with Completed
  @Test
  fun tc28_multiFileMergeReportsCompletion() = runBlocking {
    val srcParent = File(playground, "src").apply { mkdirs() }
    val srcFolder = File(srcParent, "docs").apply { mkdirs() }
    File(srcFolder, "common.txt").writeText("from source")
    File(srcFolder, "onlyInSource.txt").writeText("new file")

    val targetParent = File(playground, "target").apply { mkdirs() }
    File(targetParent, "docs").mkdirs()
    File(targetParent, "docs/common.txt").writeText("stale target content")

    val events =
      withTimeout(60_000) {
        listOf(DocumentFile.fromFile(srcFolder))
          .copyTo(
            context,
            DocumentFile.fromFile(targetParent),
            onConflict = mergeAndReplaceCallback(),
          )
          .toList()
      }

    // On-disk truth first: the merge itself works, so any missing terminal event is a false
    // negative rather than a real failure.
    assertEquals("from source", File(targetParent, "docs/common.txt").readText())
    assertTrue(
      "onlyInSource.txt missing from target",
      File(targetParent, "docs/onlyInSource.txt").exists(),
    )

    val completed = events.filterIsInstance<MultipleFilesResult.Completed>()
    println("TC-28: events=${events.map { it::class.simpleName }}")
    assertTrue(
      "every file was copied but the flow closed without Completed; events=$events",
      completed.isNotEmpty(),
    )
    assertTrue("Completed reported success=false: ${completed.last()}", completed.last().success)
    assertEquals(2, completed.last().totalCopiedFiles)
  }

  // TC-30: moveTo shares the engine, and the swallowed terminal event also skipped the
  // source-deletion step that makes a move a move
  @Test
  fun tc30_multiFileMoveWithConflictDeletesSource() = runBlocking {
    val srcParent = File(playground, "src").apply { mkdirs() }
    val srcFolder = File(srcParent, "docs").apply { mkdirs() }
    File(srcFolder, "common.txt").writeText("from source")
    File(srcFolder, "onlyInSource.txt").writeText("new file")

    val targetParent = File(playground, "target").apply { mkdirs() }
    File(targetParent, "docs").mkdirs()
    File(targetParent, "docs/common.txt").writeText("stale target content")

    val events =
      withTimeout(60_000) {
        listOf(DocumentFile.fromFile(srcFolder))
          .moveTo(
            context,
            DocumentFile.fromFile(targetParent),
            onConflict = mergeAndReplaceCallback(),
          )
          .toList()
      }

    assertEquals("from source", File(targetParent, "docs/common.txt").readText())
    val completed = events.filterIsInstance<MultipleFilesResult.Completed>()
    assertTrue("no Completed event; events=$events", completed.isNotEmpty())
    assertFalse("source folder should be gone after a move", srcFolder.exists())
  }

  // TC-29: the same transfer without any conflict, as the control case
  @Test
  fun tc29_multiFileWithoutConflictReportsCompletion() = runBlocking {
    val srcParent = File(playground, "src").apply { mkdirs() }
    val srcFolder = File(srcParent, "docs").apply { mkdirs() }
    File(srcFolder, "a.txt").writeText("a")
    File(srcFolder, "b.txt").writeText("b")

    val targetParent = File(playground, "target").apply { mkdirs() }

    val events =
      withTimeout(60_000) {
        listOf(DocumentFile.fromFile(srcFolder))
          .copyTo(
            context,
            DocumentFile.fromFile(targetParent),
            onConflict = mergeAndReplaceCallback(),
          )
          .toList()
      }

    val completed = events.filterIsInstance<MultipleFilesResult.Completed>()
    assertTrue("no Completed event; events=$events", completed.isNotEmpty())
    assertTrue("Completed reported success=false: ${completed.last()}", completed.last().success)
    assertEquals(2, completed.last().totalCopiedFiles)
  }

  // TC-31: the v3 multi-source API reports every copied file
  @Test
  fun tc31_v3MultiSourceCopy() = runBlocking {
    val src = File(playground, "src").apply { mkdirs() }
    val folder = File(src, "docs").apply { mkdirs() }
    File(folder, "a.txt").writeText("a")
    val loneFile = File(src, "note.txt").apply { writeText("note") }
    val target = File(playground, "target").apply { mkdirs() }

    val result =
      listOf(StorageFile.from(context, folder), StorageFile.from(context, loneFile))
        .copyTo(StorageFile.from(context, target))

    assertTrue("expected success but was $result", result.isSuccess)
    val copied = (result as TransferResult.Success<List<StorageFile>>).result
    assertEquals(setOf("docs", "note.txt"), copied.map { it.name }.toSet())
    assertEquals(2, result.stats.filesTransferred)
    assertEquals("a", File(target, "docs/a.txt").readText())
    assertEquals("note", File(target, "note.txt").readText())
  }

  // TC-32: a resolved conflict still ends with a terminal result, through v3 this time
  @Test
  fun tc32_v3MultiSourceMergeWithConflict() = runBlocking {
    val src = File(playground, "src").apply { mkdirs() }
    val folder = File(src, "docs").apply { mkdirs() }
    File(folder, "common.txt").writeText("from source")
    File(folder, "onlyInSource.txt").writeText("new file")
    val target = File(playground, "target").apply { mkdirs() }
    File(target, "docs").mkdirs()
    File(target, "docs/common.txt").writeText("stale target content")

    val result =
      listOf(StorageFile.from(context, folder)).copyTo(StorageFile.from(context, target)) {
        onConflict { conflict ->
          if (conflict is Conflict.TargetFolder) ConflictResolution.MERGE
          else ConflictResolution.REPLACE
        }
      }

    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals("from source", File(target, "docs/common.txt").readText())
    assertTrue(File(target, "docs/onlyInSource.txt").exists())
  }

  // TC-33: moveTo through v3 removes the sources
  @Test
  fun tc33_v3MultiSourceMove() = runBlocking {
    val src = File(playground, "src").apply { mkdirs() }
    val one = File(src, "one.txt").apply { writeText("1") }
    val two = File(src, "two.txt").apply { writeText("2") }
    val target = File(playground, "target").apply { mkdirs() }

    val result =
      listOf(StorageFile.from(context, one), StorageFile.from(context, two))
        .moveTo(StorageFile.from(context, target))

    assertTrue("expected success but was $result", result.isSuccess)
    assertTrue(File(target, "one.txt").exists())
    assertTrue(File(target, "two.txt").exists())
    assertFalse("sources should be gone after a move", one.exists() || two.exists())
  }

  // TC-34: a target that is not a folder fails instead of hanging
  @Test
  fun tc34_v3MultiSourceInvalidTarget() = runBlocking {
    val src = File(playground, "src").apply { mkdirs() }
    val file = File(src, "a.txt").apply { writeText("a") }
    val notAFolder = File(playground, "not_a_folder.txt").apply { writeText("x") }

    val result =
      listOf(StorageFile.from(context, file)).copyTo(StorageFile.from(context, notAFolder))

    assertTrue("expected Failure but was $result", result is TransferResult.Failure)
    assertEquals(TransferErrorCode.INVALID_TARGET, (result as TransferResult.Failure).errorCode)
  }

  // TC-35: copyToFile writes into an existing MediaStore entry
  @Test
  fun tc35_copyIntoMediaStoreEntry() = runBlocking {
    val src = File(playground, "src").apply { mkdirs() }
    val file = File(src, "report.txt").apply { writeText("v3 into media") }
    val entry =
      MediaStoreCompat.createDownload(
        context,
        FileDescription("tc35_${System.nanoTime()}.txt"),
      )!!
    try {
      val result =
        StorageFile.from(context, file).copyToFile(entry.toStorageFile(context))

      assertTrue("expected success but was $result", result.isSuccess)
      assertEquals("v3 into media", entry.openInputStream()?.use { String(it.readBytes()) })
      assertTrue("source should survive a copy", file.exists())
    } finally {
      entry.delete()
    }
  }

  // TC-36: copyToFile replaces the content of an existing document
  @Test
  fun tc36_copyIntoExistingDocument() = runBlocking {
    val src = File(playground, "src").apply { mkdirs() }
    val file = File(src, "new.txt").apply { writeText("fresh") }
    val targetFolder = File(playground, "target").apply { mkdirs() }
    val existing = File(targetFolder, "old.txt").apply { writeText("stale") }

    val result =
      StorageFile.from(context, file).copyToFile(StorageFile.from(context, existing))

    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals("fresh", existing.readText())
    assertEquals("the target must not be duplicated", 1, targetFolder.listFiles()!!.size)
  }

  // TC-37: moveToFile removes the source
  @Test
  fun tc37_moveIntoExistingDocument() = runBlocking {
    val src = File(playground, "src").apply { mkdirs() }
    val file = File(src, "new.txt").apply { writeText("moved") }
    val targetFolder = File(playground, "target").apply { mkdirs() }
    val existing = File(targetFolder, "old.txt").apply { writeText("stale") }

    val result =
      StorageFile.from(context, file).moveToFile(StorageFile.from(context, existing))

    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals("moved", existing.readText())
    assertFalse("source should be gone after a move", file.exists())
  }

  // TC-38: a folder target is rejected instead of silently copying into it
  @Test
  fun tc38_folderTargetIsRejected() = runBlocking {
    val src = File(playground, "src").apply { mkdirs() }
    val file = File(src, "a.txt").apply { writeText("a") }
    val folder = File(playground, "target").apply { mkdirs() }

    val result = StorageFile.from(context, file).copyToFile(StorageFile.from(context, folder))

    assertTrue("expected Failure but was $result", result is TransferResult.Failure)
    assertEquals(TransferErrorCode.INVALID_TARGET, (result as TransferResult.Failure).errorCode)
  }
}
