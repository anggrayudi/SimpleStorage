package com.anggrayudi.storage.file

import android.content.Context
import android.os.Environment
import com.anggrayudi.storage.SimpleStorage.Companion.LIBRARY_PACKAGE_NAME
import com.anggrayudi.storage.file.StorageId.PRIMARY
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Non-FAT storage IDs — USB OTG drives with NTFS (16-hex serials) and ChromeOS external media
 * (40-hex IDs) — must parse and classify like SD cards. Sample IDs come from real hardware
 * reported in PR #131.
 *
 * Created on 7/12/26
 *
 * @author Anggrayudi H
 */
class StorageIdParsingTest {

  private val context =
    mockk<Context> {
      val dataDirectoryPath = "/data/user/0/${LIBRARY_PACKAGE_NAME}"
      every { filesDir } returns File("$dataDirectoryPath/files")
      every { dataDir } returns File(dataDirectoryPath)
    }

  @Before
  fun setUp() {
    mockkStatic(Environment::class)
    every { Environment.getExternalStorageDirectory() } answers { File("/storage/emulated/0") }
  }

  companion object {
    private const val NTFS_ID = "A0E69251E6922814"
    private const val CHROMEOS_ID = "BB146539D141DC32010CB1AD374464444024627A"
    private const val FAT_ID = "AAAA-BBBB"
  }

  @Test
  fun `getStorageId extracts any volume segment under storage`() {
    assertEquals(NTFS_ID, DocumentFileCompat.getStorageId(context, "/storage/$NTFS_ID/Movies"))
    assertEquals(CHROMEOS_ID, DocumentFileCompat.getStorageId(context, "/storage/$CHROMEOS_ID/Docs/a.pdf"))
    assertEquals(FAT_ID, DocumentFileCompat.getStorageId(context, "/storage/$FAT_ID/Music"))
    assertEquals(PRIMARY, DocumentFileCompat.getStorageId(context, "/storage/emulated/0/Download"))
    assertEquals("data", DocumentFileCompat.getStorageId(context, "/data/user/0/$LIBRARY_PACKAGE_NAME/files"))
    // paths outside /storage/ still yield no ID
    assertEquals("", DocumentFileCompat.getStorageId(context, "/mnt/foo/bar"))
    // simple paths keep working
    assertEquals(NTFS_ID, DocumentFileCompat.getStorageId(context, "$NTFS_ID:Movies"))
  }

  @Test
  fun `getBasePath works for non-FAT volume paths`() {
    assertEquals("Movies", DocumentFileCompat.getBasePath(context, "/storage/$NTFS_ID/Movies"))
    assertEquals(
      "Docs/a.pdf",
      DocumentFileCompat.getBasePath(context, "/storage/$CHROMEOS_ID/Docs/a.pdf"),
    )
    assertEquals("", DocumentFileCompat.getBasePath(context, "/storage/$NTFS_ID"))
    assertEquals("Movies", DocumentFileCompat.getBasePath(context, "$NTFS_ID:Movies"))
  }

  @Test
  fun `buildAbsolutePath round-trips non-FAT IDs`() {
    assertEquals(
      "/storage/$NTFS_ID/Movies",
      DocumentFileCompat.buildAbsolutePath(context, NTFS_ID, "Movies"),
    )
  }

  @Test
  fun `FileFullPath parses non-FAT volume paths`() {
    val path = FileFullPath(context, "/storage/$NTFS_ID/Movies/Action")
    assertEquals(NTFS_ID, path.storageId)
    assertEquals("Movies/Action", path.basePath)
    assertEquals("/storage/$NTFS_ID/Movies/Action", path.absolutePath)
  }

  @Test
  fun `File getStorageId recognizes non-FAT volumes`() {
    assertEquals(NTFS_ID, File("/storage/$NTFS_ID/Movies").getStorageId(context))
    assertEquals(PRIMARY, File("/storage/emulated/0/Movies").getStorageId(context))
  }

  @Test
  fun `fromStorageId classifies known removable formats as SD_CARD`() {
    assertEquals(StorageType.SD_CARD, StorageType.fromStorageId(FAT_ID))
    assertEquals(StorageType.SD_CARD, StorageType.fromStorageId(NTFS_ID))
    assertEquals(StorageType.SD_CARD, StorageType.fromStorageId(CHROMEOS_ID))
    assertEquals(StorageType.EXTERNAL, StorageType.fromStorageId(PRIMARY))
    assertEquals(StorageType.DATA, StorageType.fromStorageId(StorageId.DATA))
  }

  @Test
  fun `fromStorageId keeps rejecting non-volume-looking strings`() {
    // uppercase words with non-hex letters must not classify as removable volumes
    assertEquals(StorageType.UNKNOWN, StorageType.fromStorageId("MOVIES"))
    assertEquals(StorageType.UNKNOWN, StorageType.fromStorageId("DOWNLOADS"))
    assertEquals(StorageType.UNKNOWN, StorageType.fromStorageId("home"))
    assertEquals(StorageType.UNKNOWN, StorageType.fromStorageId(""))
    // shorter than 8 hex chars is not a known volume-ID format
    assertEquals(StorageType.UNKNOWN, StorageType.fromStorageId("ABC123"))
  }

  @Test
  fun `path regex only matches under storage prefix`() {
    // the grouped alternation must not let bare hex strings match without the /storage/ prefix
    assertEquals(false, NTFS_ID.matches(DocumentFileCompat.SD_CARD_STORAGE_PATH_REGEX))
    assertEquals(true, "/storage/$NTFS_ID/Movies".matches(DocumentFileCompat.SD_CARD_STORAGE_PATH_REGEX))
    assertEquals(true, "/storage/$FAT_ID".matches(DocumentFileCompat.SD_CARD_STORAGE_PATH_REGEX))
  }
}
