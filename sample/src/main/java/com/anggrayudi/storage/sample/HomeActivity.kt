package com.anggrayudi.storage.sample

import android.content.Intent
import com.anggrayudi.storage.sample.compose.StorageComposeActivity
import com.anggrayudi.storage.sample.screen.AccessScreen
import com.anggrayudi.storage.sample.screen.CreateScreen
import com.anggrayudi.storage.sample.screen.JavaScreen
import com.anggrayudi.storage.sample.screen.SearchScreen
import com.anggrayudi.storage.sample.screen.StorageInfoScreen
import com.anggrayudi.storage.sample.screen.TransferScreen
import com.anggrayudi.storage.sample.screen.ZipScreen

/** One entry per use case; each screen is a self-contained example of one part of the API. */
class HomeActivity : SampleScreen() {

  override val screenTitle = "SimpleStorage"

  override val screenSummary =
    "Each screen below demonstrates one part of the v3 API, and holds the whole example in one file."

  override fun SampleScreen.buildScreen() {
    open("Storage access & pickers", AccessScreen::class.java)
    open("Create files & folders", CreateScreen::class.java)
    open("Copy, move & multi-source transfer", TransferScreen::class.java)
    open("Zip & unzip", ZipScreen::class.java)
    open("Search", SearchScreen::class.java)
    open("Storage info & granted paths", StorageInfoScreen::class.java)
    open("Pickers in Jetpack Compose", StorageComposeActivity::class.java)
    open("Calling v3 from Java", JavaScreen::class.java)
  }

  private fun open(label: String, screen: Class<*>) {
    button(label) { startActivity(Intent(this@HomeActivity, screen)) }
  }
}
