package com.anggrayudi.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
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
}

private fun assertArrayEquals(expected: ByteArray, actual: ByteArray?) {
  org.junit.Assert.assertArrayEquals(expected, actual)
}
