package com.anggrayudi.storage.permission

/**
 * Created on 12/13/20
 *
 * @author Anggrayudi H
 */
public class PermissionResult(public val permissions: List<PermissionReport>) {

  public val areAllPermissionsGranted: Boolean
    get() = permissions.all { it.isGranted }
}
