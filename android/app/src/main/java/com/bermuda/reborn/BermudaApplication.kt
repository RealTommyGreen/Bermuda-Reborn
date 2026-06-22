package com.bermuda.reborn

import android.app.Application

class BermudaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        SaveDirectoryManager(this).exportCrashLogsToSelected()
    }
}
