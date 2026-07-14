package com.anggrayudi.storage.permission

/**
 * Created on 12/13/20
 *
 * @author Anggrayudi H
 */
public interface PermissionCallback {

  public fun onDisplayConsentDialog(request: PermissionRequest) {
    request.continueToPermissionRequest()
  }

  /** @param fromSystemDialog true if users agreed/denied the permission from the system dialog. */
  public fun onPermissionsChecked(result: PermissionResult, fromSystemDialog: Boolean)

  public fun onShouldRedirectToSystemSettings(blockedPermissions: List<PermissionReport>) {
    // default implementation
  }

  /** Triggered when you request another permission when a permission request dialog is showing. */
  public fun onPermissionRequestInterrupted(permissions: Array<String>) {
    // default implementation
  }
}
