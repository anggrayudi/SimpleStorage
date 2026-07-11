@file:JvmName("ActivityUtils")

package com.anggrayudi.storage.sample

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Keeps the activity content clear of the status bar, navigation bar, display cutout, and
 * keyboard, now that edge-to-edge is enforced for apps targeting API 35+. Without this, scrolling
 * content draws behind the status bar and slides under the action bar. AppCompat's
 * ActionBarOverlayLayout still offsets the content below the action bar by itself, so only the
 * system insets need handling here.
 */
fun AppCompatActivity.applyEdgeToEdgeContentInsets() {
  ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets
    ->
    val insets =
      windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or
          WindowInsetsCompat.Type.displayCutout() or
          WindowInsetsCompat.Type.ime()
      )
    view.updatePadding(
      left = insets.left,
      top = insets.top,
      right = insets.right,
      bottom = insets.bottom,
    )
    WindowInsetsCompat.CONSUMED
  }
}
