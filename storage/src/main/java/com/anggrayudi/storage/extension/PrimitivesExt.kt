package com.anggrayudi.storage.extension

/**
 * Created on 08/09/20
 *
 * @author Anggrayudi H
 */
public fun Int?.toBoolean(): Boolean = this != null && this > 0

public fun Boolean?.toInt(): Int = if (this == true) 1 else 0
