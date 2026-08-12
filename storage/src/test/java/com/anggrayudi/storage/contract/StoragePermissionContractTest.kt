package com.anggrayudi.storage.contract

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The contract only asks for what the running API level can still grant. Getting this wrong is
 * silent: the platform ignores the request, the result map comes back empty, and callers read that
 * as "denied" forever.
 */
@RunWith(RobolectricTestRunner::class)
class StoragePermissionContractTest {

  private val contract = StoragePermissionContract()

  @Test
  @Config(sdk = [28])
  fun belowScopedStorageAsksForBoth() {
    assertArrayEquals(
      arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
      ),
      contract.getPermissions(),
    )
  }

  @Test
  @Config(sdk = [31])
  fun underScopedStorageAsksForReadOnly() {
    assertArrayEquals(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), contract.getPermissions())
  }

  @Test
  @Config(sdk = [33])
  fun fromTiramisuAsksForNothing() {
    assertArrayEquals(emptyArray<String>(), contract.getPermissions())
  }

  @Test
  @Config(sdk = [33])
  fun fromTiramisuAnswersWithoutLaunching() {
    // A non-null synchronous result is what keeps the system dialog from ever being shown.
    assertNotNull(contract.getSynchronousResult(RuntimeEnvironment.getApplication(), Unit))
  }

  @Test
  @Config(sdk = [28])
  fun belowScopedStorageStillNeedsTheDialog() {
    assertNull(contract.getSynchronousResult(RuntimeEnvironment.getApplication(), Unit))
  }
}
