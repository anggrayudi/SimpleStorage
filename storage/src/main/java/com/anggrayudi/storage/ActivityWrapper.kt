package com.anggrayudi.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity

/**
 * Created on 18/08/20
 * @author Anggrayudi H
 */
// TODO: 18/08/20 Should we use WeakReference for activity?
internal class ActivityWrapper(private val _activity: FragmentActivity) : ComponentWrapper {

    override val context: Context
        get() = _activity

    override val activity: FragmentActivity
        get() = _activity

    override fun startActivityForResult(intent: Intent, requestCode: Int): Boolean {
        return try {
            _activity.startActivityForResult(intent, requestCode)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}