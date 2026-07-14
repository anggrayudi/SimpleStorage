package com.anggrayudi.storage.permission

/**
 * Created on 12/13/20
 *
 * @author Anggrayudi H
 */
public class PermissionReport(
  public val permission: String,
  public val isGranted: Boolean,
  public val deniedPermanently: Boolean,
)
