package com.anggrayudi.storage

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.storage.StorageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.anggrayudi.storage.access.BookmarkResult
import com.anggrayudi.storage.access.VolumeBookmark
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 9 - VolumeBookmark, experimental: TC-84 (V3_TEST_CASES.md).
 *
 * The label-fallback path: a bookmark whose storageId no longer exists but whose volumeLabel
 * matches a mounted volume must trigger one SAF re-grant and come back Granted with an UPDATED
 * storageId. The SAF dialog is driven with UiAutomator.
 *
 * RUN THIS CLASS LAST AND ALONE: it opens system UI (DocumentsUI) on the device and depends on
 * the removable volume from the earlier groups (see the Group 9 notes in V3_TEST_CASES.md).
 */
@OptIn(ExperimentalSimpleStorageApi::class)
@RunWith(AndroidJUnit4::class)
class VolumeLabelFallbackTest {

  private val context: Context = targetContext()
  private lateinit var scenario: ActivityScenario<BookmarkTestActivity>
  private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

  @Before
  fun setUp() {
    scenario = ActivityScenario.launch(BookmarkTestActivity::class.java)
  }

  @After
  fun tearDown() {
    // Release the SAF grant taken during the test so later runs start from a clean slate.
    context.contentResolver.persistedUriPermissions.forEach {
      runCatching {
        context.contentResolver.releasePersistableUriPermission(
          it.uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }
    }
    // If DocumentsUI is still on top for any reason, leave it.
    device.pressBack()
    scenario.close()
  }

  // TC-84: wrong storageId + right volumeLabel -> one SAF grant -> updated bookmark.
  @Test
  fun tc84_labelFallbackRegrantsAndUpdatesBookmark(): Unit = runBlocking {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val removable =
      storageManager.storageVolumes.firstOrNull {
        !it.isPrimary && it.uuid != null && it.state == Environment.MEDIA_MOUNTED
      }
    assumeTrue("No mounted removable volume - skipping TC-84", removable != null)
    val realUuid = removable!!.uuid!!
    val realLabel = removable.getDescription(context)
    assumeTrue("volume has no description/label - fallback not testable", !realLabel.isNullOrBlank())

    lateinit var manager: com.anggrayudi.storage.access.StorageAccessManager
    scenario.onActivity { manager = it.storageAccess }

    // "Documents" exists on the freshly formatted volume (vold creates the standard dirs), and it
    // is NOT raw-accessible to this app, so resolution must go through the SAF grant.
    val staleBookmark =
      VolumeBookmark(volumeLabel = realLabel!!, storageId = "DEADBEEFDEADBEEF", basePath = "Documents")

    val resultDeferred = CompletableDeferred<BookmarkResult>()
    val job =
      MainScope().launch(Dispatchers.Main) {
        resultDeferred.complete(manager.resolveBookmark(staleBookmark))
      }

    try {
      driveSafGrantDialog()
      val result = withTimeout(TimeUnit.SECONDS.toMillis(90)) { resultDeferred.await() }

      val granted = result as? BookmarkResult.Granted
      assertNotNull("expected Granted but was $result", granted)
      assertEquals(
        "bookmark must be updated to the real volume id",
        realUuid,
        granted!!.bookmark.storageId,
      )
      assertEquals(realLabel, granted.bookmark.volumeLabel)
      assertEquals("Documents", granted.bookmark.basePath)
      assertTrue("granted folder must be readable", granted.folder.canRead)
      println(
        "TC-84: label fallback re-granted. old id=DEADBEEFDEADBEEF new id=${granted.bookmark.storageId} " +
          "folder=${granted.folder.absolutePath ?: granted.folder.uri}"
      )
    } finally {
      job.cancel()
    }
  }

  /**
   * Waits for DocumentsUI and taps through: (optional) navigation, "Use this folder", "Allow".
   * Dumps the window hierarchy into the test log on failure so a BLOCKED verdict can say exactly
   * where it got stuck.
   */
  private fun driveSafGrantDialog() {
    val timeout = 15_000L
    // DocumentsUI package differs across builds; match the grant button by text instead.
    val useThisFolder =
      device.wait(
        Until.findObject(By.text(compilePattern("use this folder"))),
        timeout,
      )
    if (useThisFolder == null) {
      dumpUi("TC-84: 'Use this folder' button not found")
      error("SAF UI: could not find the 'Use this folder' button within ${timeout}ms")
    }
    useThisFolder.click()

    val allow = device.wait(Until.findObject(By.text(compilePattern("allow"))), timeout)
    if (allow == null) {
      dumpUi("TC-84: 'Allow' confirmation button not found")
      error("SAF UI: could not find the 'Allow' confirmation button within ${timeout}ms")
    }
    allow.click()
    device.waitForIdle(5_000)
  }

  private fun compilePattern(text: String): java.util.regex.Pattern =
    java.util.regex.Pattern.compile(text, java.util.regex.Pattern.CASE_INSENSITIVE)

  private fun dumpUi(reason: String) {
    runCatching {
      val out = ByteArrayOutputStream()
      device.dumpWindowHierarchy(out)
      println("$reason\n--- window hierarchy ---\n$out\n--- end hierarchy ---")
    }
  }
}
