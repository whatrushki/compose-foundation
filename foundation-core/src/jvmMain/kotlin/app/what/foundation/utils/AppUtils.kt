package app.what.foundation.utils

import java.io.File
import kotlin.system.exitProcess

actual val isDesktop: Boolean = true

actual class AppUtils {
    actual fun restart() {
        try {
            val processHandle = ProcessHandle.current()
            val processInfo = processHandle.info()
            val command = processInfo.command().orElse(null)
            val currentArgs = processInfo.arguments().orElse(emptyArray())

            if (command != null && !command.endsWith("java.exe", ignoreCase = true) && !command.endsWith("java", ignoreCase = true)) {
                val fullCmd = mutableListOf(command).apply { addAll(currentArgs) }
                ProcessBuilder(fullCmd).start()
                exitProcess(0)
            } else {
                val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
                val classpath = System.getProperty("java.class.path")
                val sunCommand = System.getProperty("sun.java.command")
                if (!sunCommand.isNullOrBlank()) {
                    val parts = sunCommand.trim().split(Regex("""\s+"""))
                    val mainClass = parts.firstOrNull() ?: "app.what.schedule.desktop.MainKt"
                    val args = parts.drop(1)
                    val cmd = mutableListOf(javaBin, "-cp", classpath, mainClass)
                    cmd.addAll(args)
                    ProcessBuilder(cmd).start()
                    exitProcess(0)
                }
            }
        } catch (_: Exception) {}
    }
}

