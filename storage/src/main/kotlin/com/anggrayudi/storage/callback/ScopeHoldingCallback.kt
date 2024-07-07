package com.anggrayudi.storage.callback

import com.anggrayudi.storage.extension.postToUi
import kotlinx.coroutines.CoroutineScope

interface ScopeHoldingCallback {
    val uiScope: CoroutineScope

    fun postToUiScope(action: () -> Unit) {
        uiScope.postToUi(action)
    }
}