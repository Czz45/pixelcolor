package com.example.pixelcolor

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PixelColorApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Cache filesDir now that the base Context is attached. Do NOT do this in the
        // constructor/init block — filesDir is still null there and throws NPE at launch.
        appFilesDir = filesDir

        // Clear previous entry log for fresh session (crash logs are timestamped and kept)
        try { File(filesDir, "entry_log.log").delete() } catch (_: Exception) {}

        // Global uncaught exception handler — covers main thread and uncaught native/crash
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Global coroutine exception handler scope — captures unexpected coroutine failures
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + crashHandler)
    }

    companion object {
        var appScope: CoroutineScope? = null
            private set

        /**
         * Attach to a coroutine launch to record unexpected exceptions as fatal crashes.
         * Usage: `scope.launch(crashHandler) { ... }`
         * Only use on coroutines where an exception genuinely means something went wrong
         * (e.g. the game loop) — NOT on coroutines that already handle benign errors.
         */
        val crashHandler = CoroutineExceptionHandler { _, throwable ->
            logCrash(throwable)
        }

        /** filesDir cached in onCreate (after the base Context is attached); null until then */
        private var appFilesDir: File? = null

        /** Write a fatal crash log manually from anywhere. */
        fun logCrash(throwable: Throwable) {
            val dir = appFilesDir
            if (dir == null) {
                Log.e("PixelColor", "logCrash: appFilesDir not ready", throwable)
                return
            }
            writeCrashLogToFile(Thread.currentThread(), throwable, dir)
        }

        /**
         * Record a non-fatal / expected failure (e.g. background save I/O, recoverable OOM).
         * This goes to the entry log, NOT to crash_*.log, so it is not mistaken for a crash.
         */
        fun logError(tag: String, message: String, throwable: Throwable? = null) {
            val sw = if (throwable != null) {
                val s = StringWriter()
                throwable.printStackTrace(PrintWriter(s))
                s.toString()
            } else ""
            appendEntryLog("ERROR [$tag] $message${if (sw.isNotEmpty()) "\n$sw" else ""}")
        }

        fun logEntry(tag: String, message: String) {
            appendEntryLog("[$tag] $message")
        }

        @Synchronized
        private fun appendEntryLog(line: String) {
            val dir = appFilesDir ?: return
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(dir, "entry_log.log")
                FileOutputStream(file, true).use { fos ->
                    fos.write("[$ts] $line\n".toByteArray())
                    fos.fd.sync()
                }
            } catch (_: Exception) {}
        }

        @Synchronized
        private fun writeCrashLogToFile(thread: Thread, throwable: Throwable, dir: File) {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val logFile = File(dir, "crash_$ts.log")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val content = "Time: $ts\n" +
                    "Thread: ${thread.name}\n" +
                    "Exception: ${throwable.javaClass.name}: ${throwable.message}\n\n" +
                    "Stack trace:\n$sw"
                FileOutputStream(logFile).use { fos ->
                    fos.write(content.toByteArray())
                    fos.fd.sync()
                }
                Log.e("PixelColor", "Crash log saved: ${logFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("PixelColor", "Failed to write crash log", e)
            }
        }

        var instance: PixelColorApp? = null
            private set
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val dir = appFilesDir
        if (dir == null) {
            Log.e("PixelColor", "writeCrashLog: appFilesDir not ready")
            return
        }
        writeCrashLogToFile(thread, throwable, dir)
    }

    init {
        instance = this
    }
}
