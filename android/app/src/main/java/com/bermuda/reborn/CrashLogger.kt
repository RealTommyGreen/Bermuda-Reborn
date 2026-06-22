package com.bermuda.reborn

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object CrashLogger {
    private const val TAG = "CrashLogger"
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeUnhandledException(appContext, thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to write crash log: ${e.message}", e)
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun writeUnhandledException(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = Date()
        val fileName = "bermuda-crash-${fileTimestamp(timestamp)}.txt"
        val content = buildCrashLog(context, thread, throwable, timestamp)
        val result = SaveDirectoryManager(context).writeCrashLog(fileName, content)
        Log.e(TAG, "Crash log written to ${result.absolutePath}", throwable)
    }

    private fun buildCrashLog(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        timestamp: Date
    ): String {
        val stackTrace = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()

        return buildString {
            appendLine("Bermuda Reborn Crash Log")
            appendLine("Timestamp: ${displayTimestamp(timestamp)}")
            appendLine("Crash type: JVM uncaught exception")
            appendLine("Process: ${context.packageName} pid=${Process.myPid()}")
            appendLine("Thread: ${thread.name} id=${thread.id} state=${thread.state}")
            appendLine("App version: ${AppInfo.versionString(context)}")
            appendLine("Android: ${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}")
            appendLine("Product: ${Build.PRODUCT} device=${Build.DEVICE} hardware=${Build.HARDWARE}")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Build fingerprint: ${Build.FINGERPRINT}")
            appendLine()
            appendLine("Stacktrace:")
            append(stackTrace)
        }
    }

    private fun fileTimestamp(date: Date): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(date)

    private fun displayTimestamp(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(date)
}
