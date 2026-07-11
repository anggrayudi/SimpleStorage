package com.anggrayudi.storage

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.anggrayudi.storage.access.StorageAccessManager

/**
 * Minimal host for [StorageAccessManager] instrumented tests (V3_TEST_CASES.md Group 9).
 *
 * [StorageAccessManager] registers ActivityResult launchers in its constructor, which Android
 * only allows before the activity reaches STARTED — so the manager is created here in
 * [onCreate], and tests reach it through [ActivityScenario.onActivity][androidx.test.core.app.ActivityScenario.onActivity].
 */
class BookmarkTestActivity : ComponentActivity() {

  lateinit var storageAccess: StorageAccessManager
    private set

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    storageAccess = StorageAccessManager(this)
  }
}
