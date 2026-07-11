# Migrating from SimpleStorage 2.x to 3.0

Version 3.0 introduces a single abstraction over Android's three file worlds and one vocabulary
for every long-running operation. The 2.x API keeps compiling throughout the 3.x cycle (parts of
it as `@Deprecated`), so you can migrate incrementally.

Platform changes:

| | 2.x | 3.0 |
|---|---|---|
| minSdk | 23 | **26** |
| compileSdk / targetSdk | 36 | **37** (Android 17) |
| AGP / Gradle (to build this repo) | 8.13 / 8.14 | 9.2 / 9.4 |

## The one-minute overview

```kotlin
// 2.x
ioScope.launch {
  file.copyFileTo(context, targetFolder,
    onConflict = object : SingleFileConflictCallback<DocumentFile>(uiScope) {
      override fun onFileConflict(destFile: DocumentFile, action: FileConflictAction) {
        action.confirmResolution(ConflictResolution.REPLACE)
      }
    }
  ).collect { result -> when (result) { /* 8 branches */ } }
}

// 3.0 — from any thread, no scope juggling
val result = file.copyTo(targetFolder) {
  onConflict { ConflictResolution.REPLACE }
  onProgress { progressBar.progress = it.percent.toInt() }
}
when (result) {
  is TransferResult.Success -> toast("Copied ${result.result.name}")
  is TransferResult.Failure -> log(result.errorCode, result.cause)
}
```

## API mapping

### Obtaining files

| 2.x | 3.0 |
|---|---|
| `DocumentFileCompat.fromUri(context, uri)` | `StorageFile.from(context, uri)` |
| `DocumentFileCompat.fromFile(context, file)` | `StorageFile.from(context, file)` |
| `DocumentFileCompat.fromFullPath(context, path)` | `StorageFile.fromPath(context, absolutePath)` |
| `DocumentFileCompat.fromSimplePath(context, storageId, basePath)` | `StorageFile.fromPath(context, StoragePath(storageId, basePath))` |
| `DocumentFileCompat.fromPublicFolder(context, type)` | `StorageFile.fromPublicDirectory(context, type)` |
| `MediaStoreCompat.fromMediaId(context, ...)` → `MediaFile` | `StorageFile.from(context, mediaUri)` |
| `FileFullPath(context, storageId, basePath)` | `StoragePath(storageId, basePath)` — no `Context` needed |

`StorageFile` holds its `Context` internally: none of its members ask for one. `absolutePath`
returns `null` (not `""`) when a physical path cannot be resolved. Escape hatches:
`asDocumentFile()`, `asMediaFile()`, `asRawFile()`.

### File operations

| 2.x | 3.0 one-shot | 3.0 Flow |
|---|---|---|
| `DocumentFile.copyFileTo(context, target, …)` | `StorageFile.copyTo(target) { }` | `copyToAsFlow(target)` |
| `DocumentFile.moveFileTo(context, target, …)` | `StorageFile.moveTo(target) { }` | `moveToAsFlow(target)` |
| `DocumentFile.copyFolderTo/moveFolderTo(…)` | same `copyTo`/`moveTo` — folders are detected | same |
| `List<DocumentFile>.compressToZip(context, zip, …)` | `List<StorageFile>.zipTo(zipFile) { }` | `zipToAsFlow(zipFile)` |
| `DocumentFile.decompressZip(context, folder, …)` | `StorageFile.unzipTo(folder) { }` | `unzipToAsFlow(folder)` |
| `DocumentFile.deleteRecursively(context)` | `StorageFile.deleteRecursively()` (suspend) | — |
| `DocumentFile.search(…)` | — | `StorageFile.search(…)` |

Options that used to be positional parameters (`updateInterval`, `skipEmptyFiles`,
`fileDescription`, space checking) now live in the `TransferSpec` lambda.

### Results

| 2.x | 3.0 |
|---|---|
| `SingleFileResult` / `SingleFolderResult` / `MultipleFilesResult` / `ZipCompressionResult` / `ZipDecompressionResult` | `TransferEvent` (`PhaseChanged`, `Progress`, `Completed`) |
| `...Result.Completed(result: Any)` + casting | `TransferResult.Success<StorageFile>` — typed |
| `...Result.Error(errorCode, message, cause)` | `TransferResult.Failure(errorCode, message, cause, partialStats)` |
| — (skipping silently closed the flow) | `TransferResult.Skipped(existingTarget)` for top-level skips; `TransferStats.filesSkipped` for per-file skips in merges (since 3.0.0-beta02) |
| `writeSpeed: Int` (bytes per update interval) | `Progress.bytesPerSecond: Long` |

### Conflict handling

| 2.x | 3.0 |
|---|---|
| `object : SingleFileConflictCallback<DocumentFile>(uiScope) { override fun onFileConflict(...) { action.confirmResolution(...) } }` | `onConflict { conflict -> ConflictResolution.REPLACE }` |
| `SingleFolderConflictCallback.onParentConflict/onContentConflict` | same single resolver — receives `Conflict.TargetFolder(canMerge)` first, then a `Conflict.TargetFile` per conflicting child |

The resolver is a `suspend` function: show a dialog with
`withContext(Dispatchers.Main) { … }` and simply return the answer. There is no `uiScope`, no
`GlobalScope` default, and no zombie-thread hazard.

### Storage access & pickers (Views)

| 2.x (`@Deprecated`) | 3.0 |
|---|---|
| `SimpleStorageHelper(activity)` + 4 callbacks + `onSaveInstanceState` + `onActivityResult` | `StorageAccessManager(activity)` — suspend functions, nothing to forward |
| `helper.requestStorageAccess()` + `onStorageAccessGranted` | `val access = manager.ensureAccess(StoragePath.primary("Documents"))` |
| `helper.openFolderPicker()` + `onFolderSelected` | `val result = manager.pickFolder()` |
| `helper.openFilePicker()` + `onFileSelected` | `val result = manager.pickFiles(allowMultiple = true)` |
| `helper.createFile(mimeType)` + `onFileCreated` | `val result = manager.createFile(mimeType)` |
| — | `manager.pickMedia()` — system Photo Picker, no permission needed |

`StorageAccessManager` has no built-in dialogs: `ensureAccess` returns `WrongRootSelected` and you
decide how to explain and retry. If you want ready-made dialogs, keep using `SimpleStorageHelper`
until you migrate.

### Compose

Existing `rememberLauncherFor*` composables are unchanged. New in 3.0:
`rememberLauncherForMediaPicker(maxItems) { files -> … }` for the system Photo Picker.

## Deprecation timeline

| Phase | What happens |
|---|---|
| 3.0.0-alpha | `SimpleStorage`, `SimpleStorageHelper`, and the picker/access callback interfaces are `@Deprecated` |
| 3.0.0-rc | `DocumentFile`/`MediaFile` operation extensions become `@Deprecated`, delegating to the v3 engine |
| 4.0 | Deprecated 2.x API is removed |
