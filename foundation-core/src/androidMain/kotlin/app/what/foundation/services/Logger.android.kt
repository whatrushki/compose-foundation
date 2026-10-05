package app.what.foundation.services

import android.content.Context
import android.util.Log
import java.io.File

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AndroidAppLogger(context: Context) : AppLogger(
    logFilePath = "${context.applicationContext.filesDir.absolutePath}/audit_logs.txt"
) {
    private val logFileRef by lazy { File(logFilePath ?: "") }
    private val oldLogFileRef by lazy { File("${logFilePath ?: ""}.old") }
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        ioScope.launch {
            try {
                if (logFileRef.length() > 512 * 1024) {
                    if (oldLogFileRef.exists()) oldLogFileRef.delete()
                    logFileRef.renameTo(oldLogFileRef)
                }
                logFileRef.appendText("[${level.name}] [$tag] $fullMessage\n")
            } catch (_: Exception) {}
        }
    }
}

val AppLogger.logFile: File
    get() = File(logFilePath ?: "")

fun AppLogger.Companion.initialize(context: Context) {
    initialize(AndroidAppLogger(context))
}
