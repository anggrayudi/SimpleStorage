package com.anggrayudi.storage.transfer

import com.anggrayudi.storage.StorageFile

/**
 * Raised while transferring when something already exists in the destination.
 *
 * @author Anggrayudi H
 */
sealed interface Conflict {
  /** What already exists in the destination. */
  val target: StorageFile

  /** A file with the same name already exists in the destination folder. */
  data class TargetFile(override val target: StorageFile) : Conflict

  /**
   * A folder with the same name already exists in the destination.
   *
   * @param canMerge `false` when the destination cannot be merged, e.g. a file occupies the
   *   folder's name; [ConflictResolution.MERGE] is then treated as [ConflictResolution.CREATE_NEW]
   */
  data class TargetFolder(override val target: StorageFile, val canMerge: Boolean) : Conflict
}

enum class ConflictResolution {
  /** Delete the target, then transfer. */
  REPLACE,

  /** Folders only: write into the existing folder. On files this falls back to [CREATE_NEW]. */
  MERGE,

  /** Keep both: `ABC.zip` already exists, so create `ABC (1).zip`. */
  CREATE_NEW,

  /** Leave the target alone and skip this source. */
  SKIP,
}

/**
 * Decides what to do when a [Conflict] is found. Being a suspend function, it can freely switch to
 * the main dispatcher and show a dialog — no extra [kotlinx.coroutines.CoroutineScope] is needed:
 * ```kotlin
 * onConflict { conflict ->
 *   withContext(Dispatchers.Main) { askUser(conflict.target.name) }
 * }
 * ```
 */
fun interface ConflictResolver {
  suspend fun resolve(conflict: Conflict): ConflictResolution
}
