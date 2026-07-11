# SimpleStorage 3.0.0-beta03 — Usage Guide

> **Temporary document.** This guide covers the new v3 API while it is in beta. It will be merged
> into the main [README](README.md) and deleted when the stable 3.0.0 ships. The 2.x API is still
> documented in the main README and keeps working throughout the 3.x cycle.
> Migrating from 2.x? Read [MIGRATION.md](MIGRATION.md).

```groovy
implementation "com.anggrayudi:storage:3.0.0-beta03"

// For Jetpack Compose
implementation "com.anggrayudi:storage-compose:3.0.0-beta03"
```

Requirements: **minSdk 26**, compiled against **API 37 (Android 17)**. Kotlin coroutines required
for all operations.

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

Since `beta02`, a top-level `SKIP` (single-file conflict, or the whole-folder conflict) ends the
operation with a dedicated **`TransferResult.Skipped(existingTarget)`** — not a `Failure`, not a
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

### Remembering removable volumes (experimental)

Marked `@ExperimentalSimpleStorageApi` — opt in and expect changes. A `VolumeBookmark` remembers a
location on an SD card or USB OTG drive and re-resolves it after replug:

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
`SimpleStorageHelper` still works.

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

## Verified in this beta

All 23 on-device test cases pass on an API 37 emulator ([V3_TEST_CASES.md](V3_TEST_CASES.md)):
factories and metadata, copy/move (file & recursive folder), zip↔unzip round-trips, conflict
resolution including a suspending resolver (no deadlock), the MediaStore backend byte-for-byte,
Flow event shape, cancellation, and recursive search.

Not yet covered by automated tests: the interactive `StorageAccessManager` flows
(`ensureAccess`/pickers — they require driving SAF UI) and SD-card volumes. Treat those as
beta-quality and report anything odd.

## Feedback

This beta soaks for about a month before stable. Found a bug or an API rough edge? Please open an
issue: https://github.com/anggrayudi/SimpleStorage/issues
