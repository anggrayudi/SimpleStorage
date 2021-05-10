# Java Compatibility

Kotlin is fully compatible with Java, meaning that all Kotlin code are readable in Java.

## How to use?

Simple Storage contains utility functions stored in `object` class, e.g. `DocumentFileCompat` and `MediaStoreCompat`.
These classes contain only static functions.

Additionally, this library also has extension functions, e.g. `DocumentFileExtKt` and `FileExtKt`.
You can learn it [here](https://www.raywenderlich.com/10986797-extension-functions-and-properties-in-kotlin).

### Extension Functions

Common extension functions are stored in package `com.anggrayudi.storage.extension`. The others are in `com.anggrayudi.storage.file`.
You'll find that the most useful extension functions come from `DocumentFileExtKt` and `FileExtKt`. They are:
* `DocumentFile.storageId` and `File.storageId` → Get storage ID. Returns `primary` for external storage and something like `AAAA-BBBB` for SD card.
* `DocumentFile.absolutePath` → Get file's absolute path. Returns something like `/storage/AAAA-BBBB/Music/My Love.mp3`.
* `DocumentFile.copyFileTo()` and `File.copyFileTo()`
* `DocumentFile.search()` and `File.search()`, etc.

Suppose that you want to get storage ID of the file:

#### In Kotlin

```kotlin
val file = ...
val storageId = file.storageId
```

#### In Java

```java
DocumentFile file = ...
// Prior to 0.4.2:
String storageId = DocumentFileExtKt.getStorageId(file);
// 0.4.2 and higher
String storageId = DocumentFileUtils.getStorageId(file);
```

All extension functions work like static methods in Java. Note that since `0.4.2`,
their class names are renamed from using suffix `ExtKt` to `Utils`.

### Utility Functions

I will refer to utility functions stored in Kotlin `object` class so you can understand it easily.
You can find the most useful utility functions in `DocumentFileCompat` and `MediaStoreCompat`.

Suppose that I want to get file from SD card with the following simple path: `AAAA-BBBB:Music/My Love.mp3`.
BTW, `AAAA-BBBB` is the SD card's storage ID for this example.

#### In Kotlin

```kotlin
val file = DocumentFileCompat.fromSimplePath(context, "AAAA-BBBB", "Music/My Love.mp3")
```

#### In Java

```java
DocumentFile file = DocumentFileCompat.INSTANCE.fromSimplePath(context, "AAAA-BBBB", "Music/My Love.mp3");
```

In Java, you need to append `INSTANCE` after the utility class name.
Anyway, if the function is annotated with `@JvmStatic`, you don't need to append `INSTANCE`.
Just go to the source code to check whether it has the annotation.

## Request Storage Access

Although user has granted read and write permissions during runtime, your app may still does not have full access to the storage,
thus you cannot search, move and copy files. You can check whether you have the storage access via `SimpleStorage.hasStorageAccess()`.

To enable full storage access, you need to open SAF and let user grant URI permissions for read and write access. This library provides you
an helper class named `SimpleStorage` to ease the request process:

```java
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_STORAGE_ACCESS = 8;

    private SimpleStorage storage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupSimpleStorage(savedInstanceState);
        findViewById(R.id.btnRequestStorageAccess).setOnClickListener(v ->
                storage.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS, StorageType.EXTERNAL));
    }

    private void setupSimpleStorage(Bundle savedInstanceState) {
        storage = new SimpleStorage(this, savedInstanceState);
        storage.setStorageAccessCallback(new StorageAccessCallback() {
            @Override
            public void onRootPathNotSelected(int requestCode, @NotNull String rootPath, @NotNull StorageType rootStorageType, @NotNull Uri uri) {
                /*
                Show dialog to tell user that the root path of storage is not selected.
                When user tap OK button, request storage access again.
                 */
            }

            @Override
            public void onStoragePermissionDenied(int requestCode) {
                /*
                Request runtime permissions for Manifest.permission.WRITE_EXTERNAL_STORAGE
                and Manifest.permission.READ_EXTERNAL_STORAGE
                */
            }

            @Override
            public void onRootPathPermissionGranted(int requestCode, @NotNull DocumentFile root) {
                Toast.makeText(getBaseContext(), "Storage access has been granted for " + DocumentFileExtKt.getStorageId(root), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCanceledByUser(int requestCode) {
                Toast.makeText(getBaseContext(), "Canceled by user", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        storage.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        storage.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        storage.onRestoreInstanceState(savedInstanceState);
    }
}
```

We also provide [`SimpleStorageHelper`](https://github.com/anggrayudi/SimpleStorage#simplestoragehelper) to simplify the following actions:
* Request storage access
* Handle file picker
* Handle folder picker