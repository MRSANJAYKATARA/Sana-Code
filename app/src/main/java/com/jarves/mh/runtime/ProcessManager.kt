package com.jarves.mh.runtime

import android.os.Process as AndroidProcess
import java.io.File

data class LinuxProcessInfo(
    val pid: Int,
    val name: String,
    val command: String,
    val state: String = "RUNNING",
)

class ProcessManager {
    fun listProcesses(): List<LinuxProcessInfo> {
        val procDir = File("/proc")
        if (!procDir.exists() || !procDir.isDirectory) return emptyList()

        val myUid = AndroidProcess.myUid()
        val result = mutableListOf<LinuxProcessInfo>()

        procDir.listFiles()?.forEach { file ->
            val pid = file.name.toIntOrNull() ?: return@forEach
            val statusFile = File(file, "status")
            val cmdlineFile = File(file, "cmdline")

            var name = "unknown"
            var state = "RUNNING"
            var isOurUid = false

            if (statusFile.exists()) {
                runCatching {
                    statusFile.forEachLine { line ->
                        if (line.startsWith("Name:")) {
                            name = line.substringAfter("Name:").trim()
                        } else if (line.startsWith("State:")) {
                            state = line.substringAfter("State:").trim()
                        } else if (line.startsWith("Uid:")) {
                            val uid = line.substringAfter("Uid:").trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull()
                            if (uid == myUid) isOurUid = true
                        }
                    }
                }
            }

            if (isOurUid) {
                val cmdline = runCatching {
                    cmdlineFile.readBytes().joinToString(" ") { b ->
                        if (b == 0.toByte()) " " else b.toInt().toChar().toString()
                    }.trim()
                }.getOrDefault("").ifBlank { name }

                result.add(LinuxProcessInfo(pid = pid, name = name, command = cmdline, state = state))
            }
        }

        return result.sortedBy { it.pid }
    }

    fun terminate(pid: Int): Boolean {
        return NativeSpawn.kill(pid, 15) == 0
    }

    fun killForcibly(pid: Int): Boolean {
        return NativeSpawn.kill(pid, 9) == 0
    }
}
