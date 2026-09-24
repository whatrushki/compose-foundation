package app.what.foundation.services

import android.content.Context
import android.util.Log
import java.io.File

class AndroidAppLogger(context: Context) : AppLogger(
    logFilePath = "${context.applicationContext.filesDir.absolutePath}/audit_logs.txt"
) {
    private val logFileRef by lazy { File(logFilePath ?: "") }

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        super.log(level, tag, message, throwable)
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARNING -> Log.WARN
            else -> Log.ERROR
        }
        val fullMessage = "$message " + (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")
        Log.println(priority, tag, fullMessage)

        try {
            if (logFileRef.length() > 512 * 1024) {
                logFileRef.writeText("")
            }
            logFileRef.appendText("[${level.name}] [$tag] $fullMessage\n")
        } catch (_: Exception) {}
    }
}

val AppLogger.logFile: File
    get() = File(logFilePath ?: "")

fun AppLogger.Companion.initialize(context: Context) {
    initialize(AndroidAppLogger(context))
}
