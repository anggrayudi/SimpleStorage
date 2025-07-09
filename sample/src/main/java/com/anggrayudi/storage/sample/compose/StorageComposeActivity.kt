package com.anggrayudi.storage.sample.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.anggrayudi.storage.sample.compose.theme.StorageAppTheme

class StorageComposeActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { StorageAppTheme(darkTheme = false) { StorageComposeApp() } }
  }
}
