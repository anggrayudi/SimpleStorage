package com.anggrayudi.storage

import android.content.Context
import com.anggrayudi.storage.transfer.TransferResult
import com.anggrayudi.storage.transfer.getOrNull
import com.anggrayudi.storage.transfer.isSuccess
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * End-to-end tests for the v3 one-shot transfer operations on the raw-file backend. Conflict
 * resolution paths require a live main looper and are covered by instrumentation, not here.
 *
 * Created on 7/11/26
 *
 * @author Anggrayudi H
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageFileTransferTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var context: Context
  private lateinit var sourceDir: File
  private lateinit var targetDir: File

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    sourceDir = tempFolder.newFolder("source")
    targetDir = tempFolder.newFolder("target")
    File(sourceDir, "a.txt").writeText("hello world")
    File(sourceDir, "sub").mkdirs()
    File(sourceDir, "sub/b.txt").writeText("nested content")
  }

  private fun storageFile(file: File): StorageFile = StorageFile.from(context, file)

  @Test
  fun `copyTo copies a single file into the target folder`() = runBlocking {
    val result =
      storageFile(File(sourceDir, "a.txt")).copyTo(storageFile(targetDir)) {
        checkAvailableSpace = false
      }
    assertTrue("expected success but was $result", result.isSuccess)
    val copied = File(targetDir, "a.txt")
    assertTrue(copied.exists())
    assertEquals("hello world", copied.readText())
    assertTrue(File(sourceDir, "a.txt").exists()) // source untouched
    assertEquals("a.txt", result.getOrNull()?.name)
  }

  @Test
  fun `moveTo moves a single file and removes the source`() = runBlocking {
    val result =
      storageFile(File(sourceDir, "a.txt")).moveTo(storageFile(targetDir)) {
        checkAvailableSpace = false
      }
    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals("hello world", File(targetDir, "a.txt").readText())
    assertFalse(File(sourceDir, "a.txt").exists())
  }

  @Test
  fun `copyTo copies a folder recursively`() = runBlocking {
    val result =
      storageFile(sourceDir).copyTo(storageFile(targetDir)) { checkAvailableSpace = false }
    assertTrue("expected success but was $result", result.isSuccess)
    assertEquals("hello world", File(targetDir, "source/a.txt").readText())
    assertEquals("nested content", File(targetDir, "source/sub/b.txt").readText())
  }

  @Test
  fun `copyTo into an invalid target fails with INVALID_TARGET`() = runBlocking {
    val notAFolder = storageFile(File(sourceDir, "a.txt"))
    val result = storageFile(File(sourceDir, "sub/b.txt")).copyTo(notAFolder)
    assertTrue(result is TransferResult.Failure)
    assertEquals(
      com.anggrayudi.storage.transfer.TransferErrorCode.INVALID_TARGET,
      (result as TransferResult.Failure).errorCode,
    )
  }

  @Test
  fun `zipTo then unzipTo round-trips contents`() = runBlocking {
    // ZIP entry paths are derived from storage-volume base paths, so the tree must live under
    // the (Robolectric-faked) external storage directory rather than a plain JVM temp dir.
    val external = android.os.Environment.getExternalStorageDirectory()
    val zipSource = File(external, "zipsource").apply { mkdirs() }
    File(zipSource, "a.txt").writeText("hello world")
    File(zipSource, "sub").mkdirs()
    File(zipSource, "sub/b.txt").writeText("nested content")
    val zipRaw = File(external, "archive.zip").apply { createNewFile() }

    val zipResult =
      listOf(storageFile(zipSource)).zipTo(storageFile(zipRaw)) { checkAvailableSpace = false }
    assertTrue("zip failed: $zipResult", zipResult.isSuccess)
    assertTrue(zipRaw.length() > 0)

    val unzipDir = File(external, "unzipped").apply { mkdirs() }
    val unzipResult =
      storageFile(zipRaw).unzipTo(storageFile(unzipDir)) { checkAvailableSpace = false }
    assertTrue("unzip failed: $unzipResult", unzipResult.isSuccess)
    val extracted = unzipDir.walkTopDown().filter { it.isFile }.map { it.name }.toSet()
    assertEquals(setOf("a.txt", "b.txt"), extracted)
  }

  @Test
  fun `deleteRecursively removes folder tree`() = runBlocking {
    assertTrue(storageFile(sourceDir).deleteRecursively())
    assertFalse(sourceDir.exists())
  }

  @Test
  fun `search over StorageFile finds nested files`() = runBlocking {
    val results = mutableListOf<String>()
    storageFile(sourceDir).search(recursive = true, regex = Regex("^.*\\.txt$")).collect { files ->
      results.clear()
      results.addAll(files.map { it.name })
    }
    assertEquals(setOf("a.txt", "b.txt"), results.toSet())
  }
}
