package com.bermuda.reborn

import android.content.Context
import android.os.Build

object AppInfo {
    @Suppress("DEPRECATION")
    fun versionString(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = info.versionName ?: "unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            "$versionName ($versionCode)"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
