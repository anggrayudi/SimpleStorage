# Frequently Asked Questions

### The app is not responding when copy, move, and other IO tasks
Read the quick documentation, Javadoc or go to the source code.
All functions annotated by `@WorkerThread` must be called in the background thread,
otherwise `@UiThread` must be called in the main thread.
If you ignore the annotation, your apps will lead to [ANR](https://developer.android.com/topic/performance/vitals/anr).

### How to open quick documentation?
Use keyboard shortcut Control + Q on Windows or Control + J on MacOS.
More shortcuts can be found on [Android Studio keyboard shortcuts](https://developer.android.com/studio/intro/keyboard-shortcuts).

### Why permission dialog is not shown on API 29+?
No runtime permission is required to be prompted on scoped storage.

### How to upload the `DocumentFile` and `MediaFile` to server?
Read the input stream with extension function `openInputStream()` and upload it as Base64 text.

### File path returns empty string
Getting file path (`getAbsolutePath()`, `getBasePath()`, etc.) may returns empty string if the `DocumentFile` is instance of `androidx.documentfile.provider.SingleDocumentFile`.
You you can convert it to `MediaFile` and use `MediaFile.absolutePath`. If this still does not work, then there's no other way.

### How to check if a folder/file is writable?
Use `isWritable()` extension function, because `DocumentFile.canWrite()` sometimes buggy on API 30.

### What is the target branch for pull requests?
Use branch `release/*` if exists, or use `master` instead.