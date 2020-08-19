package com.anggrayudi.storage

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.anggrayudi.storage.extension.startActivityForResultSafely

/**
 * Created on 18/08/20
 * @author Anggrayudi H
 */
// TODO: 18/08/20 Should we use WeakReference for activity?
internal class ActivityWrapper(private val _activity: FragmentActivity) : ComponentWrapper {

    override val context: Context
        get() = _activity

    override fun startActivityForResult(requestCode: Int, intent: Intent) {
        _activity.startActivityForResultSafely(requestCode, intent)
    }

    override val activity: FragmentActivity
        get() = _activity
}