@file:JvmName("ContextUtils")

package com.anggrayudi.storage.extension

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Created on 17/08/20
 *
 * @author Anggrayudi H
 */
public fun Context.getAppDirectory(type: String? = null): String = "${getExternalFilesDir(type)}"

public fun Context.startActivitySafely(intent: Intent) {
  try {
    startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    // ignore
  }
}

public fun Activity.startActivityForResultSafely(requestCode: Int, intent: Intent) {
  try {
    startActivityForResult(intent, requestCode)
  } catch (_: ActivityNotFoundException) {
    // ignore
  }
}

public fun Context.unregisterReceiverSafely(receiver: BroadcastReceiver?) {
  try {
    unregisterReceiver(receiver ?: return)
  } catch (_: IllegalArgumentException) {
    // ignore
  }
}

public fun Context.fromTreeUri(fileUri: Uri): DocumentFile? =
  try {
    DocumentFile.fromTreeUri(this, fileUri)
  } catch (_: Exception) {
    null
  }

public fun Context.fromSingleUri(fileUri: Uri): DocumentFile? =
  try {
    DocumentFile.fromSingleUri(this, fileUri)
  } catch (_: Exception) {
    null
  }
