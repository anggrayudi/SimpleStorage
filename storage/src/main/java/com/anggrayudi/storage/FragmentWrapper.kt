package com.anggrayudi.storage

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.anggrayudi.storage.extension.startActivityForResultSafely

/**
 * Created on 18/08/20
 * @author Anggrayudi H
 */
internal class FragmentWrapper(private val fragment: Fragment) : ComponentWrapper {

    override val context: Context
        get() = fragment.requireContext()

    override fun startActivityForResult(requestCode: Int, intent: Intent) {
        fragment.startActivityForResultSafely(requestCode, intent)
    }

    override val activity: FragmentActivity
        get() = fragment.requireActivity()
}