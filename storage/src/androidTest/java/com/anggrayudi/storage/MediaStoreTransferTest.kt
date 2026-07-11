package com.anggrayudi.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.media.MediaFile
import com.anggrayudi.storage.transfer.getOrNull
import com.anggrayudi.storage.transfer.isSuccess
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 4 - MediaStore transfers (V3_TEST_CASES.md TC-30, TC-31).
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreTransferTest {

  private val context = targetContext()
  private lateinit var playground: File
  private var media: MediaFile? = null

  @Before
  fun setUp() {
    playground = newPlaygroundDir("tc30_31")
  }

  @After
  fun tearDown() {
    media?.delete()
    playground.deleteRecursivelyOrThrow()
  }

  // TC-30: MediaStore -> folder copy
  @Test
  fun tc30_mediaStoreToFolderCopy() = runBlocking {
    val content = "media to folder ${System.nanoTime()}".toByteArray()
    val mediaFile = insertDownloadsMedia("tc30_${System.nanoTime()}.txt", content)
    media = mediaFile

    val mediaStorageFile = StorageFile.from(context, mediaFile.uri)
    assertTrue("MediaStore URI must resolve", mediaStorageFile != null)

    val targetFolder = File(playground, "target").apply { mkdirs() }
    val result = mediaStorageFile!!.copyTo(StorageFile.from(context, targetFolder))

    assertTrue("expected success but was $result", result.isSuccess)
    val landedName = result.getOrNull()?.name
    assertTrue("copied result should have a name", !landedName.isNullOrEmpty())
    val landed = File(targetFolder, landedName!!)
    assertTrue("copied file should exist on disk at $landed", landed.exists())
    assertArrayEquals(content, landed.readBytes())
  }

  // TC-31: deleteRecursively on media
  @Test
  fun tc31_deleteRecursivelyOnMedia() = runBlocking {
    val content = "to be deleted".toByteArray()
    val mediaFile = insertDownloadsMedia("tc31_${System.nanoTime()}.txt", content)
    media = mediaFile

    val mediaStorageFile = StorageFile.from(context, mediaFile.uri)
    assertTrue("MediaStore URI must resolve", mediaStorageFile != null)

    val deleted = mediaStorageFile!!.deleteRecursively()

    assertTrue("deleteRecursively() should return true", deleted)
    val stillPresent =
      context.contentResolver.query(mediaFile.uri, null, null, null, null)?.use { it.count > 0 }
        ?: false
    assertFalse("MediaStore row should be gone after delete", stillPresent)
    media = null // already gone, don't try to delete again in tearDown
  }
}
