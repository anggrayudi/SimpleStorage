package com.anggrayudi.storage.file

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.extension.fromTreeUri
import com.anggrayudi.storage.extension.trimFileSeparator
import java.io.File

/**
 * If you're using the following functions:
 * * [SimpleStorage.createFile]
 * * [SimpleStorage.openFilePicker]
 * * [SimpleStorage.openFolderPicker]
 * * [SimpleStorage.requestFullStorageAccess]
 *
 * then you can construct [FileFullPath]:
 * ```
 * // for API 30+
 * val fullPath = FileFullPath(context, StorageType.EXTERNAL, "DCIM")
 *
 * // for API 29-, define storage ID explicitly
 * val fullPath = FileFullPath(context, StorageId.PRIMARY, "DCIM")
 *
 * storageHelper.requestStorageAccess(initialPath = fullPath)
 * storageHelper.openFolderPicker(initialPath = fullPath)
 * ```
 *
 * @author Anggrayudi H
 */
public class FileFullPath : Parcelable {

  public val storageId: String
  public val basePath: String
  public lateinit var absolutePath: String
    private set

  public lateinit var simplePath: String
    private set

  /** @param fullPath can be simple path or absolute path */
  public constructor(context: Context, fullPath: String) {
    if (fullPath.startsWith('/')) {
      when {
        fullPath.startsWith(SimpleStorage.externalStoragePath) -> {
          storageId = StorageId.PRIMARY
          val rootPath = SimpleStorage.externalStoragePath
          basePath = fullPath.substringAfter(rootPath, "").trimFileSeparator()
          simplePath = "$storageId:$basePath"
          absolutePath = "$rootPath/$basePath".trimEnd('/')
        }
        fullPath.startsWith(context.dataDirectory.path) -> {
          storageId = StorageId.DATA
          val rootPath = context.dataDirectory.path
          basePath = fullPath.substringAfter(rootPath, "").trimFileSeparator()
          simplePath = "$storageId:$basePath"
          absolutePath = "$rootPath/$basePath".trimEnd('/')
        }
        else -> {
          storageId = fullPath.substringAfter("/storage/", "").substringBefore('/')
          if (storageId.isNotEmpty()) {
            basePath = fullPath.substringAfter("/storage/$storageId", "").trimFileSeparator()
            simplePath = "$storageId:$basePath"
            absolutePath = "/storage/$storageId/$basePath".trimEnd('/')
          } else {
            basePath = ""
            simplePath = ""
            absolutePath = ""
          }
        }
      }
    } else {
      simplePath = fullPath
      storageId = fullPath.substringBefore(':', "").substringAfterLast('/')
      basePath = fullPath.substringAfter(':', "").trimFileSeparator()
      absolutePath = buildAbsolutePath(context, storageId, basePath)
    }
  }

  public constructor(context: Context, storageId: String, basePath: String) {
    this.storageId = storageId
    this.basePath = basePath.trimFileSeparator()
    buildBaseAndAbsolutePaths(context)
  }

  @RequiresApi(30)
  public constructor(context: Context, storageType: StorageType, basePath: String = "") {
    this.basePath = basePath.trimFileSeparator()
    val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    storageId =
      when (storageType) {
        StorageType.SD_CARD ->
          sm.storageVolumes.firstOrNull { it.isRemovable }?.mediaStoreVolumeName.orEmpty()
        StorageType.EXTERNAL -> StorageId.PRIMARY
        StorageType.DATA -> StorageId.DATA
        else -> ""
      }
    buildBaseAndAbsolutePaths(context)
  }

  public constructor(context: Context, file: File) : this(context, file.path.orEmpty())

  private constructor(
    storageId: String,
    basePath: String,
    absolutePath: String,
    simplePath: String,
  ) {
    this.storageId = storageId
    this.basePath = basePath
    this.absolutePath = absolutePath
    this.simplePath = simplePath
  }

  private fun buildAbsolutePath(context: Context, storageId: String, basePath: String) =
    if (storageId.isEmpty()) ""
    else
      when (storageId) {
        StorageId.PRIMARY -> "${SimpleStorage.externalStoragePath}/$basePath".trimEnd('/')
        StorageId.DATA -> "${context.dataDirectory.path}/$basePath".trimEnd('/')
        else -> "/storage/$storageId/$basePath".trimEnd('/')
      }

  private fun buildBaseAndAbsolutePaths(context: Context) {
    absolutePath = buildAbsolutePath(context, storageId, basePath)
    simplePath = if (storageId.isEmpty()) "" else "$storageId:$basePath"
  }

  public val uri: Uri?
    get() =
      if (storageId.isEmpty()) null else DocumentFileCompat.createDocumentUri(storageId, basePath)

  public fun toDocumentUri(context: Context): Uri? {
    return context.fromTreeUri(uri ?: return null)?.uri
  }

  public val storageType: StorageType
    get() = StorageType.fromStorageId(storageId)

  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public fun checkIfStorageIdIsAccessibleInSafSelector() {
    if (storageId.isEmpty()) {
      throw IllegalArgumentException("Empty storage ID")
    }
    if (storageId == StorageId.DATA) {
      throw IllegalArgumentException(
        "Cannot use StorageType.DATA because it is never available in Storage Access Framework's folder selector."
      )
    }
  }

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeString(storageId)
    parcel.writeString(basePath)
    parcel.writeString(absolutePath)
    parcel.writeString(simplePath)
  }

  override fun describeContents(): Int = 0

  public companion object CREATOR : Parcelable.Creator<FileFullPath> {
    override fun createFromParcel(parcel: Parcel): FileFullPath {
      val storageId = parcel.readString().orEmpty()
      val basePath = parcel.readString().orEmpty()
      val absolutePath = parcel.readString().orEmpty()
      val simplePath = parcel.readString().orEmpty()
      return FileFullPath(storageId, basePath, absolutePath, simplePath)
    }

    override fun newArray(size: Int): Array<FileFullPath?> = arrayOfNulls(size)
  }
}
