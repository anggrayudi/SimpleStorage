# SimpleStorage
![Maven Central](https://img.shields.io/maven-central/v/com.anggrayudi/storage.svg)
[![Build Status](https://github.com/anggrayudi/SimpleStorage/workflows/Android%20CI/badge.svg)](https://github.com/anggrayudi/SimpleStorage/actions?query=workflow%3A%22Android+CI%22)

### Table of Contents
* [Overview](#overview)
* [Why v3?](#why-v3)
* [Getting a `StorageFile`](#getting-a-storagefile)
* [Creating files & folders](#creating-files--folders)
* [Copy & move](#copy--move)
* [Conflict resolution — a suspend lambda](#conflict-resolution--a-suspend-lambda)
* [Zip & unzip](#zip--unzip)
* [The Flow forms](#the-flow-forms)
* [Storage access & pickers, without callbacks](#storage-access--pickers-without-callbacks)
* [Jetpack Compose](#jetpack-compose)
* [Terminology](#terminology)
* [Java Compatibility](#java-compatibility)
* [Using the 2.x API](#using-the-2x-api)
* [FAQ](#faq)
* [Contributing](#contributing)
* [Other SimpleStorage Usage Examples](#other-simplestorage-usage-examples)
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

### Version 3

Version `3.0.0-beta03` introduces a redesigned API: one [`StorageFile`](storage/src/main/java/com/anggrayudi/storage/StorageFile.kt)
abstraction over `DocumentFile`/`MediaFile`/`java.io.File`, one-shot suspend operations
(`copyTo`, `moveTo`, `zipTo`, `unzipTo`) with a unified `TransferResult`, suspend-lambda conflict
resolution, and [`StorageAccessManager`](storage/src/main/java/com/anggrayudi/storage/access/StorageAccessManager.kt)
replacing `SimpleStorageHelper`. It requires **minSdk 26** and is compiled against **API 37
(Android 17)**; all operations need Kotlin coroutines.

The 2.x API keeps working during the 3.x cycle, but 2.x is closed for maintenance: no bugfix
releases will be cut from the 2.x branch, so fixes land in 3.x only. Everyone is encouraged to move
to v3 — see the [migration guide](MIGRATION.md).

## Why v3?

Three ideas replace most of the 2.x surface:

1. **One file type.** [`StorageFile`](storage/src/main/java/com/anggrayudi/storage/StorageFile.kt)
   wraps SAF's `DocumentFile`, MediaStore's `MediaFile`, and `java.io.File` behind one interface.
   You stop caring which world a file lives in.
2. **One operation vocabulary.** Every long-running operation is a main-safe `suspend` function
   returning a `TransferResult`, with an optional `Flow<TransferEvent>` form when you need the
   full event stream.
3. **One access entry point.** [`StorageAccessManager`](storage/src/main/java/com/anggrayudi/storage/access/StorageAccessManager.kt)
   turns SAF grants and pickers into plain suspend calls — no request codes, no
   `onActivityResult`, no callbacks.

## Getting a `StorageFile`

```kotlin
// From whatever you already have:
val a = StorageFile.from(context, uri)                  // SAF, file://, or MediaStore URI
val b = StorageFile.from(context, File("/storage/emulated/0/Download/movie.mp4"))
val c = StorageFile.fromPath(context, "/storage/emulated/0/Download/movie.mp4")
val d = StorageFile.fromPath(context, StoragePath(storageId = "AAAA-BBBB", basePath = "Download/movie.mp4"))
val e = StorageFile.fromPublicDirectory(context, PublicDirectory.DOWNLOADS, "movie.mp4")

// Conversions from the 2.x world:
val f = documentFile.toStorageFile(context)
val g = mediaFile.toStorageFile(context)
```

`StorageFile` holds its `Context` internally — no member function asks for one. Useful properties:
`name`, `mimeType`, `length`, `isDirectory`, `exists`, `lastModified`, `canRead`, `canWrite`,
`list()`, `child("sub/file.txt")`, `openInputStream()`, `openOutputStream()`.

`absolutePath` and `path` return **`null`** when the file has no resolvable physical path (v2
returned a confusing empty string). Escape hatches back to the underlying worlds:
`asDocumentFile()`, `asMediaFile()`, `asRawFile()`.

## Creating files & folders

```kotlin
val folder = StorageFile.fromPath(context, StoragePath.primary("Documents"))!!

val report = folder.createFile("report.txt", "text/plain")          // report (1).txt if taken
val invoice = folder.createFile("invoices/2026/q3.pdf", "application/pdf")   // parents created
val archive = folder.createFolder("archive")

report?.openOutputStream()?.use { it.write("hello".toByteArray()) }
```

`name` may carry subfolders; missing ones are created and existing ones are reused. The
[`CreateMode`](storage/src/main/java/com/anggrayudi/storage/file/CreateMode.kt) applies to the last
segment only — `CREATE_NEW` (default) keeps an existing file and creates `report (1).txt` beside it,
`REPLACE` overwrites it, `REUSE` returns it untouched. Both functions return `null` when the
receiver is not a writable folder, which is always the case for a MediaStore-backed `StorageFile`.

## Copy & move

```kotlin
lifecycleScope.launch {                       // main-safe: call from any dispatcher
  val result = file.copyTo(targetFolder) {    // this block is optional
    onConflict { ConflictResolution.REPLACE }
    onProgress { progressBar.progress = it.percent.toInt() }
    updateInterval = 250                      // ms between progress events
  }
  when (result) {
    is TransferResult.Success -> toast("Copied ${result.result.name}")
    is TransferResult.Skipped -> toast("Skipped — ${result.existingTarget?.name} already exists")
    is TransferResult.Failure -> Log.e(TAG, "${result.errorCode}", result.cause)
  }
}
```

The first progress event arrives one `updateInterval` after the transfer starts, so every event
carries measured numbers — and a transfer that finishes within one interval reports no progress at
all, just its result.

Several sources at once share one event stream and come back as a list:

```kotlin
val result = listOf(photos, notes).copyTo(backupFolder) {
  onConflict { ConflictResolution.REPLACE }
}
val copied: List<StorageFile>? = result.getOrNull()
```

When the destination is an existing file rather than a folder — a MediaStore entry, or a document
you just created — use `copyToFile` / `moveToFile`, which replace its content:

```kotlin
val entry = MediaStoreCompat.createDownload(context, FileDescription("report.pdf"))!!
file.copyToFile(entry.toStorageFile(context))
```

`moveTo` has the same shape. Folders are detected automatically — `copyTo` on a directory copies
recursively. All options live in the [`TransferSpec`](storage/src/main/java/com/anggrayudi/storage/transfer/TransferSpec.kt)
block: `updateInterval`, `checkAvailableSpace`, `skipEmptyFiles` (note: also skips empty
*folders*), `fileDescription` (rename in target), `deleteSourceOnSuccess` (zip only).

`TransferResult.Failure` carries a `TransferErrorCode`, an optional `message`, the causing
`Throwable`, and `partialStats` when something was transferred before the failure.

## Conflict resolution — a suspend lambda

The resolver is a `suspend` function. Show a dialog, await the answer, return it. No callback
classes, no `CoroutineScope` parameter, no `GlobalScope`:

```kotlin
val result = folder.copyTo(destination) {
  onConflict { conflict ->
    when (conflict) {
      is Conflict.TargetFolder ->             // whole folder exists; canMerge tells you if MERGE is possible
        if (conflict.canMerge) ConflictResolution.MERGE else ConflictResolution.CREATE_NEW
      is Conflict.TargetFile ->               // per-file conflict (also emitted during folder merges)
        withContext(Dispatchers.Main) { askUserDialog(conflict.target.name) }
    }
  }
}
```

Resolutions: `REPLACE`, `MERGE` (folders; falls back to `CREATE_NEW` on files), `CREATE_NEW`
(`report.pdf` → `report (1).pdf`), `SKIP`.

A top-level `SKIP` (single-file conflict, or the whole-folder conflict) ends the operation with a dedicated **`TransferResult.Skipped(existingTarget)`** — not a `Failure`, not a
`Success`. Per-file skips inside a folder merge keep the operation `Success` and are counted in
`TransferStats.filesSkipped`.

## Zip & unzip

```kotlin
val zipResult = listOf(folder, extraFile).zipTo(targetZipFile) {   // target must already exist
  deleteSourceOnSuccess = false
}
val unzipResult = zipFile.unzipTo(targetFolder) {
  onConflict { ConflictResolution.REPLACE }
}
```

## The Flow forms

When you need every event (e.g. WorkManager notifications), use the `*AsFlow` variants:

```kotlin
file.copyToAsFlow(targetFolder).collect { event ->
  when (event) {
    is TransferEvent.PhaseChanged -> Log.d(TAG, "phase: ${event.phase}")
    is TransferEvent.Progress -> notify(event.percent, event.bytesPerSecond)
    is TransferEvent.Completed<*> -> handle(event.result)   // exactly one terminal event
  }
}
```

Cancelling the collecting coroutine aborts the transfer. Also available:
`deleteRecursively()` (suspend) and `search(recursive, name, regex, mimeTypes, updateInterval)`
returning `Flow<List<StorageFile>>`.

## Storage access & pickers, without callbacks

Create a [`StorageAccessManager`](storage/src/main/java/com/anggrayudi/storage/access/StorageAccessManager.kt)
in `onCreate` (it registers Activity Result launchers), then everything is a suspend call:

```kotlin
class MainActivity : AppCompatActivity() {

  private lateinit var storageAccess: StorageAccessManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    storageAccess = StorageAccessManager(this)

    btnBackup.setOnClickListener {
      lifecycleScope.launch {
        // 1. Make sure we can write to Documents — asks the user through SAF only when needed
        when (val access = storageAccess.ensureAccess(StoragePath.primary("Documents"))) {
          is AccessResult.Granted -> myFile.copyTo(access.folder)
          is AccessResult.WrongRootSelected -> explainAndRetry(access.grantedRoot)
          AccessResult.CanceledByUser, AccessResult.PermissionDenied -> showError()
        }
      }
    }

    btnPick.setOnClickListener {
      lifecycleScope.launch {
        // 2. Pickers are one-liners; results are the contract result types
        val picked = storageAccess.pickFolder()
        if (picked is FolderPickerResult.Picked) {
          use(picked.folder.toStorageFile(this@MainActivity))
        }

        // 3. System Photo Picker — no permission, no SAF grant
        val media: List<StorageFile> = storageAccess.pickMedia()
      }
    }
  }
}
```

Also available: `pickFiles(allowMultiple, filterMimeTypes)`, `createFile(mimeType, fileName)`,
`requestStoragePermission()`.

### Remembering removable volumes

A `VolumeBookmark` remembers a location on an SD card or USB OTG drive and re-resolves it after
replug:

```kotlin
// After the user grants access once:
val bookmark = storageAccess.createBookmark(folder)   // persist it yourself

// Later — no UI when the volume ID is unchanged (mainline Android):
when (val result = storageAccess.resolveBookmark(bookmark)) {
  is BookmarkResult.Granted -> use(result.folder)     // persist result.bookmark: ID may have changed
  BookmarkResult.VolumeNotMounted -> askUserToPlugDriveIn()
  else -> showError()
}

// Optional (API 30+): react to drives being plugged in
storageAccess.volumeMountEvents().collect { volume -> maybeResolveBookmarks() }
```

If the ID changed (some OEM builds, ChromeOS), a volume with the same label triggers a single SAF
re-grant and `Granted` carries the updated bookmark. There are no built-in dialogs — you own the UX around
`WrongRootSelected` retries. If you prefer the old guided dialogs, the deprecated
`SimpleStorageHelper` still works — see [README-2.x.md](README-2.x.md).

## Jetpack Compose

All 2.x launchers still exist, plus the new Photo Picker one:

```kotlin
val mediaPicker = rememberLauncherForMediaPicker(maxItems = 5) { files: List<StorageFile> ->
  viewModel.onMediaPicked(files)
}
Button(onClick = { mediaPicker.launch() }) { Text("Pick photos") }
```

Others: `rememberLauncherForStoragePermission`, `rememberLauncherForStorageAccess`,
`rememberLauncherForFolderPicker`, `rememberLauncherForFilePicker`,
`rememberLauncherForFileCreation`.

## Terminology

![Alt text](art/terminology.png?raw=true "Simple Storage Terms")

### Other Terminology
* Storage Permission – related to [runtime permissions](https://developer.android.com/training/permissions/requesting)
* Storage Access – related to [URI permissions](https://developer.android.com/reference/android/content/ContentResolver#takePersistableUriPermission(android.net.Uri,%20int))


## Java Compatibility

Simple Storage is built in Kotlin and v3 is Kotlin-first, but the synchronous half of the library
is Java-callable: `StorageFile` and its `@JvmStatic` factories, metadata and folder navigation,
streams, and the `ActivityResultContract` pickers.

The long-running operations — copy, move, zip, unzip, search — are `suspend` functions and cannot
be called from Java, and neither can `StorageAccessManager`. Keep those call sites in Kotlin;
the two languages mix freely in one module. Staying on
[v1.5.6](https://github.com/anggrayudi/SimpleStorage/releases/tag/1.5.6) is the other option, but
that version is no longer maintained.

Follow this [documentation](JAVA_COMPATIBILITY.md) for the details and code samples.

## Using the 2.x API

The 2.x surface (`SimpleStorage`, `SimpleStorageHelper`, `DocumentFileCompat`, the `DocumentFile`
and `MediaFile` extensions) still ships inside 3.x as `@Deprecated` and is removed in 4.0. Its
documentation now lives in **[README-2.x.md](README-2.x.md)**, and [MIGRATION.md](MIGRATION.md) maps
each call to its v3 replacement.

## FAQ

Having trouble? Read the [Frequently Asked Questions](FAQ.md) or join the [Discussions](https://github.com/anggrayudi/SimpleStorage/discussions).

## Contributing

[CONTRIBUTING.md](CONTRIBUTING.md) covers the formatting rules (ktfmt, Google style), what makes a
comment worth keeping, and how the test suites are run.

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
