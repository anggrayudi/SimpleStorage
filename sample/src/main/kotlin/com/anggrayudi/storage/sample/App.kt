package com.anggrayudi.storage.sample

import androidx.multidex.MultiDexApplication
import timber.log.Timber

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version App, v 0.0.1 10/08/20 00.39 by Anggrayudi Hardiannico A.
 */
class App : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}