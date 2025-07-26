# SimpleStorage
![Maven Central](https://img.shields.io/maven-central/v/com.anggrayudi/storage.svg)
[![Build Status](https://github.com/anggrayudi/SimpleStorage/workflows/Android%20CI/badge.svg)](https://github.com/anggrayudi/SimpleStorage/actions?query=workflow%3A%22Android+CI%22)

### Table of Contents
* [Overview](#overview)
  + [Java Compatibility](#java-compatibility)
  + [Jetpack Compose](#jetpack-compose)
* [Terminology](#terminology)
* [Check Accessible Paths](#check-accessible-paths)
* [Read Files](#read-files)
  + [`DocumentFileCompat`](#documentfilecompat)
    - [Example](#example)
  + [`MediaStoreCompat`](#mediastorecompat)
    - [Example](#example-1)
* [Manage Files](#manage-files)
  + [`DocumentFile`](#documentfile)
  + [`MediaFile`](#mediafile)
* [Request Storage Access, Pick Folder & Files, Request Create File, etc.](#request-storage-access-pick-folder--files-request-create-file-etc)
* [Activity Result Contracts](#activity-result-contracts)
* [Move & Copy: Files & Folders](#move--copy-files--folders)
* [Search: Files & Folders](#search-files--folders)
* [Compress & Unzip: Files & Folders](#compress--unzip-files--folders)
  + [Compression](#compression)
  + [Decompression](#decompression)
* [FAQ](#faq)
* [Other SimpleStorage Usage Examples](#other-simpleStorage-usage-examples)
* [License](#license)

## Overview

The more higher API level, the more Google restricted file access on Android storage.
Although Storage Access Framework (SAF) is designed to secure user's storage from malicious apps,
but this makes us even more difficult in accessing files as a developer. Let's take an example where
[`java.io.File` has been deprecated in Android 10](https://commonsware.com/blog/2019/06/07/death-external-storage-end-saga.html).

Simple Storage ease you in accessing and managing files across API levels.
If you want to know more about the background of this library, please read this article:
[Easy Storage Access Framework in Android with SimpleStorage](https://medium.com/@hardiannicko/easy-storage-access-framework-in-android-with-simplestorage-ec0a566f472c)

Adding Simple Storage into your project is pretty simple:

```groovy
implementation "com.anggrayudi:storage:X.Y.Z"

// For Jetpack Compose
implementation "com.anggrayudi:storage-compose:X.Y.Z"
```

Where `X.Y.Z` is the library version: ![Maven Central](https://img.shields.io/maven-central/v/com.anggrayudi/storage.svg)

All versions can be found here:
- [Simple Storage Core](https://central.sonatype.com/artifact/com.anggrayudi/storage/versions)
- [Simple Storage Jetpack Compose](https://central.sonatype.com/artifact/com.anggrayudi/storage-compose/versions)

To use `SNAPSHOT` version, you need to add this URL to the root Gradle:

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        // add this line
        maven { url "https://central.sonatype.com/repository/maven-snapshots/" }
    }
}
```

### Java Compatibility

Simple Storage is built in Kotlin. Follow this [documentation](JAVA_COMPATIBILITY.md) to use it in your Java project.

Note that some long-running functions like copy, move, search, compress, and unzip are now only available in Kotlin.
They are powered by Kotlin Coroutines & Flow, which are easy to use.
You can still use these Java features in your project, but you will need [v1.5.6](https://github.com/anggrayudi/SimpleStorage/releases/tag/1.5.6) which is the latest version that
supports Java.

### Jetpack Compose

`SimpleStorageHelper` is a traditional class that helps you to request storage access, pick folders/files, and create files.
This class has interactive dialogs, so you don't have to handle storage access & permissions manually.
In Jetpack Compose, you can achieve the same thing with [`SimpleStorageCompose.kt`](storage-compose/src/main/java/com/anggrayudi/storage/compose/SimpleStorageCompose.kt).
This class contains composable functions:
- `rememberLauncherForStoragePermission()`
- `rememberLauncherForStorageAccess()`
- `rememberLauncherForFolderPicker()`
- `rememberLauncherForFilePicker()`

If you think these composable functions has too many UI manipulations and don't suit your needs, then
you can copy the logic from [`SimpleStorageCompose.kt`](storage-compose/src/main/java/com/anggrayudi/storage/compose/SimpleStorageCompose.kt)
and create your own composable functions. Because you might need custom dialogs, custom strings, etc.

For file creation, you can use `rememberLauncherForActivityResult(FileCreationContract(context))`.
Check all available contracts in the [`SimpleStorageResultContracts.kt`](storage/src/main/java/com/anggrayudi/storage/contract/SimpleStorageResultContracts.kt)

## Terminology

![Alt text](art/terminology.png?raw=true "Simple Storage Terms")

### Other Terminology
* Storage Permission – related to [runtime permissions](https://developer.android.com/training/permissions/requesting)
* Storage Access – related to [URI permissions](https://developer.android.com/reference/android/content/ContentResolver#takePersistableUriPermission(android.net.Uri,%20int))

## Check Accessible Paths

To check whether you have access to particular paths, call `DocumentFileCompat.getAccessibleAbsolutePaths()`. The results will look like this in breakpoint:

![Alt text](art/getAccessibleAbsolutePaths.png?raw=true "DocumentFileCompat.getAccessibleAbsolutePaths()")

All paths in those locations are accessible via functions `DocumentFileCompat.from*()`, otherwise your action will be denied by the system if you want to
access paths other than those, then functions `DocumentFileCompat.from*()` (next section) will return null as well. On API 28-, you can obtain it by requesting
the runtime permission. For API 29+, it is obtained automatically by calling `SimpleStorageHelper#requestStorageAccess()` or
`SimpleStorageHelper#openFolderPicker()`. The granted paths are persisted by this library via `ContentResolver#takePersistableUriPermission()`,
so you don't need to remember them in preferences:
```kotlin
buttonSelectFolder.setOnClickListener {
    storageHelper.openFolderPicker()
}

storageHelper.onFolderSelected = { requestCode, folder ->
    // tell user the selected path
}
```

In the future, if you want to write files into the granted path, use `DocumentFileCompat.fromFullPath()`:
```kotlin
val grantedPaths = DocumentFileCompat.getAccessibleAbsolutePaths(this)
val path = grantedPaths.values.firstOrNull()?.firstOrNull() ?: return
val folder = DocumentFileCompat.fromFullPath(this, path, requiresWriteAccess = true)
val file = folder?.makeFile(this, "notes", "text/plain")
```

## Read Files

In Simple Storage, `DocumentFile` is used to access files when your app has been granted full storage access,
included URI permissions for read and write. Whereas `MediaFile` is used to access media files from `MediaStore`
without URI permissions to the storage.

You can read file with helper functions in `DocumentFileCompat` and `MediaStoreCompat`:

### `DocumentFileCompat`

* `DocumentFileCompat.fromFullPath()`
* `DocumentFileCompat.fromSimplePath()`
* `DocumentFileCompat.fromFile()`
* `DocumentFileCompat.fromPublicFolder()`

#### Example
```kotlin
val fileFromExternalStorage = DocumentFileCompat.fromSimplePath(context, basePath = "Download/MyMovie.mp4")

val fileFromSdCard = DocumentFileCompat.fromSimplePath(context, storageId = "9016-4EF8", basePath = "Download/MyMovie.mp4")
```

### `MediaStoreCompat`

* `MediaStoreCompat.fromMediaId()`
* `MediaStoreCompat.fromFileName()`
* `MediaStoreCompat.fromRelativePath()`
* `MediaStoreCompat.fromFileNameContains()`
* `MediaStoreCompat.fromMimeType()`
* `MediaStoreCompat.fromMediaType()`

#### Example
```kotlin
val myVideo = MediaStoreCompat.fromFileName(context, MediaType.DOWNLOADS, "MyMovie.mp4")

val imageList = MediaStoreCompat.fromMediaType(context, MediaType.IMAGE)
```

## Manage Files

### `DocumentFile`

Since `java.io.File` has been deprecated in Android 10, thus you have to use `DocumentFile` for file management.

Simple Storage adds Kotlin extension functions to `DocumentFile`, so you can manage files like this:
* `DocumentFile.getStorageId()`
* `DocumentFile.getStorageType()`
* `DocumentFile.getBasePath()`
* `DocumentFile.copyFileTo()`
* `List<DocumentFile>.moveTo()`
* `DocumentFile.search()`
* `DocumentFile.deleteRecursively()`
* `DocumentFile.getProperties()`
* `DocumentFile.openOutputStream()`, and many more…

### `MediaFile`

For media files, you can have similar capabilities to `DocumentFile`, i.e.:
* `MediaFile.absolutePath`
* `MediaFile.isPending`
* `MediaFile.delete()`
* `MediaFile.renameTo()`
* `MediaFile.copyFileTo()`
* `MediaFile.moveFileTo()`
* `MediaFile.openInputStream()`
* `MediaFile.openOutputStream()`, etc.

## Request Storage Access, Pick Folder & Files, Request Create File, etc.

Although user has granted read and write permissions during runtime, your app may still does not have full access to the storage,
thus you cannot search, move and copy files. You can check whether you have the storage access via `SimpleStorage.hasStorageAccess()` or
`DocumentFileCompat.getAccessibleAbsolutePaths()`.

To enable full storage access, you need to open SAF and let user grant URI permissions for read and write access.
This library provides you an helper class named `SimpleStorageHelper` to ease the request process:

```kotlin
class MainActivity : AppCompatActivity() {

    private val storageHelper = SimpleStorageHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Only setup required callbacks, based on your need:
        storageHelper.onStorageAccessGranted = { requestCode, root ->
            // do stuff
        }
        storageHelper.onFolderSelected = { requestCode, folder ->
            // do stuff
        }
        storageHelper.onFileSelected = { requestCode, files ->
            // do stuff
        }
        storageHelper.onFileCreated = { requestCode, file ->
            // do stuff
        }

        // Depends on your actions:
        btnRequestStorageAccess.setOnClickListener { storageHelper.requestStorageAccess() }
        btnOpenFolderPicker.setOnClickListener { storageHelper.openFolderPicker() }
        btnOpenFilePicker.setOnClickListener { storageHelper.openFilePicker() }
        btnCreateFile.setOnClickListener { storageHelper.createFile("text/plain", "Test create file") }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storageHelper.onRestoreInstanceState(savedInstanceState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Mandatory for direct subclasses of android.app.Activity,
        // but not for subclasses of androidx.fragment.app.Fragment, androidx.activity.ComponentActivity, androidx.appcompat.app.AppCompatActivity
        storageHelper.storage.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Mandatory for direct subclasses of android.app.Activity,
        // but not for subclasses of androidx.fragment.app.Fragment, androidx.activity.ComponentActivity, androidx.appcompat.app.AppCompatActivity
        storageHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
```

Simple, right?

This helper class contains default styles for managing storage access.
If you want to use custom dialogs for `SimpleStorageHelper`, just copy the logic from this class.

## Activity Result Contracts

If you want to use `ActivityResultContract` instead of `SimpleStorageHelper`, you can use contracts
provided in [`SimpleStorageResultContracts.kt`](storage/src/main/java/com/anggrayudi/storage/contract/SimpleStorageResultContracts.kt):
- `RequestStorageAccessContract`
- `StoragePermissionContract`
- `FileCreationContract`
- `OpenFilePickerContract`
- `OpenFolderPickerContract`

Then use them like this:
```kotlin
class MainActivity : AppCompatActivity() {
  lateinit var requestStorageAccessLauncher: ActivityResultLauncher<RequestStorageAccessContract.Options>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // setContentView(R.layout.activity_main)
    val contract = RequestStorageAccessContract(
        expectedStorageId = StorageId.PRIMARY,
        expectedBasePath = "Documents"
    )
    requestStorageAccessLauncher = registerForActivityResult(contract) { result -> 
      when (result) {
        is RequestStorageAccessResult.RootPathNotSelected -> {
          // Ask user to select the root path.
        }
        is RequestStorageAccessResult.ExpectedStorageNotSelected -> {
          // Ask the user to select the expected storage.
          // This can happen if you set expectedBasePath or expectedStorageType to the contract.
        }
        is RequestStorageAccessResult.RootPathPermissionGranted -> {
          // Access granted to the root path
        }
      }
    }

    btnRequestStorageAccess.setOnClickListener {
      val options = RequestStorageAccessContract.Options(
        initialPath = FileFullPath(
          baseContext,
          storageId = StorageId.PRIMARY,
          basePath = "Documents"
        )
      )
      requestStorageAccessLauncher.launch(options)
    }
  }
}
```

This way, you don't need to maintain the instance of `SimpleStorageHelper`, dealing with `onActivityResult()`, `onSaveInstanceState()`, etc.

## Move & Copy: Files & Folders

Simple Storage helps you in copying/moving files & folders via:
* `DocumentFile.copyFileTo()`
* `DocumentFile.moveFileTo()`
* `DocumentFile.copyFolderTo()`
* `DocumentFile.moveFolderTo()`

For example, you can move a folder with few lines of code:

```kotlin
val folder: DocumentFile = ...
val targetFolder: DocumentFile = ...

val job = ioScope.launch {
  folder.moveFolderTo(applicationContext, targetFolder, skipEmptyFiles = false, updateInterval = 1000, onConflict = object : FolderConflictCallback(uiScope) {
    override fun onParentConflict(destinationFolder: DocumentFile, action: ParentFolderConflictAction, canMerge: Boolean) {
      handleParentFolderConflict(destinationFolder, action, canMerge)
    }

    override fun onContentConflict(
      destinationFolder: DocumentFile,
      conflictedFiles: MutableList<FileConflict>,
      action: FolderContentConflictAction
    ) {
      handleFolderContentConflict(action, conflictedFiles)
    }
  }).onCompletion {
    if (it is CancellationException) {
      Timber.d("Folder move is aborted")
    }
  }.collect { result ->
    when (result) {
      is FolderResult.Validating -> Timber.d("Validating...")
      is FolderResult.Preparing -> Timber.d("Preparing...")
      is FolderResult.CountingFiles -> Timber.d("Counting files...")
      is FolderResult.DeletingConflictedFiles -> Timber.d("Deleting conflicted files...")
      is FolderResult.Starting -> Timber.d("Starting...")
      is FolderResult.InProgress -> Timber.d("Progress: ${result.progress.toInt()}% | ${result.fileCount} files")
      is FolderResult.Completed -> uiScope.launch {
        Timber.d("Completed: ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files")
        Toast.makeText(baseContext, "Moved ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files", Toast.LENGTH_SHORT).show()
      }

      is FolderResult.Error -> uiScope.launch {
        Timber.e(result.errorCode.name)
        Toast.makeText(baseContext, "An error has occurred: ${result.errorCode.name}", Toast.LENGTH_SHORT).show()
      }
    }
  }
}

// call this function somewhere, for example in a dialog with a cancel button:
job.cancel() // it will abort the process
```

The coolest thing of this library is you can ask users to choose Merge, Replace, Create New, or Skip Duplicate folders & files
whenever a conflict is found via `onConflict()`. Here're screenshots of the sample code when dealing with conflicts:

![Alt text](art/parent-folder-conflict.png?raw=true "Parent Folder Conflict")
![Alt text](art/folder-content-conflict.png?raw=true "Folder Content Conflict")

Read [`MainActivity`](sample/src/main/java/com/anggrayudi/storage/sample/activity/MainActivity.kt)
from the sample code if you want to mimic above dialogs.

## Search: Files & Folders

You can search files and folders by using `DocumentFile.search()` extension function:

```kotlin
ioScope.launch {
  val nameToFind = "nicko" // search files with name containing "nicko"
  folder.search(recursive = true, regex = Regex("^.*$nameToFind.*\$"), updateInterval = 1000).collect {
    // update results every 1 second
    Timber.d("Found ${it.size} files, last: ${it.lastOrNull()?.fullName}")
  }
}
```

## Compress & Unzip: Files & Folders

### Compression

To compress files and folders, use `List<DocumentFile>.compressToZip()` extension function:

```kotlin
ioScope.launch {
  // make sure you have an URI access to /storage/emulated/0/Documents, otherwise it will return null
  val targetZipFile = DocumentFileCompat.createFile(baseContext, basePath = "Documents/compress test.zip", mimeType = "application/zip")
  if (targetZipFile != null) {
    listOf(folder).compressToZip(baseContext, targetZipFile, deleteSourceWhenComplete = false, updateInterval = 500).collect {
      when (it) {
        is ZipCompressionResult.CountingFiles -> Timber.d("Calculating...")
        is ZipCompressionResult.Compressing -> Timber.d("Compressing... ${it.progress.toInt()}%")
        is ZipCompressionResult.Completed -> Timber.d("Completed: ${it.zipFile.fullName}")
        is ZipCompressionResult.Error -> Timber.e(it.errorCode.name)
        is ZipCompressionResult.DeletingEntryFiles -> Timber.d("Deleting ...") // will be emitted if `deleteSourceWhenComplete` is true
      }
    }
  }
}
```

If you don't have any URI access, then you can request the user to create a ZIP file in the desired location:

```kotlin
storageHelper.onFileCreated = { requestCode, file ->
  ioScope.launch {
    listOf(folder).compressToZip(baseContext, file).collect {
      // do stuff
    }
  }
}
storageHelper.createFile(mimeType = "application/zip", fileName = "compress test", initialPath = FileFullPath(baseContext, StorageId.PRIMARY, "Documents"))
```

### Decompression

FYI, decompressing ZIP files is also easy:

```kotlin
ioScope.launch {
  file.decompressZip(baseContext, targetFolder)
    .onCompletion {
      if (it is CancellationException) {
        Timber.d("Decompression is aborted")
      }
    }.collect {
      when (it) {
        is ZipDecompressionResult.Validating -> Timber.d("Validating...")
        is ZipDecompressionResult.Decompressing -> Timber.d("Decompressing... ${it.bytesDecompressed}")
        is ZipDecompressionResult.Completed -> uiScope.launch {
          Toast.makeText(baseContext, "Decompressed successfully", Toast.LENGTH_SHORT).show()
        }

        is ZipDecompressionResult.Error -> uiScope.launch {
          Toast.makeText(baseContext, "An error has occurred: ${it.errorCode.name}", Toast.LENGTH_SHORT).show()
        }
      }
    }
}
```

## FAQ

Having trouble? Read the [Frequently Asked Questions](FAQ.md) or join the [Discussions](https://github.com/anggrayudi/SimpleStorage/discussions).

## Other SimpleStorage Usage Examples

SimpleStorage is used in these open source projects.
Check how these repositories use it:

* [Snapdrop](https://github.com/fm-sys/snapdrop-android)
* [MaterialPreference](https://github.com/anggrayudi/MaterialPreference)
* [Super Productivity](https://github.com/johannesjo/super-productivity-android)
* [Shared Storage for Flutter](https://pub.dev/packages/shared_storage)
* [Nextcloud Cookbook](https://codeberg.org/MicMun/nextcloud-cookbook)
* [Audiobookshelf](https://github.com/advplyr/audiobookshelf-app)

## License

    Copyright © 2020-2025 Anggrayudi Hardiannico A.
 
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
 
        http://www.apache.org/licenses/LICENSE-2.0
 
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
