package com.anggrayudi.storage.sample.screen

import android.text.format.Formatter
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.sample.SampleScreen

/** Which volumes exist, how big they are, and which paths this app may touch. */
class StorageInfoScreen : SampleScreen() {

  override val screenTitle = "Storage info"

  override val screenSummary =
    "getStorageIds lists every volume the app can see, including mounted removable ones."

  override fun SampleScreen.buildScreen() {
    val context = this@StorageInfoScreen

    button("List storages") {
      log(
        DocumentFileCompat.getStorageIds(context).joinToString("\n\n") { id ->
          val capacity =
            Formatter.formatFileSize(context, DocumentFileCompat.getStorageCapacity(context, id))
          val free = Formatter.formatFileSize(context, DocumentFileCompat.getFreeSpace(context, id))
          "$id\ncapacity $capacity, free $free"
        }
      )
    }

    button("Show granted paths") {
      val accessible = DocumentFileCompat.getAccessibleAbsolutePaths(context)
      log(
        if (accessible.isEmpty()) "No SAF grant yet — use the access screen first"
        else
          accessible.entries.joinToString("\n\n") { (id, paths) ->
            "$id\n${paths.joinToString("\n")}"
          }
      )
    }

    button("Clean up redundant grants") {
      DocumentFileCompat.cleanupRedundantUriPermissions(context)
      log("Done — ${context.contentResolver.persistedUriPermissions.size} grant(s) left")
    }
  }
}
