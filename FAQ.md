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
Getting file path (`getAbsolutePath()`, `getBasePath()`, etc.) may returns empty string if the `DocumentFile` is an instance of `androidx.documentfile.provider.SingleDocumentFile`. The following URIs are the example of `SingleDocumentFile`:
```
content://com.android.providers.downloads.documents/document/9
content://com.android.providers.media.documents/document/document%3A34
```
Here're some notes:
* Empty file path is not this library's limitation, but Android OS itself.
* To check if the file has guaranteed direct file path, extension function `DocumentFile.isTreeDocumentFile` will return `true`.
* You can convert `SingleDocumentFile` to `MediaFile` and use `MediaFile.absolutePath`. If this still does not work, then there's no other way.
* We don't recommend you to use direct file path for file management, such as reading, uploading it to the server, or importing it into your app.
Because Android OS wants us to use URI, thus direct file path is useless. So you need to use extension function `Uri.openInputStream()` for `DocumentFile` and `MediaFile`.

### How to check if a folder/file is writable?
Use `isWritable()` extension function, because `DocumentFile.canWrite()` sometimes buggy on API 30.

### What is the target branch for pull requests?
Use branch `release/*` if exists, or use `master` instead.
