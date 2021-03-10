package com.anggrayudi.storage.permission

import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.anggrayudi.storage.ActivityWrapper
import com.anggrayudi.storage.ComponentWrapper
import com.anggrayudi.storage.FragmentWrapper

/**
 * Dexter cannot display consent dialog before requesting runtime permissions, thus we create our own permission request handler.
 *
 * Created on 12/13/20
 * @author Anggrayudi H
 */
class PermissionRequest private constructor(
    private val wrapper: ComponentWrapper,
    private val permissions: Array<String>,
    private val requestCode: Int,
    private val callback: PermissionCallback
) {

    fun check() = apply {
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(wrapper.context, it) != PackageManager.PERMISSION_GRANTED) {
                callback.onDisplayConsentDialog(this@PermissionRequest)
                return@apply
            }
        }
        callback.onPermissionsChecked(
            PermissionResult(permissions.map {
                PermissionReport(it, isGranted = true, deniedPermanently = false)
            })
        )
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode != this.requestCode) {
            return
        }

        val reports = permissions.mapIndexed { index, permission ->
            val isGranted = grantResults[index] == PackageManager.PERMISSION_GRANTED
            PermissionReport(permission, isGranted, !isGranted && !ActivityCompat.shouldShowRequestPermissionRationale(wrapper.activity, permission))
        }
        val blockedPermissions = reports.filter { it.deniedPermanently }
        if (blockedPermissions.isEmpty()) {
            callback.onPermissionsChecked(PermissionResult(reports))
        } else {
            callback.onShouldRedirectToSystemSettings(blockedPermissions)
        }
    }

    /**
     * If you override [PermissionCallback.onDisplayConsentDialog], then call this method in the
     * `onPositive` callback of the dialog.
     */
    fun continueToPermissionRequest() {
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(wrapper.context, it) != PackageManager.PERMISSION_GRANTED) {
                wrapper.checkPermissions(permissions, requestCode)
                return
            }
        }
        callback.onPermissionsChecked(
            PermissionResult(permissions.map {
                PermissionReport(it, isGranted = true, deniedPermanently = false)
            })
        )
    }

    class Builder private constructor(private val wrapper: ComponentWrapper) {

        private var permissions = emptySet<String>()

        private var callback: PermissionCallback? = null

        private var requestCode: Int = 0

        constructor(activity: FragmentActivity) : this(ActivityWrapper(activity))

        constructor(fragment: Fragment) : this(FragmentWrapper(fragment))

        fun withPermissions(vararg permissions: String) = apply {
            this.permissions = permissions.toSet()
        }

        fun withCallback(callback: PermissionCallback) = apply {
            this.callback = callback
        }

        fun withRequestCode(requestCode: Int) = apply {
            this.requestCode = requestCode
        }

        fun build() = PermissionRequest(wrapper, permissions.toTypedArray(), requestCode, callback!!)

        fun check() = build().check()
    }
}