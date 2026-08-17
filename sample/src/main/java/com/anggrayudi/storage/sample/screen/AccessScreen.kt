package com.anggrayudi.storage.sample.screen

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.anggrayudi.storage.StoragePath
import com.anggrayudi.storage.access.AccessResult
import com.anggrayudi.storage.access.StorageAccessManager
import com.anggrayudi.storage.contract.FileCreationResult
import com.anggrayudi.storage.contract.FilePickerResult
import com.anggrayudi.storage.contract.FolderPickerResult
import com.anggrayudi.storage.sample.SampleScreen

/**
 * Everything interactive lives on [StorageAccessManager]: suspend calls that return when the user
 * has answered, with no request codes, no callbacks and no `onActivityResult`.
 */
@RequiresApi(Build.VERSION_CODES.N)
class AccessScreen : SampleScreen() {

  override val screenTitle = "Storage access & pickers"

  override val screenSummary =
    "StorageAccessManager suspends until the user answers, so each of these is one call."

  // Launchers must be registered before the activity is STARTED, so build the manager in onCreate.
  private lateinit var storageAccess: StorageAccessManager

  override fun onCreate(savedInstanceState: Bundle?) {
    storageAccess = StorageAccessManager(this)
    super.onCreate(savedInstanceState)
  }

  override fun SampleScreen.buildScreen() {
    button("ensureAccess(Documents)") {
      when (val access = storageAccess.ensureAccess(StoragePath.primary("Documents"))) {
        is AccessResult.Granted ->
          log("Granted: ${access.folder.absolutePath ?: access.folder.uri}")
        is AccessResult.WrongRootSelected -> log("Wrong root: ${access.grantedRoot?.name}")
        AccessResult.CanceledByUser -> log("Canceled")
        AccessResult.PermissionDenied -> log("Permission denied")
      }
    }

    button("pickFolder()") {
      when (val result = storageAccess.pickFolder()) {
        is FolderPickerResult.Picked -> log("Picked: ${result.folder.uri}")
        is FolderPickerResult.AccessDenied -> log("Access denied for ${result.folder?.uri}")
        FolderPickerResult.CanceledByUser -> log("Canceled")
      }
    }

    button("pickFiles(allowMultiple = true)") {
      when (val result = storageAccess.pickFiles(allowMultiple = true)) {
        is FilePickerResult.Picked ->
          log("Picked ${result.files.size}: ${result.files.map { it.name }}")
        is FilePickerResult.StoragePermissionDenied -> log("Permission denied")
        FilePickerResult.CanceledByUser -> log("Canceled")
      }
    }

    button("createFile(\"text/plain\")") {
      when (val result = storageAccess.createFile("text/plain", "sample.txt")) {
        is FileCreationResult.Created -> log("Created: ${result.file.uri}")
        FileCreationResult.StoragePermissionDenied -> log("Permission denied")
        FileCreationResult.CanceledByUser -> log("Canceled")
      }
    }

    button("pickMedia() — system Photo Picker") {
      val media = storageAccess.pickMedia()
      log(if (media.isEmpty()) "Nothing picked" else "Picked: ${media.map { it.name }}")
    }

    button("requestStoragePermission()") {
      log("Granted: ${storageAccess.requestStoragePermission()}")
    }
  }
}
