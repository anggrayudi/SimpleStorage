package com.anggrayudi.storage.permission

import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.karumi.dexter.Dexter

/**
 * [Dexter] cannot display consent dialog before requesting runtime permissions, thus we create our own permission request handler.
 *
 * Created on 12/13/20
 * @author Anggrayudi H
 */
class ActivityPermissionRequest private constructor(
    private val activity: FragmentActivity,
    private val permissions: Array<String>,
    private val requestCode: Int,
    private val callback: PermissionCallback
) : PermissionRequest {

    fun check() = apply {
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED) {
                callback.onDisplayConsentDialog(this@ActivityPermissionRequest)
                return@apply
            }
        }
        callback.onPermissionsChecked(
            PermissionResult(permissions.map {
                PermissionReport(it, isGranted = true, deniedPermanently = false)
            }), false
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
            PermissionReport(permission, isGranted, !isGranted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))
        }
        val blockedPermissions = reports.filter { it.deniedPermanently }
        if (blockedPermissions.isEmpty()) {
            callback.onPermissionsChecked(PermissionResult(reports), true)
        } else {
            callback.onShouldRedirectToSystemSettings(blockedPermissions)
        }
    }

    /**
     * If you override [PermissionCallback.onDisplayConsentDialog], then call this method in the
     * `onPositive` callback of the dialog.
     */
    override fun continueToPermissionRequest() {
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, permissions, requestCode)
                return
            }
        }
        callback.onPermissionsChecked(
            PermissionResult(permissions.map {
                PermissionReport(it, isGranted = true, deniedPermanently = false)
            }), false
        )
    }

    class Builder(private val activity: FragmentActivity, private val requestCode: Int) {

        private var permissions = emptySet<String>()

        private var callback: PermissionCallback? = null

        fun withPermissions(vararg permissions: String) = apply {
            this.permissions = permissions.toSet()
        }

        fun withCallback(callback: PermissionCallback) = apply {
            this.callback = callback
        }

        fun build() = ActivityPermissionRequest(activity, permissions.toTypedArray(), requestCode, callback!!)

        fun check() = build().check()
    }
}