package com.anggrayudi.storage.permission

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Created on 12/13/20
 *
 * @author Anggrayudi H
 */
public class ActivityPermissionRequest
private constructor(
  private val activity: Activity,
  private val permissions: Array<String>,
  private val requestCode: Int?,
  private val callback: PermissionCallback,
) : PermissionRequest {

  private val launcher =
    if (activity is ComponentActivity)
      activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onRequestPermissionsResult(it)
      }
    else null

  override fun check() {
    if (
      permissions.all {
        ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
      }
    ) {
      callback.onPermissionsChecked(
        PermissionResult(
          permissions.map { PermissionReport(it, isGranted = true, deniedPermanently = false) }
        ),
        false,
      )
    } else {
      callback.onDisplayConsentDialog(this)
    }
  }

  public fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray,
  ) {
    if (requestCode != this.requestCode) {
      return
    }

    if (launcher != null) {
      throw IllegalAccessException("Do not call onRequestPermissionsResult() in ComponentActivity")
    }

    val reports =
      permissions.mapIndexed { index, permission ->
        val isGranted = grantResults[index] == PackageManager.PERMISSION_GRANTED
        PermissionReport(
          permission,
          isGranted,
          !isGranted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission),
        )
      }
    reportResult(reports)
  }

  private fun onRequestPermissionsResult(result: Map<String, Boolean>) {
    val reports =
      result.map {
        PermissionReport(
          it.key,
          it.value,
          !it.value && !ActivityCompat.shouldShowRequestPermissionRationale(activity, it.key),
        )
      }
    reportResult(reports)
  }

  private fun reportResult(reports: List<PermissionReport>) {
    if (reports.isEmpty()) {
      callback.onPermissionRequestInterrupted(permissions)
      return
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
        if (launcher != null) {
          launcher.launch(permissions)
        } else {
          ActivityCompat.requestPermissions(
            activity,
            permissions,
            requestCode ?: throw IllegalStateException("Request code hasn't been set yet"),
          )
        }
        return
      }
    }
    callback.onPermissionsChecked(
      PermissionResult(
        permissions.map { PermissionReport(it, isGranted = true, deniedPermanently = false) }
      ),
      false,
    )
  }

  public class Builder {

    private val activity: Activity
    private val requestCode: Int?

    public constructor(activity: Activity, requestCode: Int) {
      this.activity = activity
      this.requestCode = requestCode
    }

    public constructor(activity: ComponentActivity) {
      this.activity = activity
      this.requestCode = null
    }

    private var permissions = emptySet<String>()

    private var callback: PermissionCallback? = null

    public fun withPermissions(vararg permissions: String): Builder = apply {
      this.permissions = permissions.toSet()
    }

    public fun withCallback(callback: PermissionCallback): Builder = apply { this.callback = callback }

    public fun build(): ActivityPermissionRequest =
      ActivityPermissionRequest(activity, permissions.toTypedArray(), requestCode, callback!!)

    public fun check(): Unit = build().check()
  }
}
