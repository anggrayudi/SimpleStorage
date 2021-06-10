package com.anggrayudi.storage

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity

/**
 * Created on 18/08/20
 * @author Anggrayudi H
 */
internal interface ComponentWrapper {

    val context: Context

    val activity: FragmentActivity

    fun startActivityForResult(intent: Intent, requestCode: Int): Boolean
}