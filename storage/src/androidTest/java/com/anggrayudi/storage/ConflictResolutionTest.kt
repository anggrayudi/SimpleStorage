package com.anggrayudi.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.transfer.Conflict
import com.anggrayudi.storage.transfer.ConflictResolution
import com.anggrayudi.storage.transfer.TransferResult
import com.anggrayudi.storage.transfer.getOrNull
import com.anggrayudi.storage.transfer.isSuccess
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 3 - Conflict resolution (V3_TEST_CASES.md TC-20..TC-25). This is the critical gap this
 * device pass exists to close: the suspend `ConflictResolver` is bridged internally to v2's
 * callback classes which post continuations to `Dispatchers.Main`. Under Robolectric the main
 * looper never pumps during `runBlocking`, so this path deadlocks there - on the instrumentation
 * thread the looper is free, so this is the first time it has ever actually run.
 */
@RunWith(AndroidJUnit4::class)
class ConflictResolutionTest {

  private val context = targetContext()
  private lateinit var playground: File

  @Before
  fun setUp() {
    playground = newPlaygroundDir("tc20_25")
  }

  @After
  fun tearDown() {
    playground.deleteRecursivelyOrThrow()
  }

  private fun storageFile(file: File) = StorageFile.from(context, file)

  /** source folder with a new a.txt + target folder with an existing (old) a.txt. */
  private fun setUpSingleFileConflict(): Triple<File, File, File> {
    val source = File(playground, "source").apply { mkdirs() }
    val target = File(playground, "target").apply { mkdirs() }
    val newFile = File(source, "a.txt").apply { writeText("NEW content") }
    val oldFile = File(target, "a.txt").apply { writeText("OLD content") }
    return Triple(source, target, newFile)
  }

  // TC-20: REPLACE
  @Test
  fun tc20_replace() = runBlocking {
    val (_, target, newFile) = setUpSingleFileConflict()

    val result = storageFile(newFile).copyTo(storageFile(target)) { onConflict { ConflictResolution.REPLACE } }

    assertTrue("expected success but was $result", result.isSuccess)
    val filesNamedA = target.listFiles { f -> f.name.startsWith("a") }.orEmpty()
    assertEquals("expected exactly one a.txt in target", 1, filesNamedA.size)
    assertEquals("a.txt", filesNamedA[0].name)
    assertEquals("NEW content", filesNamedA[0].readText())
  }

  // TC-21: CREATE_NEW
  @Test
  fun tc21_createNew() = runBlocking {
    val (_, target, newFile) = setUpSingleFileConflict()

    val result = storageFile(newFile).copyTo(storageFile(target)) { onConflict { ConflictResolution.CREATE_NEW } }

    assertTrue("expected success but was $result", result.isSuccess)
    val original = File(target, "a.txt")
    val duplicate = File(target, "a (1).txt")
    assertTrue("original a.txt should still exist", original.exists())
    assertEquals("OLD content", original.readText())
    assertTrue("expected a (1).txt to be created, target has: ${target.list()?.toList()}", duplicate.exists())
    assertEquals("NEW content", duplicate.readText())
  }

  // TC-22: SKIP - documents the returned result shape, and that it is deterministic.
  @Test
  fun tc22_skip() = runBlocking {
    val (_, target, newFile) = setUpSingleFileConflict()

    val result = storageFile(newFile).copyTo(storageFile(target)) { onConflict { ConflictResolution.SKIP } }
    // Target must be untouched by the skipped transfer.
    assertEquals("OLD content", File(target, "a.txt").readText())
    assertTrue(
      "SKIP must not silently fabricate a second file",
      target.listFiles { f -> f.name.startsWith("a") }.orEmpty().size == 1,
    )

    // Since 3.0.0-beta02, SKIP is a first-class terminal result instead of the misleading
    // Failure(UNKNOWN_IO_ERROR) documented in beta01.
    assertTrue("expected Skipped but was $result", result is TransferResult.Skipped)
    assertEquals("a.txt", (result as TransferResult.Skipped).existingTarget?.name)
  }

  // TC-26: SKIP on the parent-folder conflict aborts the whole transfer as Skipped
  @Test
  fun tc26_folderParentSkip() = runBlocking {
    val sourceParent = File(playground, "skipSrcParent").apply { mkdirs() }
    val sourceShared = File(sourceParent, "shared").apply { mkdirs() }
    File(sourceShared, "common.txt").writeText("NEW common")

    val targetParent = File(playground, "skipDstParent").apply { mkdirs() }
    val targetShared = File(targetParent, "shared").apply { mkdirs() }
    File(targetShared, "common.txt").writeText("OLD common")

    val result =
      storageFile(sourceShared).copyTo(storageFile(targetParent)) {
        onConflict { ConflictResolution.SKIP }
      }

    assertTrue("expected Skipped but was $result", result is TransferResult.Skipped)
    assertEquals("shared", (result as TransferResult.Skipped).existingTarget?.name)
    assertEquals("OLD common", File(targetShared, "common.txt").readText())
  }

  // TC-27: per-file SKIP inside a merge keeps Success and reports stats.filesSkipped
  @Test
  fun tc27_mergeWithPerFileSkip() = runBlocking {
    val sourceParent = File(playground, "mergeSkipSrc").apply { mkdirs() }
    val sourceShared = File(sourceParent, "shared").apply { mkdirs() }
    File(sourceShared, "common.txt").writeText("NEW common")
    File(sourceShared, "onlyInSource.txt").writeText("only in source")

    val targetParent = File(playground, "mergeSkipDst").apply { mkdirs() }
    val targetShared = File(targetParent, "shared").apply { mkdirs() }
    File(targetShared, "common.txt").writeText("OLD common")

    val result =
      storageFile(sourceShared).copyTo(storageFile(targetParent)) {
        onConflict { conflict ->
          when (conflict) {
            is Conflict.TargetFolder -> ConflictResolution.MERGE
            is Conflict.TargetFile -> ConflictResolution.SKIP
          }
        }
      }

    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals("OLD common", File(targetShared, "common.txt").readText()) // skipped, untouched
    assertEquals("only in source", File(targetShared, "onlyInSource.txt").readText())
    assertEquals(1, (result as TransferResult.Success<*>).stats.filesSkipped)
  }

  // TC-23: Suspending resolver, no deadlock
  @Test
  fun tc23_suspendingResolverNoDeadlock() = runBlocking {
    val (_, target, newFile) = setUpSingleFileConflict()
    val started = System.currentTimeMillis()

    val result =
      withTimeout(30_000) {
        storageFile(newFile).copyTo(storageFile(target)) {
          onConflict {
            withContext(Dispatchers.Main) { delay(300) }
            ConflictResolution.REPLACE
          }
        }
      }

    val elapsed = System.currentTimeMillis() - started
    println("TC-23: suspending resolver completed in ${elapsed}ms")
    assertTrue("expected success but was $result", result.isSuccess)
    assertTrue("resolver delay of 300ms should have been honored", elapsed >= 300)
    assertTrue("should complete well before the 30s timeout, took ${elapsed}ms", elapsed < 10_000)
    assertEquals("NEW content", File(target, "a.txt").readText())
  }

  // TC-24: Folder merge
  @Test
  fun tc24_folderMerge() = runBlocking {
    val sourceParent = File(playground, "sourceParent").apply { mkdirs() }
    val sourceShared = File(sourceParent, "shared").apply { mkdirs() }
    File(sourceShared, "common.txt").writeText("NEW common")
    File(sourceShared, "onlyInSource.txt").writeText("only in source")

    val targetParent = File(playground, "targetParent").apply { mkdirs() }
    val targetShared = File(targetParent, "shared").apply { mkdirs() }
    File(targetShared, "common.txt").writeText("OLD common")
    File(targetShared, "onlyInTarget.txt").writeText("only in target")

    val conflictOrder = mutableListOf<Conflict>()
    val result =
      storageFile(sourceShared).copyTo(storageFile(targetParent)) {
        onConflict { conflict ->
          conflictOrder.add(conflict)
          when (conflict) {
            is Conflict.TargetFolder -> ConflictResolution.MERGE
            is Conflict.TargetFile -> ConflictResolution.REPLACE
          }
        }
      }

    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals("only in source", File(targetShared, "onlyInSource.txt").readText())
    assertEquals("only in target", File(targetShared, "onlyInTarget.txt").readText())
    assertEquals("NEW common", File(targetShared, "common.txt").readText())

    assertTrue("resolver should have been consulted at least twice", conflictOrder.size >= 2)
    assertTrue(
      "first conflict should be TargetFolder but order was $conflictOrder",
      conflictOrder.first() is Conflict.TargetFolder,
    )
    assertTrue(
      "a TargetFile conflict for common.txt should follow, order was $conflictOrder",
      conflictOrder.drop(1).any { it is Conflict.TargetFile && it.target.name == "common.txt" },
    )
  }

  // TC-25: Resolver receives correct conflict info
  @Test
  fun tc25_resolverReceivesCorrectConflictInfo() = runBlocking {
    val (_, target, newFile) = setUpSingleFileConflict()
    var capturedConflict: Conflict? = null

    val result =
      storageFile(newFile).copyTo(storageFile(target)) {
        onConflict { conflict ->
          capturedConflict = conflict
          ConflictResolution.REPLACE
        }
      }

    assertTrue("expected success but was $result", result.isSuccess)
    assertNotNull("resolver should have been invoked", capturedConflict)
    val conflict = capturedConflict!!
    assertTrue("expected Conflict.TargetFile but was $conflict", conflict is Conflict.TargetFile)
    assertEquals("a.txt", conflict.target.name)
    assertTrue("target.exists should be true", conflict.target.exists)
  }
}
