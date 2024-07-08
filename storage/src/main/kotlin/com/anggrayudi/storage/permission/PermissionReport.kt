package com.anggrayudi.storage.permission

/**
 * Created on 12/13/20
 * @author Anggrayudi H
 */
data class PermissionReport(
    val permission: String,
    val isGranted: Boolean,
    val deniedPermanently: Boolean
)