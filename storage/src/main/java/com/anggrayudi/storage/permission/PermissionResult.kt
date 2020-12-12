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
class PermissionResult(val permissions: List<PermissionReport>) {

    val areAllPermissionsGranted: Boolean
        get() = permissions.all { it.isGranted }
}