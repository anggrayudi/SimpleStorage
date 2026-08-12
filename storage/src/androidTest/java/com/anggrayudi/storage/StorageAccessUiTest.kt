package com.anggrayudi.storage

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.anggrayudi.storage.access.AccessResult
import com.anggrayudi.storage.access.StorageAccessManager
import com.anggrayudi.storage.contract.FileCreationResult
import com.anggrayudi.storage.contract.FilePickerResult
import com.anggrayudi.storage.contract.FolderPickerResult
import com.anggrayudi.storage.file.CreateMode
import com.anggrayudi.storage.media.FileDescription
import com.anggrayudi.storage.media.MediaStoreCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 10 - the interactive half of [StorageAccessManager] (V3_TEST_CASES.md TC-90..TC-95).
 *
 * Every picker here suspends until the system UI answers, so each test starts the call on Main,
 * drives DocumentsUI / the Photo Picker with UiAutomator from the instrumentation thread, and then
 * awaits the result. A missing system button fails with a window-hierarchy dump rather than a bare
 * timeout, so a failure says where it got stuck.
 */
@RunWith(AndroidJUnit4::class)
class StorageAccessUiTest {

  private val context: Context = targetContext()
  private lateinit var scenario: ActivityScenario<BookmarkTestActivity>
  private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

  private val uiTimeout = 15_000L
  private val callTimeout = TimeUnit.SECONDS.toMillis(90)

  @Before
  fun setUp() {
    device.wakeUp()
    val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
    if (keyguard.isKeyguardLocked) {
      device.pressMenu()
      device.waitForIdle(2_000)
    }
    // A locked screen makes every picker here fail with an unhelpful "button not found", so say
    // what is actually wrong. V3_TEST_CASES.md Group 10 documents the unlock step.
    check(!keyguard.isKeyguardLocked) {
      "Group 10 needs an unlocked device: the pickers run behind the keyguard otherwise"
    }
    // DocumentsUI remembers its last folder across launches, which makes one test's navigation
    // leak into the next. Clearing it is what keeps these tests independent of run order.
    InstrumentationRegistry.getInstrumentation()
      .uiAutomation
      .executeShellCommand("pm clear com.google.android.documentsui")
      .close()
    scenario = ActivityScenario.launch(BookmarkTestActivity::class.java)
  }

  @After
  fun tearDown() {
    context.contentResolver.persistedUriPermissions.forEach {
      runCatching {
        context.contentResolver.releasePersistableUriPermission(
          it.uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }
    }
    device.pressBack()
    scenario.close()
  }

  private fun manager(): StorageAccessManager {
    lateinit var m: StorageAccessManager
    scenario.onActivity { m = it.storageAccess }
    return m
  }

  /** Starts [call] on Main (pickers must run there) and hands back its eventual result. */
  private fun <T> startOnMain(call: suspend (StorageAccessManager) -> T): CompletableDeferred<T> {
    val manager = manager()
    val deferred = CompletableDeferred<T>()
    MainScope().launch(Dispatchers.Main) { deferred.complete(call(manager)) }
    return deferred
  }

  // TC-90: ensureAccess on a raw-accessible path answers without showing any UI
  @Test
  fun tc90_ensureAccessOnRawPathNeedsNoUi() = runBlocking {
    val appDir = context.getExternalFilesDir(null)!!
    val path = StoragePath.fromAbsolutePath(context, appDir.absolutePath)

    val deferred = startOnMain { it.ensureAccess(path) }
    val result = withTimeout(TimeUnit.SECONDS.toMillis(20)) { deferred.await() }

    assertTrue("expected Granted without UI but was $result", result is AccessResult.Granted)
    assertTrue((result as AccessResult.Granted).folder.canWrite)
    assertTrue(
      "no SAF grant should have been taken",
      context.contentResolver.persistedUriPermissions.isEmpty(),
    )
  }

  // TC-91: pickFolder -> DocumentsUI -> Picked
  @Test
  fun tc91_pickFolderGrantsAccess() = runBlocking {
    // Open inside Documents: the volume root cannot be granted on modern Android - picking it
    // leaves DocumentsUI showing "Can't use this folder".
    val deferred = startOnMain { it.pickFolder(StoragePath.primary("Documents")) }

    waitForSystemUi("TC-91: folder picker")
    tapText("use this folder", "TC-91: folder picker")
    tapText("allow", "TC-91: grant confirmation")

    val result = withTimeout(callTimeout) { deferred.await() }
    val picked = result as? FolderPickerResult.Picked
    assertTrue("expected Picked but was $result", picked != null)
    assertEquals("Documents", picked!!.folder.name)
    assertTrue("picked folder must be readable", picked.folder.canRead())
    println("TC-91: picked ${picked.folder.uri}")
  }

  // TC-92: back out of the folder picker -> CanceledByUser
  @Test
  fun tc92_pickFolderCanceled() = runBlocking {
    val deferred = startOnMain { it.pickFolder() }

    waitForSystemUi("TC-92: folder picker")
    device.pressBack()

    val result = withTimeout(callTimeout) { deferred.await() }
    assertEquals(FolderPickerResult.CanceledByUser, result)
  }

  // TC-93: createFile places a real document through SAF
  @Test
  fun tc93_createFileThroughSaf(): Unit = runBlocking {
    val fileName = "tc93_${System.currentTimeMillis()}.txt"
    val deferred = startOnMain { it.createFile("text/plain", fileName) }

    waitForSystemUi("TC-93: create-document UI")
    tapText("save", "TC-93: save button")

    val result = withTimeout(callTimeout) { deferred.await() }
    val created = result as? FileCreationResult.Created
    assertTrue("expected Created but was $result", created != null)
    assertEquals(fileName, created!!.file.name)
    created.file.delete()
  }

  // TC-94: pickFiles returns what the user tapped
  @Test
  fun tc94_pickFilesReturnsSelection() = runBlocking {
    val seeded = seedPickableFile()
    val deferred = startOnMain { it.pickFiles(initialPath = StoragePath.primary("Download")) }

    waitForSystemUi("TC-94: file picker")
    scrollToAndTapText(seeded, "TC-94: seeded file '$seeded'")

    val result = withTimeout(callTimeout) { deferred.await() }
    val picked = result as? FilePickerResult.Picked
    assertTrue("expected Picked but was $result", picked != null)
    assertEquals(1, picked!!.files.size)
    assertEquals(seeded, picked.files.single().name)
  }

  // TC-96: pickMedia returns the picked image
  @Test
  fun tc96_pickMediaReturnsSelection() = runBlocking {
    seedPickableImage()
    val deferred = startOnMain { it.pickMedia() }

    waitForSystemUi("TC-96: photo picker")
    tapFirstPhoto()
    tapText("done", "TC-96: done button")

    val picked = withTimeout(callTimeout) { deferred.await() }
    assertTrue("expected at least one media item, got $picked", picked.isNotEmpty())
    assertTrue("picked media must be readable", picked.first().canRead)
    println("TC-96: picked ${picked.map { it.name }}")
  }

  // TC-97: backing out of the photo picker yields an empty selection, not a hang
  @Test
  fun tc97_pickMediaCanceled() = runBlocking {
    val deferred = startOnMain { it.pickMedia() }

    waitForSystemUi("TC-97: photo picker")
    device.pressBack()

    val picked = withTimeout(callTimeout) { deferred.await() }
    assertTrue("expected an empty selection but got $picked", picked.isEmpty())
  }

  // TC-95: requestStoragePermission is a no-op above API 29
  @Test
  fun tc95_storagePermissionIsNoOpOnModernApi() = runBlocking {
    assertTrue("this device is API ${Build.VERSION.SDK_INT}", Build.VERSION.SDK_INT >= 30)
    val deferred = startOnMain { it.requestStoragePermission() }
    val granted = withTimeout(TimeUnit.SECONDS.toMillis(20)) { deferred.await() }
    assertTrue("API 30+ must report permission granted without any dialog", granted)
  }

  /**
   * Puts a file in Downloads through MediaStore - scoped storage forbids writing there directly -
   * so the SAF picker has something real to browse to. Returns its name.
   */
  /**
   * DocumentsUI lists Download from MediaProvider's database on API 30+, so the file has to be
   * created through MediaStore - a file dropped straight onto the filesystem stays invisible until
   * something scans it. REPLACE keeps the name short and stable so it survives repeated runs.
   */
  private fun seedPickableFile(): String {
    val name = "tc94_pick.txt"
    val media =
      MediaStoreCompat.createDownload(context, FileDescription(name), CreateMode.REPLACE)
        ?: error("could not seed a file in Downloads through MediaStore")
    media.openOutputStream()?.use { it.write("pick me".toByteArray()) }
    val deadline = System.currentTimeMillis() + 10_000
    while (media.length <= 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(100)
    }
    check(media.length > 0) { "seeded file never materialised in MediaStore" }
    return media.name ?: name
  }

  private fun shell(command: String) {
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    device.waitForIdle(1_000)
  }

  /** Puts one image in MediaStore so the photo picker has something to show. */
  private fun seedPickableImage() {
    val media =
      MediaStoreCompat.createImage(
          context,
          FileDescription("tc96_${System.currentTimeMillis()}.png"),
        )
        ?: error("could not seed an image through MediaStore")
    val bitmap =
      android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.CYAN)
    media.openOutputStream()?.use {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }
  }

  /** The photo picker labels each thumbnail by capture date, so match the grid cell by class. */
  private fun tapFirstPhoto() {
    // The thumbnail itself is not marked clickable - only its container is - so match on the
    // accessibility label ("Photo taken on Aug 12, 2026 4:28 PM") and tap its centre.
    val cell =
      device.wait(Until.findObject(By.descContains("Photo taken on")), uiTimeout)
        ?: device.wait(Until.findObject(By.descContains("Photo")), uiTimeout)
    if (cell == null) {
      dumpUi("TC-96: no photo thumbnail found")
      error("TC-96: photo picker showed no selectable item within ${uiTimeout}ms")
    }
    cell.click()
    device.waitForIdle(5_000)
  }

  private fun waitForSystemUi(what: String) {
    val ourPackage = context.packageName
    val deadline = System.currentTimeMillis() + uiTimeout
    while (System.currentTimeMillis() < deadline) {
      val current = device.currentPackageName
      if (current != null && current != ourPackage) {
        device.waitForIdle(5_000)
        return
      }
      Thread.sleep(200)
    }
    dumpUi("$what: system UI never came to the foreground")
    error("$what: no system UI within ${uiTimeout}ms")
  }

  /**
   * Like [tapText], but scrolls the list first: UiAutomator only sees rendered nodes, so a file
   * below the fold in a busy Download folder is invisible to a plain lookup.
   */
  private fun scrollToAndTapText(text: String, what: String) {
    val pattern =
      Pattern.compile(".*${Pattern.quote(text)}.*", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    repeat(10) {
      device.findObject(By.text(pattern))?.let {
        it.click()
        device.waitForIdle(5_000)
        return
      }
      val list = device.findObject(By.scrollable(true)) ?: return@repeat
      list.scroll(androidx.test.uiautomator.Direction.DOWN, 0.8f)
      device.waitForIdle(2_000)
    }
    dumpUi("$what: '$text' not found after scrolling")
    error("$what: could not find '$text' within the list")
  }

  private fun tapText(text: String, what: String) {
    // Substring match: By.text(Pattern) matches the WHOLE label, and system buttons carry text
    // like "Allow access to Documents".
    val pattern =
      Pattern.compile(".*${Pattern.quote(text)}.*", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    // Clickable first: a confirmation dialog repeats the button wording in its message ("Allow
    // <app> to access Documents?"), and tapping the message does nothing at all.
    val target =
      device.wait(Until.findObject(By.clickable(true).text(pattern)), uiTimeout)
        ?: device.findObject(By.text(pattern))
    if (target == null) {
      dumpUi("$what: '$text' not found")
      error("$what: could not find '$text' within ${uiTimeout}ms")
    }
    target.click()
    device.waitForIdle(5_000)
  }

  private fun dumpUi(reason: String) {
    runCatching {
      val out = ByteArrayOutputStream()
      device.dumpWindowHierarchy(out)
      println("$reason\n--- window hierarchy ---\n$out\n--- end hierarchy ---")
    }
  }
}
