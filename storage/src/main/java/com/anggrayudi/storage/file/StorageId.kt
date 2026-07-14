package com.anggrayudi.storage.file

import android.content.Context
import android.os.Environment
import androidx.annotation.RestrictTo

/**
 * Created on 03/06/21
 *
 * @author Anggrayudi H
 */
public object StorageId {

  /** For files under [Environment.getExternalStorageDirectory] */
  public const val PRIMARY: String = "primary"

  /**
   * For files under [Context.getFilesDir] or [Context.getDataDir]. It is not really a storage ID,
   * and can't be used in file tree URI.
   */
  public const val DATA: String = "data"

  /** For `/storage/emulated/0/Documents` It is only exists on API 29- */
  @RestrictTo(RestrictTo.Scope.LIBRARY) public const val HOME: String = "home"
}
