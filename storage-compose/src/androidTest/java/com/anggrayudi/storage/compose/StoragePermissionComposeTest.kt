package com.anggrayudi.storage.compose

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Compose launcher used to carry its own hardcoded WRITE/READ_EXTERNAL_STORAGE pair, which the
 * platform stopped granting: from API 33 the request is ignored, the result map comes back empty,
 * and the caller's callback was never invoked at all.
 */
@RunWith(AndroidJUnit4::class)
class StoragePermissionComposeTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComposePermissionTestActivity>()

  @Test
  fun launcherAnswersWithoutADialogOnModernApi() {
    assertTrue("this device is API ${Build.VERSION.SDK_INT}", Build.VERSION.SDK_INT >= 33)
    val results = mutableListOf<Boolean>()

    composeRule.setContent {
      val launcher = rememberLauncherForStoragePermission { granted -> results.add(granted) }
      LaunchedEffect(Unit) { launcher.launch(Unit) }
      Text("host")
    }

    val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(20)
    while (results.isEmpty() && System.currentTimeMillis() < deadline) {
      composeRule.waitForIdle()
      Thread.sleep(100)
    }

    assertEquals("the callback must fire exactly once", 1, results.size)
    assertTrue("API 33+ has nothing left to request, so this must report granted", results.single())
  }
}
