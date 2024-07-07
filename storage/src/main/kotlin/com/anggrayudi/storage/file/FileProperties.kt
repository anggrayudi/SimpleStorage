package com.anggrayudi.storage.file

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.UiThread
import com.anggrayudi.storage.callback.ScopeHoldingCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import java.util.Date

/**
 * Created on 03/06/21
 * @author Anggrayudi H
 */
data class FileProperties(
    var name: String = "",
    var location: String = "",
    var size: Long = 0,
    var isFolder: Boolean = false,
    var folders: Int = 0,
    var files: Int = 0,
    var emptyFiles: Int = 0,
    var emptyFolders: Int = 0,
    var isVirtual: Boolean = false,
    var lastModified: Date? = null
) {
    fun formattedSize(context: Context): String = Formatter.formatFileSize(context, size)

    abstract class CalculationCallback(
        val updateInterval: Long = 500, // 500ms
        @OptIn(DelicateCoroutinesApi::class)
        override val uiScope: CoroutineScope = GlobalScope
    ) : ScopeHoldingCallback {

        @UiThread
        open fun onUpdate(properties: FileProperties) {
            // default implementation
        }

        @UiThread
        abstract fun onComplete(properties: FileProperties)

        @UiThread
        open fun onCanceled(properties: FileProperties) {
            // default implementation
        }

        @UiThread
        open fun onError() {
            // default implementation
        }
    }
}