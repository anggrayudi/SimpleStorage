# Java Compatibility

Kotlin is compatible with Java, meaning that Kotlin code is readable in Java.

## How to use?

Simple Storage contains utility functions stored in `object` class, e.g. `DocumentFileCompat` and `MediaStoreCompat`.
These classes contain only static functions.

Additionally, this library also has extension functions, e.g. `DocumentFileExtKt` and `FileExtKt`.
You can learn it [here](https://www.raywenderlich.com/10986797-extension-functions-and-properties-in-kotlin).

### Extension Functions

Common extension functions are stored in package `com.anggrayudi.storage.extension`. The others are in `com.anggrayudi.storage.file`.
You'll find that the most useful extension functions come from `DocumentFileExtKt` and `FileExtKt`. They are:
* `DocumentFile.getStorageId()` and `File.getStorageId()` → Get storage ID. Returns `primary` for external storage and something like `AAAA-BBBB` for SD card.
* `DocumentFile.getAbsolutePath()` → Get file's absolute path. Returns something like `/storage/AAAA-BBBB/Music/My Love.mp3`.
* `DocumentFile.copyFileTo()` and `File.copyFileTo()`
* `DocumentFile.search()` and `File.search()`, etc.

Suppose that you want to get storage ID of the file:

#### In Kotlin

```kotlin
val file = ...
val storageId = file.getStorageId(context)
```

#### In Java

```java
DocumentFile file = ...
String storageId = DocumentFileUtils.getStorageId(file, context);
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

## Sample Code

* More sample code in Java can be found in
[`JavaActivity`](https://github.com/anggrayudi/SimpleStorage/blob/master/sample/src/main/java/com/anggrayudi/storage/sample/activity/JavaActivity.java)
* Learn Kotlin on [Udacity](https://classroom.udacity.com/courses/ud9011). It's easy and free!