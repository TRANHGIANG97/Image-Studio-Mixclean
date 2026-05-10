package com.abizer_r.quickedit.utils

import android.Manifest
import android.os.Build

object PermissionUtils {

    fun getInternalStoragePermissions(): Array<String> = emptyArray()

    fun isAndroidQAndAbove(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
