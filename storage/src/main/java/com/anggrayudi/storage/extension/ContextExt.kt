package com.anggrayudi.storage.extension

import android.content.Context

/**
 * Created on 17/08/20
 * @author Anggrayudi H
 */
fun Context.getAppDirectory(type: String? = null) = "${getExternalFilesDir(type)}"