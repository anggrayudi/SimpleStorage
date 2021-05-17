@file:JvmName("ContextUtils")

package com.anggrayudi.storage.extension

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */
fun Context.getAppDirectory(type: String? = null) = "${getExternalFilesDir(type)}"

fun Intent?.hasActivityHandler(context: Context) =
    this?.resolveActivity(context.packageManager) != null

fun Context.startActivitySafely(intent: Intent) {
    if (intent.hasActivityHandler(this)) {
        startActivity(intent)
    }
}

fun Activity.startActivityForResultSafely(requestCode: Int, intent: Intent) {
    if (intent.hasActivityHandler(this)) {
        startActivityForResult(intent, requestCode)
    }
}

fun Context.unregisterReceiverSafely(receiver: BroadcastReceiver) {
    try {
        unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
        // ignore
    }
}

fun Context.fromTreeUri(fileUri: Uri) = try {
    DocumentFile.fromTreeUri(this, fileUri)
} catch (e: Exception) {
    null
}

fun Context.fromSingleUri(fileUri: Uri) = try {
    DocumentFile.fromSingleUri(this, fileUri)
} catch (e: Exception) {
    null
}