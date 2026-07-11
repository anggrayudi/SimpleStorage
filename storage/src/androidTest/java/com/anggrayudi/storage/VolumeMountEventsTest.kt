package com.anggrayudi.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 9 - VolumeBookmark, experimental: TC-83 (V3_TEST_CASES.md).
 *
 * Verifies [com.anggrayudi.storage.access.StorageAccessManager.volumeMountEvents] emits the
 * [StorageVolume] when a removable volume is remounted. The unmount/mount cycle is driven
 * on-device through `UiAutomation.executeShellCommand` (shell uid may call `sm`).
 *
 * KEEP THIS CLASS OUT OF THE MAIN SUITE RUN: the unmount cycle can kill processes holding open
 * files on the volume, so it must run in its own instrumentation invocation AFTER all other
 * groups (see the Group 9 notes in V3_TEST_CASES.md).
 */
@OptIn(ExperimentalSimpleStorageApi::class)
@RunWith(AndroidJUnit4::class)
class VolumeMountEventsTest {

  private val context: Context = targetContext()
  private lateinit var scenario: ActivityScenario<BookmarkTestActivity>

  @Before
  fun setUp() {
    scenario = ActivityScenario.launch(BookmarkTestActivity::class.java)
  }

  @After
  fun tearDown() {
    scenario.close()
  }

  private fun shell(command: String): String {
    val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() }
  }

  // TC-83: volumeMountEvents emits on remount.
  @Test
  fun tc83_volumeMountEventsEmitsOnRemount(): Unit = runBlocking {
    assumeTrue("volumeMountEvents requires API 30+", Build.VERSION.SDK_INT >= 30)
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val removable =
      storageManager.storageVolumes.firstOrNull {
        !it.isPrimary && it.uuid != null && it.state == Environment.MEDIA_MOUNTED
      }
    assumeTrue("No mounted removable volume - skipping TC-83", removable != null)
    val expectedUuid = removable!!.uuid!!

    // `sm list-volumes public` lines look like: "public:7,433 mounted 4145-0BEA".
    val volumeName =
      shell("sm list-volumes public")
        .lines()
        .firstOrNull { it.contains(expectedUuid) }
        ?.trim()
        ?.substringBefore(' ')
    assertNotNull("could not find volume name for uuid $expectedUuid", volumeName)

    lateinit var manager: com.anggrayudi.storage.access.StorageAccessManager
    scenario.onActivity { manager = it.storageAccess }

    val emitted = CompletableDeferred<StorageVolume>()
    val collector =
      MainScope().launch(Dispatchers.Main) {
        manager.volumeMountEvents().collect { volume ->
          if (expectedUuid.equals(volume.uuid, ignoreCase = true) && !emitted.isCompleted) {
            emitted.complete(volume)
          }
        }
      }
    try {
      delay(1_000) // let the StorageVolumeCallback register before we churn the volume
      println("TC-83: unmounting $volumeName -> ${shell("sm unmount $volumeName")}")
      delay(3_000)
      println("TC-83: mounting $volumeName -> ${shell("sm mount $volumeName")}")

      val volume = withTimeout(TimeUnit.SECONDS.toMillis(60)) { emitted.await() }
      assertEquals(expectedUuid, volume.uuid)
      println(
        "TC-83: received MEDIA_MOUNTED for uuid=${volume.uuid} " +
          "description=${volume.getDescription(context)} state=${volume.state}"
      )
    } finally {
      collector.cancel()
      // Best effort: make sure the volume is mounted again for whoever runs next.
      shell("sm mount $volumeName")
    }
  }
}
