package com.anggrayudi.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.transfer.TransferErrorCode
import com.anggrayudi.storage.transfer.TransferResult
import com.anggrayudi.storage.transfer.getOrNull
import com.anggrayudi.storage.transfer.isSuccess
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 2 - One-shot transfers, happy paths (V3_TEST_CASES.md TC-10..TC-15).
 */
@RunWith(AndroidJUnit4::class)
class TransferHappyPathTest {

  private val context = targetContext()
  private lateinit var playground: File

  @Before
  fun setUp() {
    playground = newPlaygroundDir("tc10_15")
  }

  @After
  fun tearDown() {
    playground.deleteRecursivelyOrThrow()
  }

  private fun storageFile(file: File) = StorageFile.from(context, file)

  // TC-10: copyTo file
  @Test
  fun tc10_copyToFile() = runBlocking {
    val source = File(playground, "source").apply { mkdirs() }
    val target = File(playground, "target").apply { mkdirs() }
    val src = File(source, "a.txt").apply { writeRandomBytes(4096) }
    val expectedMd5 = src.md5()

    val result = storageFile(src).copyTo(storageFile(target))

    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals("a.txt", result.getOrNull()?.name)
    val copied = File(target, "a.txt")
    assertTrue(copied.exists())
    assertEquals(expectedMd5, copied.md5())
    assertTrue("source should remain intact", src.exists())
    assertEquals(expectedMd5, src.md5())
  }

  // TC-11: moveTo file
  @Test
  fun tc11_moveToFile() = runBlocking {
    val source = File(playground, "source").apply { mkdirs() }
    val target = File(playground, "target").apply { mkdirs() }
    val src = File(source, "a.txt").apply { writeRandomBytes(4096) }
    val expectedMd5 = src.md5()

    val result = storageFile(src).moveTo(storageFile(target))

    assertTrue("expected success but was $result", result.isSuccess)
    val moved = File(target, "a.txt")
    assertTrue(moved.exists())
    assertEquals(expectedMd5, moved.md5())
    assertFalse("source should be gone", src.exists())
  }

  // TC-12: copyTo folder recursive
  @Test
  fun tc12_copyToFolderRecursive() = runBlocking {
    val root = File(playground, "root").apply { mkdirs() }
    File(root, "file1.txt").writeRandomBytes(100, seed = 1)
    val subA = File(root, "subA").apply { mkdirs() }
    File(subA, "file2.txt").writeRandomBytes(200, seed = 2)
    val subB = File(subA, "subB").apply { mkdirs() }
    File(subB, "file3.txt").writeRandomBytes(300, seed = 3)
    File(subB, "file4.txt").writeRandomBytes(400, seed = 4)
    File(subA, "emptyFolder").mkdirs()

    val target = File(playground, "target").apply { mkdirs() }
    val result = storageFile(root).copyTo(storageFile(target))

    assertTrue("expected success but was $result", result.isSuccess)
    val copiedRoot = File(target, "root")
    val files = copiedRoot.walkTopDown().filter { it.isFile }.toList()
    assertEquals(4, files.size)
    assertEquals(File(root, "file1.txt").md5(), File(copiedRoot, "file1.txt").md5())
    assertEquals(File(subA, "file2.txt").md5(), File(copiedRoot, "subA/file2.txt").md5())
    assertEquals(File(subB, "file3.txt").md5(), File(copiedRoot, "subA/subB/file3.txt").md5())
    assertEquals(File(subB, "file4.txt").md5(), File(copiedRoot, "subA/subB/file4.txt").md5())

    // Documented behavior: default spec has skipEmptyFiles = true, but that flag only governs
    // zero-length *files*, not empty *folders* - record what actually happens on disk.
    val emptyFolderCopied = File(copiedRoot, "subA/emptyFolder").exists()
    println("TC-12: empty folder present in copy target = $emptyFolderCopied")
  }

  // TC-13: zip -> unzip round-trip
  @Test
  fun tc13_zipUnzipRoundTrip() = runBlocking {
    val root = File(playground, "root").apply { mkdirs() }
    File(root, "file1.txt").writeRandomBytes(100, seed = 1)
    val subA = File(root, "subA").apply { mkdirs() }
    File(subA, "file2.txt").writeRandomBytes(200, seed = 2)
    val subB = File(subA, "subB").apply { mkdirs() }
    File(subB, "file3.txt").writeRandomBytes(300, seed = 3)
    File(subB, "file4.txt").writeRandomBytes(400, seed = 4)
    File(subA, "emptyFolder").mkdirs()

    val zipFile = File(playground, "archive.zip").apply { createNewFile() }
    val zipResult = listOf(storageFile(root)).zipTo(storageFile(zipFile))
    assertTrue("zip failed: $zipResult", zipResult.isSuccess)
    assertEquals(4, (zipResult as TransferResult.Success<*>).stats.filesTransferred)

    val unzipDir = File(playground, "unzipped").apply { mkdirs() }
    val unzipResult = storageFile(zipFile).unzipTo(storageFile(unzipDir))
    assertTrue("unzip failed: $unzipResult", unzipResult.isSuccess)

    val extracted = unzipDir.walkTopDown().filter { it.isFile }.associateBy { it.name }
    assertEquals(setOf("file1.txt", "file2.txt", "file3.txt", "file4.txt"), extracted.keys)
    assertEquals(File(root, "file1.txt").md5(), extracted.getValue("file1.txt").md5())
    assertEquals(File(subA, "file2.txt").md5(), extracted.getValue("file2.txt").md5())
    assertEquals(File(subB, "file3.txt").md5(), extracted.getValue("file3.txt").md5())
    assertEquals(File(subB, "file4.txt").md5(), extracted.getValue("file4.txt").md5())
  }

  // TC-14: Invalid target
  @Test
  fun tc14_invalidTarget() = runBlocking {
    val source = File(playground, "source").apply { mkdirs() }
    val src = File(source, "a.txt").apply { writeText("hello") }
    val notAFolder = File(playground, "not_a_folder.txt").apply { writeText("i am a file") }

    val result = storageFile(src).copyTo(storageFile(notAFolder))

    assertTrue("expected Failure but was $result", result is TransferResult.Failure)
    assertEquals(TransferErrorCode.INVALID_TARGET, (result as TransferResult.Failure).errorCode)
  }

  // TC-15: Progress events
  @Test
  fun tc15_progressEvents() = runBlocking {
    val source = File(playground, "source").apply { mkdirs() }
    val target = File(playground, "target").apply { mkdirs() }
    val bigFile = File(source, "big.bin").apply { writeRandomBytes(20 * 1024 * 1024) }

    val progressEvents = mutableListOf<com.anggrayudi.storage.transfer.TransferEvent.Progress>()
    val result =
      storageFile(bigFile).copyTo(storageFile(target)) {
        updateInterval = 100
        onProgress { progressEvents.add(it) }
      }

    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals(bigFile.md5(), File(target, "big.bin").md5())

    val validProgress = progressEvents.filter { it.percent > 0f && it.percent <= 100f && it.bytesPerSecond > 0 }
    println(
      "TC-15: captured ${progressEvents.size} progress events, " +
        "${validProgress.size} satisfy 0<percent<=100 && bytesPerSecond>0: $progressEvents"
    )
    assertTrue(
      "expected at least one Progress with 0<percent<=100 and bytesPerSecond>0, " +
        "got $progressEvents",
      validProgress.isNotEmpty(),
    )
  }
}
