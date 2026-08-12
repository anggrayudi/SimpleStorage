package com.anggrayudi.storage.sample.screen

import android.content.Context
import com.anggrayudi.storage.StorageFile
import com.anggrayudi.storage.toStorageFile

/**
 * Every operation needs somewhere to work. The app-specific external directory needs no grant at
 * all, which keeps these examples about the operation rather than about permissions.
 */
internal fun playground(context: Context): StorageFile =
  context.getExternalFilesDir(null)!!.toStorageFile(context)
