package com.anggrayudi.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * Created on 18/08/20
 * @author Anggrayudi H
 */
internal class FragmentWrapper(private val fragment: Fragment) : ComponentWrapper {

    lateinit var storage: SimpleStorage
    var requestCode: Int? = null

    private val activityResultLauncher = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        requestCode?.run { storage.onActivityResult(this, it.resultCode, it.data) }
        requestCode = null
    }

    override val context: Context
        get() = fragment.requireContext()

    override val activity: FragmentActivity
        get() = fragment.requireActivity()

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