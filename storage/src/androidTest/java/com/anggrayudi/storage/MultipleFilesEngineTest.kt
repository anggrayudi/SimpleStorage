package com.anggrayudi.storage

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.callback.MultipleFilesConflictCallback
import com.anggrayudi.storage.callback.SingleFileConflictCallback
import com.anggrayudi.storage.callback.SingleFolderConflictCallback
import com.anggrayudi.storage.file.copyTo
import com.anggrayudi.storage.file.moveTo
import com.anggrayudi.storage.result.MultipleFilesResult
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
}
