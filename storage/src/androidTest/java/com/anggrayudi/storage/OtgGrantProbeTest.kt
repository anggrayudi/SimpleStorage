package com.anggrayudi.storage

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anggrayudi.storage.access.AccessResult
import com.anggrayudi.storage.access.StorageAccessManager
import com.anggrayudi.storage.file.openInputStream
import com.anggrayudi.storage.file.openOutputStream
import com.anggrayudi.storage.file.CreateMode
import com.anggrayudi.storage.file.makeFile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Manual probe for OPEN_ITEMS A1: does a SAF grant on a removable volume survive unplugging and
 * replugging the drive? Not part of the automated suite — it needs a physical USB OTG drive and a
 * human to pull it out between the two runs.
 *
 * Both tests are opt-in: without `-e otgProbe true` they skip, so the automated suite is unaffected
 * (otherwise `grantOnce` would sit waiting for a SAF dialog nobody is going to answer).
 *
 * 1. `grantOnce` opens the SAF picker and waits up to five minutes for the grant. Run it once.
 * 2. `reportAccess` prints the volume list, the persisted URI permissions, and whether the library
 *    can still resolve, read and write the volume. Run it before the replug, then again after.
 *
 * ```
 * adb shell am instrument -w -e otgProbe true \
 *   -e class com.anggrayudi.storage.OtgGrantProbeTest#reportAccess \
 *   com.anggrayudi.storage.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class OtgGrantProbeTest {

  private val context: Context = targetContext()

  private fun removableVolumeId(): String? {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    return storageManager.storageVolumes
      .firstOrNull { !it.isPrimary && it.state == Environment.MEDIA_MOUNTED }
      ?.uuid
  }

  private fun assumeProbeRequested() {
    assumeTrue(
      "manual probe - pass -e otgProbe true to run it",
      InstrumentationRegistry.getArguments().getString("otgProbe") == "true",
    )
  }

  @Test
  fun grantOnce() = runBlocking {
    assumeProbeRequested()
    val volumeId = removableVolumeId()
    assumeTrue("no removable volume mounted - plug the OTG drive in first", volumeId != null)
    println("PROBE: asking for access to $volumeId - answer the SAF dialog on the device")

    val scenario = ActivityScenario.launch(BookmarkTestActivity::class.java)
    try {
      lateinit var manager: StorageAccessManager
      scenario.onActivity { manager = it.storageAccess }
      val deferred = CompletableDeferred<AccessResult>()
      MainScope().launch(Dispatchers.Main) {
        deferred.complete(manager.ensureAccess(StoragePath(volumeId!!, "")))
      }
      val result = withTimeout(TimeUnit.MINUTES.toMillis(5)) { deferred.await() }
      println("PROBE: grant result = $result")
      assertTrue("expected Granted but was $result", result is AccessResult.Granted)
    } finally {
      scenario.close()
    }
  }

  @Test
  fun reportAccess() {
    assumeProbeRequested()
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    println("PROBE: ---------- volume report ----------")
    storageManager.storageVolumes.forEach {
      println(
        "PROBE: volume uuid=${it.uuid} primary=${it.isPrimary} state=${it.state} " +
          "label=${it.getDescription(context)}"
      )
    }

    val persisted = context.contentResolver.persistedUriPermissions
    println("PROBE: persisted URI permissions = ${persisted.size}")
    persisted.forEach {
      println(
        "PROBE:   uri=${it.uri} read=${it.isReadPermission} write=${it.isWritePermission} " +
          "since=${it.persistedTime}"
      )
    }

    val volumeId = removableVolumeId()
    println("PROBE: mounted removable volume id = $volumeId")
    if (volumeId == null) {
      println("PROBE: drive is not mounted right now - nothing else to check")
      return
    }

    // The question A1 actually asks: is the remembered grant still usable, not just still listed?
    val root = StorageFile.fromPath(context, StoragePath(volumeId, ""), requiresWriteAccess = true)
    println("PROBE: StorageFile.fromPath(root, write=true) -> ${root?.uri}")
    if (root != null) {
      val children = runCatching { root.list().map { it.name } }
      println("PROBE: canRead=${root.canRead} canWrite=${root.canWrite}")
      println("PROBE: listing = ${children.getOrNull()?.take(10)} error=${children.exceptionOrNull()}")
    }
    println("PROBE: isMountedVolumeId = ${DocumentFileCompatProbe.isMounted(context, volumeId)}")

    // canWrite is a permission flag, not proof: actually write, read back and clean up.
    if (root != null) {
      val probe =
        runCatching {
          // v3 has no create-a-file API, so this goes through the 2.x escape hatch.
          val dir = root.asDocumentFile() ?: error("root is not a DocumentFile")
          val file =
            dir.makeFile(context, "probe_write.txt", "text/plain", CreateMode.REPLACE)
              ?: error("could not create the probe file")
          file.openOutputStream(context)?.use { it.write("otg probe".toByteArray()) }
          val readBack = file.openInputStream(context)?.use { String(it.readBytes()) }
          val deleted = file.delete()
          "wrote+read '$readBack', deleted=$deleted"
        }
      println("PROBE: write test -> ${probe.getOrNull() ?: probe.exceptionOrNull()}")
    }
    println("PROBE: ------------------------------------")
  }
}

/** Keeps the probe readable while reaching a `DocumentFileCompat` helper by its real name. */
private object DocumentFileCompatProbe {
  fun isMounted(context: Context, volumeId: String): Boolean =
    com.anggrayudi.storage.file.DocumentFileCompat.isMountedVolumeId(context, volumeId)
}
