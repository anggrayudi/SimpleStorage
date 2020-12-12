package com.anggrayudi.storage

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * Created on 18/08/20
 * @author Anggrayudi H
 */
internal class FragmentWrapper(private val fragment: Fragment) : ComponentWrapper {

    override val context: Context
        get() = fragment.requireContext()

    override val activity: FragmentActivity
        get() = fragment.requireActivity()

    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        fragment.startActivityForResult(intent, requestCode)
    }

    override fun checkPermissions(permissions: Array<String>, requestCode: Int) {
        fragment.requestPermissions(permissions, requestCode)
    }
}