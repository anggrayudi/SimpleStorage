package com.anggrayudi.storage

import android.content.Context
import android.os.storage.StorageManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.access.BookmarkResult
import com.anggrayudi.storage.access.StorageAccessManager
import com.anggrayudi.storage.access.VolumeBookmark
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 9 - VolumeBookmark, experimental (V3_TEST_CASES.md TC-80..TC-82).
 *
 * TC-80/81 require a removable volume with an app-specific dir (`getExternalFilesDirs` index > 0
 * outside `/storage/emulated`); they SKIP via Assume on SD-less devices. TC-82 runs anywhere.
 *
 * [StorageAccessManager] must be constructed before its host activity is started, so every test
 * launches [BookmarkTestActivity] (which builds the manager in `onCreate`) via [ActivityScenario].
 */
@OptIn(ExperimentalSimpleStorageApi::class)
@RunWith(AndroidJUnit4::class)
class VolumeBookmarkTest {

  private val context: Context = targetContext()
  private lateinit var scenario: ActivityScenario<BookmarkTestActivity>
  private var playground: File? = null

  private val sdCardDir: File? by lazy {
    context.getExternalFilesDirs(null).filterNotNull().withIndex().firstOrNull { (index, dir) ->
      index > 0 && !dir.path.startsWith("/storage/emulated")
    }?.value
  }

  @Before
  fun setUp() {
    scenario = ActivityScenario.launch(BookmarkTestActivity::class.java)
  }

  @After
  fun tearDown() {
    playground?.deleteRecursively()
    scenario.close()
  }

  private fun manager(): StorageAccessManager {
    lateinit var manager: StorageAccessManager
    scenario.onActivity { manager = it.storageAccess }
    return manager
  }

  private fun newSdPlayground(): File {
    val dir = File(sdCardDir!!, "tc80_82_${UUID.randomUUID()}").apply { check(mkdirs()) }
    playground = dir
    return dir
  }

  // TC-80: createBookmark on a folder inside the removable volume's app-specific dir.
  @Test
  fun tc80_createBookmarkOnRemovableVolume() {
    assumeTrue("No removable volume mounted - skipping TC-80", sdCardDir != null)
    val dir = newSdPlayground()
    val expectedStorageId = dir.path.substringAfter("/storage/").substringBefore('/')
    val expectedBasePath = dir.path.substringAfter("/storage/$expectedStorageId/")

    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val volume =
      storageManager.storageVolumes.firstOrNull {
        expectedStorageId.equals(it.uuid, ignoreCase = true)
      }
    assertNotNull("StorageManager must know volume $expectedStorageId", volume)
    val expectedLabel = volume!!.getDescription(context).orEmpty()
    println("TC-80: volume uuid=$expectedStorageId description(label)=$expectedLabel")

    val bookmark = manager().createBookmark(StorageFile.from(context, dir))

    assertNotNull("createBookmark should succeed for $dir", bookmark)
    assertEquals(expectedStorageId, bookmark!!.storageId)
    assertEquals(expectedBasePath, bookmark.basePath)
    assertEquals(expectedLabel, bookmark.volumeLabel)
  }

  // TC-81: resolveBookmark happy path - volume mounted, same UUID, and NO SAF UI.
  @Test
  fun tc81_resolveBookmarkHappyPathNoUi() {
    assumeTrue("No removable volume mounted - skipping TC-81", sdCardDir != null)
    val dir = newSdPlayground()
    val manager = manager()
    val bookmark = manager.createBookmark(StorageFile.from(context, dir))
    assertNotNull(bookmark)

    var result: BookmarkResult? = null
    val latch = CountDownLatch(1)
    val job =
      MainScope().launch(Dispatchers.Main) {
        result = manager.resolveBookmark(bookmark!!)
        latch.countDown()
      }
    // The happy path must not wait on any launcher, so it resolves almost instantly. A short
    // timeout doubles as the "no SAF UI appeared" guard: had a launcher fired, resolveBookmark
    // would still be suspended waiting for the (never-answered) activity result.
    assertTrue(
      "resolveBookmark did not finish within 10s - a launcher probably fired (SAF UI visible?)",
      latch.await(10, TimeUnit.SECONDS),
    )
    job.cancel()

    val granted = result as? BookmarkResult.Granted
    assertNotNull("expected Granted but was $result", granted)
    assertEquals("bookmark must come back unchanged on the happy path", bookmark, granted!!.bookmark)
    assertEquals(dir.absolutePath, granted.folder.absolutePath)
    assertTrue("granted folder must be readable", granted.folder.canRead)

    // Second no-UI signal: the host activity never lost the foreground. Any SAF dialog would
    // have paused it.
    assertEquals(Lifecycle.State.RESUMED, scenario.state)
    var hasFocus = false
    scenario.onActivity { hasFocus = it.hasWindowFocus() }
    assertTrue("activity lost window focus - some UI appeared on top of it", hasFocus)
  }

  // TC-82: resolveBookmark with the volume absent.
  @Test
  fun tc82_resolveBookmarkVolumeAbsent() {
    val manager = manager()
    val fabricated =
      VolumeBookmark(volumeLabel = "NoSuchDrive", storageId = "A0E69251E6922814", basePath = "Movies")

    var result: BookmarkResult? = null
    val latch = CountDownLatch(1)
    val job =
      MainScope().launch(Dispatchers.Main) {
        result = manager.resolveBookmark(fabricated)
        latch.countDown()
      }
    assertTrue(
      "resolveBookmark did not finish within 10s - it must fail fast without UI",
      latch.await(10, TimeUnit.SECONDS),
    )
    job.cancel()

    assertEquals(BookmarkResult.VolumeNotMounted, result)
  }
}
