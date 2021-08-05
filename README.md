# SimpleStorage
![Maven Central](https://img.shields.io/maven-central/v/com.anggrayudi/storage.svg)
[![Build Status](https://github.com/anggrayudi/SimpleStorage/workflows/Android%20CI/badge.svg)](https://github.com/anggrayudi/SimpleStorage/actions?query=workflow%3A%22Android+CI%22)

### Table of Contents
* [Overview](#overview)
  + [Java Compatibility](#java-compatibility)
* [Terminology](#terminology)
* [Read Files](#read-files)
  + [`DocumentFileCompat`](#documentfilecompat)
    - [Example](#example)
  + [`MediaStoreCompat`](#mediastorecompat)
    - [Example](#example-1)
* [Manage Files](#manage-files)
  + [`DocumentFile`](#documentfile)
  + [`MediaFile`](#mediafile)
* [Request Storage Access](#request-storage-access)
* [Folder Picker](#folder-picker)
* [File Picker](#file-picker)
* [`SimpleStorageHelper`](#simplestoragehelper)
* [Move & Copy: Files & Folders](#move--copy-files--folders)
* [License](#license)

## Overview

The more higher API level, the more Google restricted file access on Android storage.
Although Storage Access Framework (SAF) is designed to secure user's storage from malicious apps,
but this makes us even more difficult in accessing files. Let's take an example where
[`java.io.File` has been deprecated in Android 10](https://commonsware.com/blog/2019/06/07/death-external-storage-end-saga.html).

Simple Storage ease you in accessing and managing files across API levels.
If you want to know more about the background of this library, please read this article:
[Easy Storage Access Framework in Android with SimpleStorage](https://medium.com/@hardiannicko/easy-storage-access-framework-in-android-with-simplestorage-ec0a566f472c)

Adding Simple Storage into your project is pretty simple:

```groovy
implementation "com.anggrayudi:storage:X.Y.Z"
```

Where `X.Y.Z` is the library version: ![Maven Central](https://img.shields.io/maven-central/v/com.anggrayudi/storage.svg)

All versions can be found [here](https://oss.sonatype.org/#nexus-search;gav~com.anggrayudi~storage~~~~kw,versionexpand).
To use `SNAPSHOT` version, you need to add this URL to the root Gradle:

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        // add this line
        maven { url "https://oss.sonatype.org/content/repositories/snapshots" }
    }
}
```

### Java Compatibility

Simple Storage is built in Kotlin. Follow this [documentation](JAVA_COMPATIBILITY.md) to use it in your Java project.

## Terminology

![Alt text](art/terminology.png?raw=true "Simple Storage Terms")

### Other Terminology
* Storage Permission – related to [runtime permissions](https://developer.android.com/training/permissions/requesting)
* Storage Access – related to [URI permissions](https://developer.android.com/reference/android/content/ContentResolver#takePersistableUriPermission(android.net.Uri,%20int))

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
val fileFromExternalStorage = DocumentFileCompat.fromSimplePath(context, basePath = "Downloads/MyMovie.mp4")

val fileFromSdCard = DocumentFileCompat.fromSimplePath(context, storageId = "9016-4EF8", basePath = "Downloads/MyMovie.mp4")
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

## Request Storage Access

Although user has granted read and write permissions during runtime, your app may still does not have full access to the storage,
thus you cannot search, move and copy files. You can check whether you have the storage access via `SimpleStorage.hasStorageAccess()`.

To enable full storage access, you need to open SAF and let user grant URI permissions for read and write access. This library provides you
an helper class named `SimpleStorage` to ease the request process:

```kotlin
class MainActivity : AppCompatActivity() {

    private val storage = SimpleStorage(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupSimpleStorage()
        btnRequestStorageAccess.setOnClickListener {
            storage.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS)
        }
    }

    private fun setupSimpleStorage() {
        storage.storageAccessCallback = object : StorageAccessCallback {
            override fun onRootPathNotSelected(
                requestCode: Int,
                rootPath: String,
                uri: Uri,
                selectedStorageType: StorageType,
                expectedStorageType: StorageType
            ) {
                MaterialDialog(this@MainActivity)
                    .message(text = "Please select $rootPath")
                    .negativeButton(android.R.string.cancel)
                    .positiveButton {
                        val initialRoot = if (expectedStorageType.isExpected(selectedStorageType)) selectedStorageType else expectedStorageType
                        storage.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS, initialRoot, expectedStorageType)
                    }.show()
            }

            override fun onCanceledByUser(requestCode: Int) {
                Toast.makeText(baseContext, "Canceled by user", Toast.LENGTH_SHORT).show()
            }

            override fun onStoragePermissionDenied(requestCode: Int) {
                /*
                Request runtime permissions for Manifest.permission.WRITE_EXTERNAL_STORAGE
                and Manifest.permission.READ_EXTERNAL_STORAGE
                */
            }

            override fun onRootPathPermissionGranted(requestCode: Int, root: DocumentFile) {
                Toast.makeText(baseContext, "Storage access has been granted for ${root.getStorageId(baseContext)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storage.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storage.onRestoreInstanceState(savedInstanceState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Mandatory for Activity, but not for Fragment & ComponentActivity
        storage.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Mandatory for Activity, but not for Fragment & ComponentActivity
        storage.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
```

## Folder Picker

```kotlin
private fun requestStoragePermission() {
    /*
    Request runtime permissions for Manifest.permission.WRITE_EXTERNAL_STORAGE
    and Manifest.permission.READ_EXTERNAL_STORAGE
    */
}

private fun setupFolderPickerCallback() {
    storage.folderPickerCallback = object : FolderPickerCallback {
        override fun onStoragePermissionDenied(requestCode: Int) {
            requestStoragePermission()
        }

        override fun onStorageAccessDenied(requestCode: Int, folder: DocumentFile?, storageType: StorageType) {
            if (storageType == StorageType.UNKNOWN) {
                requestStoragePermission()
                return
            }
            MaterialDialog(this@MainActivity)
                .message(
                    text = "You have no write access to this storage, thus selecting this folder is useless." +
                            "\nWould you like to grant access to this folder?"
                )
                .negativeButton(android.R.string.cancel)
                .positiveButton {
                    storage.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS, storageType)
                }.show()
        }

        override fun onFolderSelected(requestCode: Int, folder: DocumentFile) {
            Toast.makeText(baseContext, folder.getAbsolutePath(baseContext), Toast.LENGTH_SHORT).show()
        }

        override fun onCanceledByUser(requestCode: Int) {
            Toast.makeText(baseContext, "Folder picker canceled by user", Toast.LENGTH_SHORT).show()
        }
    }
}
```

## File Picker

```kotlin
private fun setupFilePickerCallback() {
    storage.filePickerCallback = object : FilePickerCallback {
        override fun onCanceledByUser(requestCode: Int) {
            Toast.makeText(baseContext, "File picker canceled by user", Toast.LENGTH_SHORT).show()
        }

        override fun onStoragePermissionDenied(requestCode: Int, file: DocumentFile?) {
            requestStoragePermission()
        }

        override fun onFileSelected(requestCode: Int, file: DocumentFile) {
            Toast.makeText(baseContext, "File selected: ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

## `SimpleStorageHelper`

If you feel implementing folder & file picker and full disk request are complicated enough,
you can use `SimpleStorageHelper` to simplify the process. This helper class contains
default styles for managing storage access.

```kotlin
class MainActivity : AppCompatActivity() {

    private val storageHelper = SimpleStorageHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storageHelper.onFolderSelected = { requestCode, folder ->
            // do stuff
        }
        storageHelper.onFileSelected = { requestCode, files ->
            // do stuff
        }

        btnRequestStorageAccess.setOnClickListener { storageHelper.requestStorageAccess() }
        btnOpenFolderPicker.setOnClickListener { storageHelper.openFolderPicker() }
        btnOpenFilePicker.setOnClickListener { storageHelper.openFilePicker() }
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
        // Mandatory for Activity, but not for Fragment & ComponentActivity
        storageHelper.storage.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Mandatory for Activity, but not for Fragment & ComponentActivity
        storageHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
```

Simpler, right?

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

// Since moveFolderTo() is annotated with @WorkerThread, you must execute it in background thread
folder.moveFolderTo(applicationContext, targetFolder, skipEmptyFiles = false, callback = object : FolderCallback() {
    override fun onPrepare() {
        // Show notification or progress bar dialog with indeterminate state
    }

    override fun onCountingFiles() {
        // Inform user that the app is counting & calculating files
    }

    override fun onStart(folder: DocumentFile, totalFilesToCopy: Int, workerThread: Thread): Long {
        return 1000 // update progress every 1 second
    }

    override fun onParentConflict(destinationFolder: DocumentFile, action: FolderCallback.ParentFolderConflictAction, canMerge: Boolean) {
        handleParentFolderConflict(destinationFolder, action, canMerge)
    }

    override fun onContentConflict(
        destinationFolder: DocumentFile,
        conflictedFiles: MutableList<FolderCallback.FileConflict>,
        action: FolderCallback.FolderContentConflictAction
    ) {
        handleFolderContentConflict(action, conflictedFiles)
    }

    override fun onReport(report: Report) {
        Timber.d("onReport() -> ${report.progress.toInt()}% | Copied ${report.fileCount} files")
    }

    override fun onCompleted(result: Result) {
        Toast.makeText(baseContext, "Copied ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files", Toast.LENGTH_SHORT).show()
    }

    override fun onFailed(errorCode: ErrorCode) {
        Toast.makeText(baseContext, "An error has occurred: $errorCode", Toast.LENGTH_SHORT).show()
    }
})
```

The coolest thing of this library is you can ask users to choose Merge, Replace, Create New, or Skip Duplicate folders & files
whenever a conflict is found via `onConflict()`. Here're screenshots of the sample code when dealing with conflicts:

![Alt text](art/parent-folder-conflict.png?raw=true "Parent Folder Conflict")
![Alt text](art/folder-content-conflict.png?raw=true "Folder Content Conflict")

Read `MainActivity` from the sample code if you want to mimic above dialogs.

## License

    Copyright © 2020-2021 Anggrayudi Hardiannicko A.
 
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
 
        http://www.apache.org/licenses/LICENSE-2.0
 
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
