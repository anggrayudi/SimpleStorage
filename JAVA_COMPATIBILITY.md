# Java Compatibility

Kotlin is fully compatible with Java, meaning that all Kotlin code are readable in Java.

## Add Kotlin dependency

Before using Simple Storage, make sure you have added Kotlin dependencies:

In your `build.gradle` of project level, add Kotlin classpath:

```gradle
buildscript {
    // add this line
    ext.kotlin_version = '1.4.21'

    dependencies {
        classpath 'com.android.tools.build:gradle:x.x.x'
        // add Kotlin classpath
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}

allprojects {
    // Support @JvmDefault
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).all {
        kotlinOptions {
            freeCompilerArgs = ['-Xjvm-default=all', '-Xopt-in=kotlin.RequiresOptIn']
            jvmTarget = '1.8'
            includeRuntime = true
        }
    }
}
```

And for your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.anggrayudi:storage:x.x.x'
    // add Kotlin standard library
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version"
}
```

Now you are ready to use Simple Storage.

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
* `DocumentFile.copyFile()` and `File.copyFile()`
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
String storageId = DocumentFileExtKt.getStorageId(file);
```

All extension functions work like static methods in Java.

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

Although user has granted read and write permissions during runtime, your app may still does not
have full access to the storage, thus you cannot search, move and copy files. To enable full disk access,
you need to open SAF and let user grant URI permissions for read and write access. This library provides you
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
            public void onCancelledByUser(int requestCode) {
                Toast.makeText(getBaseContext(), "Cancelled by user", Toast.LENGTH_SHORT).show();
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