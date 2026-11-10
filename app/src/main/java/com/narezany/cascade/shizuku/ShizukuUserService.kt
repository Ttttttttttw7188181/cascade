package com.narezany.cascade.shizuku

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * Runs inside the Shizuku user-service process with shell (ADB) identity.
 *
 * It is intentionally tiny: Shizzi exposes a broadcast API, and shell identity is
 * exactly what is needed to deliver those broadcasts on newer Android versions.
 */
class ShizukuUserService : IShizukuUserService.Stub() {
    override fun run(command: String?): String {
        val safe = command.orEmpty().trim()
        if (!safe.startsWith("am broadcast -a dev.shizzi.")) {
            return "error: command rejected"
        }
        return runCatching {
            val process = ProcessBuilder("sh", "-c", safe).start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "error: timeout"
            }
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            "code=${process.exitValue()}\n$out\n$err".trim()
        }.getOrElse { "error: ${it.message}" }
    }
}
