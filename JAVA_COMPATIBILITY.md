# Java Compatibility

Simple Storage is written in Kotlin, and v3 is Kotlin-first. Java can still do the synchronous
half of the library — wrapping files, reading metadata, navigating folders, opening streams, and
running the pickers. The long-running operations (copy, move, zip, unzip, search) are `suspend`
functions and cannot be called from Java at all.

If your project is Java-only and you need those operations, keep those call sites in Kotlin (the
two languages mix freely in one module). Staying on
[v1.5.6](https://github.com/anggrayudi/SimpleStorage/releases/tag/1.5.6) is the other option, but
that version is no longer maintained.

## `StorageFile` from Java

The factories are `@JvmStatic`, so they are plain static methods. Every one of them returns `null`
when the location is not accessible.

```java
StorageFile fromUri = StorageFile.from(context, uri);
StorageFile fromFile = StorageFile.from(context, new File("/storage/emulated/0/Download/movie.mp4"));
StorageFile fromPath = StorageFile.fromPath(context, StoragePath.primary("Download"));
StorageFile fromAbsolute = StorageFile.fromPath(context, "/storage/emulated/0/Download/movie.mp4");
StorageFile inDownloads =
    StorageFile.fromPublicDirectory(context, PublicDirectory.DOWNLOADS, "movie.mp4", false);
```

`StoragePath` is a data class with `@JvmStatic` factories: `StoragePath.primary(basePath)`,
`StoragePath.fromAbsolutePath(context, fullPath)`, and `StoragePath.from(context, file)`.

## Metadata, navigation, and streams

```java
String name = file.getName();
long size = file.getLength();
String mimeType = file.getMimeType();
boolean isDirectory = file.isDirectory();
String absolutePath = file.getAbsolutePath();   // null when the file has no physical path
StoragePath path = file.getPath();

StorageFile report = folder.createFile("report.txt", "text/plain", CreateMode.CREATE_NEW);
StorageFile archive = folder.createFolder("archive", CreateMode.CREATE_NEW);

List<StorageFile> children = file.list();
StorageFile child = file.child("docs/report.pdf", false);
try (InputStream input = file.openInputStream()) {
  // read
}
file.delete();

DocumentFile documentFile = file.asDocumentFile();   // escape hatch, also asMediaFile()/asRawFile()
```

Two things to watch:

* Kotlin's `exists` and `canWrite` properties compile to `getExists()` and `getCanWrite()`, not
  `exists()` / `canWrite()`.
* `child()`, `openOutputStream()`, `createFile()` and `createFolder()` are interface methods, so
  their Kotlin default arguments do not reach Java — pass every argument explicitly.

## Pickers and storage access

`StorageAccessManager` is suspend-based and therefore Kotlin-only, but the `ActivityResultContract`
classes it is built on are ordinary Java-callable classes, and their `Options` constructors carry
`@JvmOverloads`:

```java
ActivityResultLauncher<OpenFolderPickerContract.Options> folderPicker =
    registerForActivityResult(
        new OpenFolderPickerContract(this),
        result -> {
          if (result instanceof FolderPickerResult.Picked) {
            DocumentFile folder = ((FolderPickerResult.Picked) result).getFolder();
          } else if (result instanceof FolderPickerResult.AccessDenied) {
            // ask again
          }
        });

folderPicker.launch(new OpenFolderPickerContract.Options());
```

The same shape works for `OpenFilePickerContract`, `FileCreationContract`,
`RequestStorageAccessContract`, and `StoragePermissionContract`. Results are sealed classes, so
`instanceof` covers every case, and the object cases are singletons — e.g.
`FolderPickerResult.CanceledByUser.INSTANCE`.

## The 2.x API from Java

The 2.x surface still ships in 3.x as deprecated API and is removed in 4.0. From Java, extension
functions appear as static methods on `*Utils` classes, and `object` singletons need `INSTANCE`
unless the member is `@JvmStatic`:

```java
String storageId = DocumentFileUtils.getStorageId(file, context);
DocumentFile file =
    DocumentFileCompat.INSTANCE.fromSimplePath(context, "AAAA-BBBB", "Music/My Love.mp3");
```

Its long-running operations were already Kotlin-only, so nothing new is lost by migrating to
`StorageFile`.

## Sample code

* [`JavaActivity`](https://github.com/anggrayudi/SimpleStorage/blob/master/sample/src/main/java/com/anggrayudi/storage/sample/activity/JavaActivity.java)
  demonstrates the 2.x API from Java.
* The v3 API is documented in the [README](README.md); the Kotlin snippets there translate to Java
  exactly as shown above for everything that is not `suspend`.
