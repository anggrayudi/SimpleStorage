package com.anggrayudi.storage

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Created on 7/11/26
 *
 * @author Anggrayudi H
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageFileTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var context: Context
  private lateinit var root: File

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    root = tempFolder.newFolder("root")
    File(root, "a.txt").writeText("hello")
    File(root, "sub").mkdirs()
    File(root, "sub/b.txt").writeText("world")
  }

  @Test
  fun `raw folder exposes children and metadata`() {
    val folder = StorageFile.from(context, root)
    assertTrue(folder.isDirectory)
    assertFalse(folder.isFile)
    assertTrue(folder.exists)
    assertEquals("root", folder.name)
    assertEquals(setOf("a.txt", "sub"), folder.list().map { it.name }.toSet())
  }

  @Test
  fun `child resolves nested paths`() {
    val folder = StorageFile.from(context, root)
    val nested = folder.child("sub/b.txt")
    assertNotNull(nested)
    assertEquals("b.txt", nested!!.name)
    assertTrue(nested.isFile)
    assertEquals(5L, nested.length)
    assertNull(folder.child("missing/file.txt"))
  }

  @Test
  fun `streams round-trip content`() {
    val file = StorageFile.from(context, File(root, "a.txt"))
    val content = file.openInputStream()!!.use { it.readBytes().decodeToString() }
    assertEquals("hello", content)

    file.openOutputStream(append = true)!!.use { it.write(" again".toByteArray()) }
    val appended = file.openInputStream()!!.use { it.readBytes().decodeToString() }
    assertEquals("hello again", appended)
  }

  @Test
  fun `delete removes the file`() {
    val file = StorageFile.from(context, File(root, "a.txt"))
    assertTrue(file.delete())
    assertFalse(file.exists)
  }

  @Test
  fun `equality follows the underlying uri`() {
    val f1 = StorageFile.from(context, File(root, "a.txt"))
    val f2 = StorageFile.from(context, File(root, "a.txt"))
    val other = StorageFile.from(context, File(root, "sub/b.txt"))
    assertEquals(f1, f2)
    assertFalse(f1 == other)
  }

  @Test
  fun `storage path renders storageId colon basePath`() {
    val path = StoragePath("AAAA-BBBB", "Download/movie.mp4")
    assertEquals("AAAA-BBBB:Download/movie.mp4", path.toString())
    assertEquals("Download", StoragePath.primary("Download").basePath)
  }
}
