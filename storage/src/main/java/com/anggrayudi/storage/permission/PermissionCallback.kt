/*
 * DANA.id
 * PT. Espay Debit Indonesia Koe.
 * Copyright (c) 2017-2020 All Rights Reserved.
 */

package com.anggrayudi.storage.permission

/**
 * Created on 12/13/20
 * @author Anggrayudi H
 */
interface PermissionCallback {

    @JvmDefault
    fun onDisplayConsentDialog(request: PermissionRequest) {
        request.continueToPermissionRequest()
    }

    fun onPermissionsChecked(result: PermissionResult)

    @JvmDefault
    fun onShouldRedirectToSystemSettings(blockedPermissions: List<PermissionReport>) {
        // default implementation
    }
}