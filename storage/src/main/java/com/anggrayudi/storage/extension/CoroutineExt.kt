package com.anggrayudi.storage.extension

import kotlinx.coroutines.*

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version CoroutineExt.kt, v 0.0.1 04/04/20 18.26 by Anggrayudi Hardiannico A.
 */

fun startCoroutineTimer(
    delayMillis: Long = 0,
    repeatMillis: Long = 0,
    runActionOnUiThread: Boolean = false,
    action: () -> Unit
) = GlobalScope.launch {
    delay(delayMillis)
    if (repeatMillis > 0) {
        while (true) {
            if (runActionOnUiThread) {
                launchOnUiThread { action() }
            } else {
                action()
            }
            delay(repeatMillis)
        }
    } else {
        if (runActionOnUiThread) {
            launchOnUiThread { action() }
        } else {
            action()
        }
    }
}

fun launchOnUiThread(action: suspend CoroutineScope.() -> Unit) {
    GlobalScope.launch(Dispatchers.Main, block = action)
}