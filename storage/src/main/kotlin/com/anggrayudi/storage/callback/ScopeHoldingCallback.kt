package com.anggrayudi.storage.callback

import kotlinx.coroutines.CoroutineScope

interface ScopeHoldingCallback {
    val uiScope: CoroutineScope
}