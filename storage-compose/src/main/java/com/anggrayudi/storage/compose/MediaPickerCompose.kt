package com.anggrayudi.storage.compose

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.anggrayudi.storage.StorageFile

/**
 * Launches the system Photo Picker ([ActivityResultContracts.PickVisualMedia]).
 *
 * @author Anggrayudi H
 */
public class MediaPickerLauncher
internal constructor(
  private val single: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>?,
  private val multiple: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>?,
) {

  public fun launch(
    type: ActivityResultContracts.PickVisualMedia.VisualMediaType =
      ActivityResultContracts.PickVisualMedia.ImageAndVideo
  ) {
    val request = PickVisualMediaRequest(type)
    single?.launch(request) ?: multiple?.launch(request)
  }
}

/**
 * Remembers a launcher for the system Photo Picker — no storage permission and no SAF grant
 * required. Picked media arrive as [StorageFile]s; an empty list means the user canceled.
 *
 * @param maxItems `1` opens the single-pick UI; `2..100` allows multi-select. Must not change
 *   across recompositions.
 */
@Composable
public fun rememberLauncherForMediaPicker(
  maxItems: Int = 1,
  onMediaPicked: (List<StorageFile>) -> Unit,
): MediaPickerLauncher {
  val appContext = LocalContext.current.applicationContext
  val currentOnMediaPicked = rememberUpdatedState(onMediaPicked)
  return if (maxItems <= 1) {
    val launcher =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        currentOnMediaPicked.value(
          listOfNotNull(uri?.let { StorageFile.from(appContext, it) })
        )
      }
    remember(launcher) { MediaPickerLauncher(launcher, null) }
  } else {
    val launcher =
      rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems)
      ) { uris ->
        currentOnMediaPicked.value(uris.mapNotNull { StorageFile.from(appContext, it) })
      }
    remember(launcher) { MediaPickerLauncher(null, launcher) }
  }
}
