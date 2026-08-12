package com.anggrayudi.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.file.CreateMode
import com.anggrayudi.storage.file.StorageId
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 1 - StorageFile factories & metadata (V3_TEST_CASES.md TC-01..TC-04). Runs on-device
 * against `context.getExternalFilesDir(null)`, no SAF grant required.
 */
@RunWith(AndroidJUnit4::class)
class StorageFileFactoryTest {

  private val context = targetContext()
  private lateinit var playground: File

  @Before
  fun setUp() {
    playground = newPlaygroundDir("tc01_04")
  }

  @After
  fun tearDown() {
    playground.deleteRecursivelyOrThrow()
  }

  // TC-01: Raw file metadata
  @Test
  fun tc01_rawFileMetadata() {
    val file = File(playground, "a.txt").apply { writeText("hello") }

    val storageFile = StorageFile.from(context, file)

    assertEquals("a.txt", storageFile.name)
    assertTrue("expected isFile", storageFile.isFile)
    assertTrue("expected exists", storageFile.exists)
    assertEquals(5L, storageFile.length)
    assertTrue(
      "mimeType should be text/plain or null but was ${storageFile.mimeType}",
      storageFile.mimeType == "text/plain" || storageFile.mimeType == null,
    )
    assertNotNull("absolutePath should be non-null", storageFile.absolutePath)
    assertEquals(file.absolutePath, storageFile.absolutePath)
    assertNotNull("path should be non-null", storageFile.path)
    assertEquals(StorageId.PRIMARY, storageFile.path?.storageId)
  }

  // TC-02: fromPath round-trip
  @Test
  fun tc02_fromPathRoundTrip() {
    val file = File(playground, "a.txt").apply { writeText("hello") }
    val original = StorageFile.from(context, file)

    val resolved = StorageFile.fromPath(context, file.absolutePath)
    assertNotNull("existing path should resolve", resolved)
    assertEquals(original.uri, resolved!!.uri)

    val nonexistent = File(playground, "does_not_exist.txt")
    val resolvedMissing = StorageFile.fromPath(context, nonexistent.absolutePath)
    assertNull("nonexistent path should resolve to null", resolvedMissing)
  }

  // TC-03: MediaStore backend
  @Test
  fun tc03_mediaStoreBackend() = runBlocking {
    val content = "media hello world".toByteArray()
    val mediaFile = insertDownloadsMedia("tc03_${System.nanoTime()}.txt", content)
    try {
      val storageFile = StorageFile.from(context, mediaFile.uri)
      assertNotNull("MediaStore URI should resolve to a StorageFile", storageFile)
      assertNotNull("asMediaFile() should be non-null for a MediaStore-backed file", storageFile!!.asMediaFile())
      assertTrue("expected isFile", storageFile.isFile)
      assertEquals(mediaFile.fullName, storageFile.name)
      assertEquals(content.size.toLong(), storageFile.length)
      val readBack = storageFile.openInputStream()?.use { it.readBytes() }
      assertNotNull("openInputStream() should return the written bytes", readBack)
      assertArrayEquals(content, readBack)
    } finally {
      mediaFile.delete()
    }
  }

  // TC-04: Children & child()
  @Test
  fun tc04_childrenAndChild() {
    val folder = File(playground, "folder").apply { mkdirs() }
    File(folder, "x.txt").writeText("x")
    File(folder, "y.txt").writeText("y")
    val sub = File(folder, "sub").apply { mkdirs() }
    File(sub, "z.txt").writeText("z")

    val storageFolder = StorageFile.from(context, folder)
    val children = storageFolder.list()
    assertEquals(3, children.size)

    val nested = storageFolder.child("sub/z.txt")
    assertNotNull("nested child should resolve", nested)
    assertEquals("z.txt", nested!!.name)
    assertTrue(nested.isFile)

    val missing = storageFolder.child("sub/does_not_exist.txt")
    assertNull("missing child should be null", missing)
  }

  // TC-08: length is right the instant a MediaStore file is written, and 0 stays 0
  @Test
  fun tc08_mediaLengthIsImmediate() {
    val content = "seventeen bytes!!".toByteArray()
    val written = insertDownloadsMedia("tc08_${System.nanoTime()}.txt", content)
    try {
      // Read with no delay: MediaProvider fills the _size column asynchronously, so this is the
      // window where the column still says 0 while the bytes are already on disk.
      assertEquals(content.size.toLong(), StorageFile.from(context, written.uri)?.length)
    } finally {
      written.delete()
    }

    val empty = insertDownloadsMedia("tc08_empty_${System.nanoTime()}.txt", ByteArray(0))
    try {
      assertEquals("an empty file must still report 0", 0L, StorageFile.from(context, empty.uri)?.length)
    } finally {
      empty.delete()
    }
  }

  // TC-05: createFile writes a real file, including nested names
  @Test
  fun tc05_createFile() {
    val folder = StorageFile.from(context, playground)

    val file = folder.createFile("report.txt", "text/plain")
    assertNotNull("createFile returned null", file)
    assertEquals("report.txt", file!!.name)
    assertTrue("created entry should be a file", file.isFile)
    file.openOutputStream()?.use { it.write("hello".toByteArray()) }
    assertEquals("hello", file.openInputStream()?.use { String(it.readBytes()) })
    assertTrue(File(playground, "report.txt").exists())

    val nested = folder.createFile("docs/2026/invoice.pdf", "application/pdf")
    assertNotNull("nested createFile returned null", nested)
    assertEquals("invoice.pdf", nested!!.name)
    assertTrue(File(playground, "docs/2026/invoice.pdf").exists())
  }

  // TC-06: CreateMode decides what happens to an existing name
  @Test
  fun tc06_createFileModes() {
    val folder = StorageFile.from(context, playground)
    folder.createFile("note.txt", "text/plain")!!.openOutputStream()?.use {
      it.write("first".toByteArray())
    }

    val createNew = folder.createFile("note.txt", "text/plain")
    assertEquals("CREATE_NEW must not overwrite", "note (1).txt", createNew?.name)
    assertEquals("first", File(playground, "note.txt").readText())

    val reused = folder.createFile("note.txt", "text/plain", CreateMode.REUSE)
    assertEquals("note.txt", reused?.name)
    assertEquals("REUSE must keep the content", "first", File(playground, "note.txt").readText())

    val replaced = folder.createFile("note.txt", "text/plain", CreateMode.REPLACE)
    assertEquals("note.txt", replaced?.name)
    assertEquals("REPLACE must empty the file", 0, File(playground, "note.txt").length())
  }

  // TC-07: createFolder, nested folders, and the non-folder receiver
  @Test
  fun tc07_createFolderAndInvalidReceiver() {
    val folder = StorageFile.from(context, playground)

    val created = folder.createFolder("invoices")
    assertNotNull("createFolder returned null", created)
    assertTrue("created entry should be a directory", created!!.isDirectory)
    assertTrue(File(playground, "invoices").isDirectory)

    val nested = folder.createFolder("invoices/2026/q3")
    assertNotNull(nested)
    assertTrue(File(playground, "invoices/2026/q3").isDirectory)

    val file = folder.createFile("plain.txt", "text/plain")!!
    assertNull("a file is not a folder to create in", file.createFile("nope.txt"))
    assertNull("a file is not a folder to create in", file.createFolder("nope"))
  }
}

private fun assertArrayEquals(expected: ByteArray, actual: ByteArray?) {
  org.junit.Assert.assertArrayEquals(expected, actual)
}
