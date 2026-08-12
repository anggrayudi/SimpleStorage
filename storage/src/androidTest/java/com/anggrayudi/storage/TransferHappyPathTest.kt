package com.anggrayudi.storage

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.transfer.TransferEvent
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
    val bigFile = File(source, "big.bin").apply { writeRandomBytes(20 * 1024 * 1024) }

    // Part 1 is deterministic: a copy that finishes long before the first interval elapses must
    // report nothing. The engine used to fire an immediate Progress(0, 0, 0) before the first
    // byte, which showed up here as exactly one event no matter how long the interval was.
    val slowTarget = File(playground, "target-slow").apply { mkdirs() }
    val untimedEvents = mutableListOf<TransferEvent.Progress>()
    val untimedResult =
      storageFile(bigFile).copyTo(storageFile(slowTarget)) {
        updateInterval = 60_000
        onProgress { untimedEvents.add(it) }
      }

    assertTrue("expected success but was $untimedResult", untimedResult.isSuccess)
    assertEquals(bigFile.md5(), File(slowTarget, "big.bin").md5())
    assertTrue(
      "a copy shorter than the 60s interval must not report progress, got $untimedEvents",
      untimedEvents.isEmpty(),
    )

    // Part 2 watches the real stream. How many events arrive depends on the disk, so only their
    // shape is asserted unconditionally; their existence is asserted when the copy actually ran
    // long enough for a tick to be due.
    val target = File(playground, "target").apply { mkdirs() }
    val interval = 10L
    val events = mutableListOf<TransferEvent.Progress>()
    val startedAt = SystemClock.elapsedRealtime()
    val result =
      storageFile(bigFile).copyTo(storageFile(target)) {
        updateInterval = interval
        onProgress { events.add(it) }
      }
    val elapsed = SystemClock.elapsedRealtime() - startedAt

    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals(bigFile.md5(), File(target, "big.bin").md5())
    println(
      "TC-15: 20 MB copy took ${elapsed}ms at updateInterval=${interval}ms, " +
        "${events.size} progress events: $events"
    )

    events.forEachIndexed { i, event ->
      assertTrue(
        "event $i: percent=${event.percent} outside 0..100 in $events",
        event.percent in 0f..100f,
      )
      assertTrue("event $i: negative bytesTransferred in $events", event.bytesTransferred >= 0)
      assertTrue("event $i: negative bytesPerSecond in $events", event.bytesPerSecond >= 0)
    }
    val transferred = events.map { it.bytesTransferred }
    assertEquals("bytesTransferred went backwards in $events", transferred.sorted(), transferred)

    if (elapsed > 2 * interval) {
      assertTrue(
        "copy ran ${elapsed}ms at a ${interval}ms interval but reported no measured progress, " +
          "got $events",
        events.any { it.percent > 0f && it.bytesPerSecond > 0 },
      )
    } else {
      println(
        "TC-15: copy finished in ${elapsed}ms, too fast for a tick to be due - " +
          "skipped the existence assertion"
      )
    }
  }
}
