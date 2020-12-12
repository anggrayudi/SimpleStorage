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
class PermissionReport(
    val permission: String,
    val isGranted: Boolean,
    val deniedPermanently: Boolean
)