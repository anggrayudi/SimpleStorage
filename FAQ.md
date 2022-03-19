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

### Which paths are writable with `java.io.File` on scoped storage?
Accessing files in scoped storage requires URI, but the following paths are exception and no storage permission needed:
* `/storage/emulated/0/Android/data/<your.app.package>`
* `/storage/<SD card ID>/Android/data/<your.app.package>`
* `/data/user/0/<your.app.package>` (API 24+)
* `/data/data/<your.app.package>` (API 23-)

### What is the target branch for pull requests?
Use branch `release/*` if exists, or use `master` instead.

### I have Java projects, but this library is built in Kotlin. How can I use it?
Kotlin is compatible with Java. You can read Kotlin functions as Java methods.
Read: [Java Compatibility](https://github.com/anggrayudi/SimpleStorage/blob/master/JAVA_COMPATIBILITY.md)

### Why does SimpleStorage use Kotlin?
The main reasons why this library really needs Kotlin:
* SimpleStorage requires thread suspension feature, but this feature is only provided by [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines).
* SimpleStorage contains many `String` & `Collection` manipulations, and Kotlin can overcome them in simple and easy ways.

Other reasons are:
* Kotlin can shorten and simplify your code.
* Writing code in Kotlin is faster, thus it saves your time and improves your productivity.
* [Google is Kotlin first](https://techcrunch.com/2019/05/07/kotlin-is-now-googles-preferred-language-for-android-app-development/) now.

### What are SimpleStorage alternatives?
You can't run from the fact that Google is Kotlin First now. Even Google has created [ModernStorage](https://github.com/google/modernstorage) (alternative for SimpleStorage) in Kotlin.
Learn Kotlin, or Google will leave you far behind.

**We have no intention to create Java version of SimpleStorage.** It will double our works and requires a lot of effort.
Keep in mind that we don't want to archive this library, even though Google has released the stable version of ModernStorage.
This library has rich features that Google may not covers, e.g. moving, copying, compressing and scanning folders.
