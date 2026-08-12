package com.anggrayudi.storage

import com.anggrayudi.storage.file.DocumentFileCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The storage utilities used to live only on the deprecated `SimpleStorage` class, which would have
 * taken them down with it in 4.0. They now live on `DocumentFileCompat`, and the old names
 * delegate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageUtilitiesTest {

  private val context = RuntimeEnvironment.getApplication()

  @Test
  fun utilitiesAnswerFromTheirNewHome() {
    // Values are environment-dependent under Robolectric, so this pins reachability and shape:
    // the point of the move is that these answer at all without touching a deprecated class.
    assertTrue(DocumentFileCompat.externalStoragePath.isNotEmpty())
    DocumentFileCompat.isSdCardPresent
    DocumentFileCompat.hasStoragePermission(context)
    DocumentFileCompat.hasStorageReadPermission(context)
    DocumentFileCompat.hasFullDiskAccess(context, "primary")
    // Nothing is granted, so this has nothing to release - it must still not throw.
    DocumentFileCompat.cleanupRedundantUriPermissions(context)
  }

  @Suppress("DEPRECATION")
  @Test
  fun theDeprecatedNamesStillAgreeWithTheNewOnes() {
    assertEquals(DocumentFileCompat.externalStoragePath, SimpleStorage.externalStoragePath)
    assertEquals(DocumentFileCompat.isSdCardPresent, SimpleStorage.isSdCardPresent)
    assertEquals(
      DocumentFileCompat.hasStoragePermission(context),
      SimpleStorage.hasStoragePermission(context),
    )
    assertEquals(
      DocumentFileCompat.hasStorageAccess(context, DocumentFileCompat.externalStoragePath, true),
      SimpleStorage.hasStorageAccess(context, DocumentFileCompat.externalStoragePath, true),
    )
  }
}
