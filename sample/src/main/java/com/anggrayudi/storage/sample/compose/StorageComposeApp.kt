package com.anggrayudi.storage.sample.compose

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anggrayudi.storage.R
import com.anggrayudi.storage.compose.rememberLauncherForFilePicker
import com.anggrayudi.storage.compose.rememberLauncherForFolderPicker
import com.anggrayudi.storage.compose.rememberLauncherForStorageAccess
import com.anggrayudi.storage.compose.rememberLauncherForStoragePermission
import com.anggrayudi.storage.contract.FileCreationContract
import com.anggrayudi.storage.contract.FileCreationResult
import com.anggrayudi.storage.file.fullName
import com.anggrayudi.storage.file.getAbsolutePath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageComposeApp(modifier: Modifier = Modifier) {
  val activity = LocalActivity.current ?: return
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text("Storage Compose App") },
        navigationIcon = {
          IconButton(onClick = { activity.finish() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.padding(innerPadding)
          .padding(horizontal = 16.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val folderPickerLauncher = rememberLauncherForFolderPicker { folder ->
        Toast.makeText(activity, folder.getAbsolutePath(activity), Toast.LENGTH_SHORT).show()
      }
      val filePickerLauncher =
        rememberLauncherForFilePicker(allowMultiple = true) { files ->
          val names = files.joinToString(", ") { it.fullName }
          Toast.makeText(activity, "File selected: $names", Toast.LENGTH_SHORT).show()
        }
      val fileCreationLauncher =
        rememberLauncherForActivityResult(FileCreationContract(activity)) { result ->
          when (result) {
            is FileCreationResult.Created -> {
              Toast.makeText(activity, "File created: ${result.file.name}", Toast.LENGTH_SHORT)
                .show()
            }
            is FileCreationResult.StoragePermissionDenied -> {
              Toast.makeText(
                  activity,
                  "Storage permission denied. Please grant storage permission to create file.",
                  Toast.LENGTH_SHORT,
                )
                .show()
            }
            is FileCreationResult.CanceledByUser -> Unit
          }
        }
      val storagePermissionLauncher = rememberLauncherForStoragePermission { result ->
        val textResult = if (result) "granted" else "denied"
        Toast.makeText(activity, "Storage permission is $textResult", Toast.LENGTH_SHORT).show()
      }
      val storageAccessLauncher = rememberLauncherForStorageAccess { root ->
        Toast.makeText(
            activity,
            activity.getString(
              R.string.ss_selecting_root_path_success_without_open_folder_picker,
              root.getAbsolutePath(activity),
            ),
            Toast.LENGTH_SHORT,
          )
          .show()
      }

      Spacer(modifier = Modifier.height(24.dp))
      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { storagePermissionLauncher.launch(Unit) },
      ) {
        Text("Request storage permission")
      }
      Button(modifier = Modifier.fillMaxWidth(), onClick = { storageAccessLauncher.launch() }) {
        Text("Request storage access")
      }
      Button(modifier = Modifier.fillMaxWidth(), onClick = { folderPickerLauncher.launch() }) {
        Text("Select folder")
      }
      Button(modifier = Modifier.fillMaxWidth(), onClick = { filePickerLauncher.launch() }) {
        Text("Select file")
      }
      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { fileCreationLauncher.launch(FileCreationContract.Options("text/plain")) },
      ) {
        Text("Create file")
      }
    }
  }
}
