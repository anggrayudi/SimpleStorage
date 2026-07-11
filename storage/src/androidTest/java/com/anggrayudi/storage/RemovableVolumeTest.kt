package com.anggrayudi.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageType
import com.anggrayudi.storage.transfer.getOrNull
import com.anggrayudi.storage.transfer.isSuccess
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 8 - Non-FAT storage IDs (slice 1) (V3_TEST_CASES.md TC-70..TC-72).
 *
 * Verifies the claim that AOSP names public (removable) volumes by filesystem UUID (vold: "use
 * filesystem UUID as visible PublicVolume name to be more deterministic"), and that
 * [DocumentFileCompat.getStorageId]/[DocumentFileCompat.isMountedVolumeId]/
 * [StorageType.fromStorageId] recognize a real removable volume end-to-end.
 *
 * TC-71 requires a device/emulator with a removable volume actually mounted (e.g. an emulator
 * launched with `-sdcard <img>`, or a device with a USB OTG drive plugged in). On an SD-less
 * device this test SKIPs via [assumeTrue] so the suite stays green.
 *
 * TC-72 needs no removable volume - it exercises the regex fallback for an ID that is
 * definitely not currently mounted, and runs on any device.
 */
@RunWith(AndroidJUnit4::class)
class RemovableVolumeTest {

  private val context = targetContext()
  private var createdFile: File? = null
  private var copyTargetDir: File? = null

  private val sdCardDir: File? by lazy {
    context.getExternalFilesDirs(null).filterNotNull().withIndex().firstOrNull { (index, dir) ->
      index > 0 && !dir.path.startsWith("/storage/emulated")
    }?.value
  }

  @After
  fun tearDown() {
    createdFile?.delete()
    copyTargetDir?.deleteRecursively()
  }

  // TC-71: end-to-end recognition of a real removable volume.
  @Test
  fun tc71_endToEndRemovableVolumeRecognition() =
    runBlocking {
      val dir = sdCardDir
      assumeTrue("No removable volume mounted on this device - skipping TC-71", dir != null)
      dir!!

      // Derive the volume ID the same way the library does, and cross-check it against the raw
      // path segment independently (not by calling the function under test twice).
      val id = DocumentFileCompat.getStorageId(context, dir.path)
      val expectedSegment = dir.path.substringAfter("/storage/").substringBefore('/')
      assertTrue("storage id should be non-empty for path ${dir.path}", id.isNotEmpty())
      assertEquals(expectedSegment, id)

      assertTrue(
        "isMountedVolumeId($id) should be true - it is the volume we are running on",
        DocumentFileCompat.isMountedVolumeId(context, id),
      )
      assertEquals(StorageType.SD_CARD, StorageType.fromStorageId(context, id))

      // Create a real file on the volume and resolve it back through StorageFile.fromPath.
      val content = "TC-71 removable volume round-trip $id"
      val testFile = File(dir, "tc71_${UUID.randomUUID()}.txt").apply { writeText(content) }
      createdFile = testFile

      val resolved = StorageFile.fromPath(context, testFile.absolutePath)
      assertNotNull("StorageFile.fromPath should resolve $testFile", resolved)
      resolved!!
      assertEquals(testFile.name, resolved.name)
      assertEquals(content.toByteArray().size.toLong(), resolved.length)
      assertEquals(id, resolved.path?.storageId)

      // copyTo another folder on the SAME volume.
      val targetDir = File(dir, "tc71_copy_target_${UUID.randomUUID()}").apply { mkdirs() }
      copyTargetDir = targetDir

      // Free-space reporting on the virtual removable volume proved sane in manual checks
      // (df reported ~510 MB available), so the default checkAvailableSpace=true stays on.
      val result = resolved.copyTo(StorageFile.from(context, targetDir))
      assertTrue("copyTo within the same removable volume should succeed, was $result", result.isSuccess)
      val copied = File(targetDir, testFile.name)
      assertTrue("copied file should exist at $copied", copied.exists())
      assertEquals(content, copied.readText())
      assertEquals(testFile.name, result.getOrNull()?.name)
      // On-device evidence for the log (V3_TEST_CASES.md TC-71):
      println(
        "TC-71: volumeId=$id dir=$dir source=${testFile.name}(${testFile.length()}b) " +
          "resolved.path=${resolved.path} copied=${copied.path}(${copied.length()}b) " +
          "targetListing=${targetDir.list()?.toList()}"
      )
    }

  // TC-72: isMountedVolumeId negative + regex fallback classification.
  @Test
  fun tc72_unmountedVolumeIdRegexFallback() {
    val unmountedNtfsId = "A0E69251E6922814"
    assertFalse(
      "no such volume should be mounted on this device",
      DocumentFileCompat.isMountedVolumeId(context, unmountedNtfsId),
    )
    // Not mounted, but the regex fallback still classifies it as a removable volume by ID shape.
    assertEquals(StorageType.SD_CARD, StorageType.fromStorageId(context, unmountedNtfsId))
  }
}
