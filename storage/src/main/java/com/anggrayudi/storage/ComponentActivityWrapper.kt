package com.anggrayudi.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Created on 18/08/20
 * @author Anggrayudi H
 */
internal class ComponentActivityWrapper(private val _activity: ComponentActivity) : ComponentWrapper {

    lateinit var storage: SimpleStorage
    var requestCode: Int? = null

    private val activityResultLauncher = _activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        requestCode?.run { storage.onActivityResult(this, it.resultCode, it.data) }
        requestCode = null
    }

    override val context: Context
        get() = _activity

    override val activity: ComponentActivity
        get() = _activity

    override fun startActivityForResult(intent: Intent, requestCode: Int): Boolean {
        return try {
            activityResultLauncher.launch(intent)
            this.requestCode = requestCode
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}