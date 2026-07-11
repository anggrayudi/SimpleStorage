package com.anggrayudi.storage.access

import com.anggrayudi.storage.ExperimentalSimpleStorageApi
import com.anggrayudi.storage.StoragePath
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created on 7/12/26
 *
 * @author Anggrayudi H
 */
@OptIn(ExperimentalSimpleStorageApi::class)
class VolumeBookmarkTest {

  @Test
  fun `bookmark maps to StoragePath`() {
    val bookmark = VolumeBookmark("My USB Drive", "A0E69251E6922814", "Backup/photos")
    assertEquals(StoragePath("A0E69251E6922814", "Backup/photos"), bookmark.toStoragePath())
  }

  @Test
  fun `re-granted bookmark keeps label and basePath with the new volume id`() {
    val old = VolumeBookmark("My USB Drive", "OLD1234OLD1234AB", "Backup")
    val updated = old.copy(storageId = "NEW1234NEW1234CD")
    assertEquals("My USB Drive", updated.volumeLabel)
    assertEquals("Backup", updated.basePath)
    assertEquals("NEW1234NEW1234CD:Backup", updated.toStoragePath().toString())
  }
}
