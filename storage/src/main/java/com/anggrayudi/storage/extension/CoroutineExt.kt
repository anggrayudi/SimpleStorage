package com.anggrayudi.storage.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version CoroutineExt.kt, v 0.0.1 04/04/20 18.26 by Anggrayudi Hardiannico A.
 */

inline fun startCoroutineTimer(
    delayMillis: Long = 0,
    repeatMillis: Long = 0,
    runActionOnUiThread: Boolean = false,
    crossinline action: () -> Unit
) = GlobalScope.launch {
    delay(delayMillis)
    if (repeatMillis > 0) {
        while (true) {
            if (runActionOnUiThread) {
                GlobalScope.launch(Dispatchers.Main) { action() }
            } else {
                action()
            }
            delay(repeatMillis)
        }
    } else {
        if (runActionOnUiThread) {
            GlobalScope.launch(Dispatchers.Main) { action() }
        } else {
            action()
        }
    }
}