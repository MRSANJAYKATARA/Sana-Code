package com.jarves.mh.runtime

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TerminalSessionData(
    val id: String,
    val title: String,
    val workingDir: String,
    val pid: Int,
    val isAlive: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

class SessionManager(
    private val app: Application,
    private val installer: RuntimeInstaller,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ptyProcesses = ConcurrentHashMap<String, PtyProcess>()
    private val outputBuffers = ConcurrentHashMap<String, StringBuilder>()

    private val _sessions = MutableStateFlow<List<TerminalSessionData>>(emptyList())
    val sessions: StateFlow<List<TerminalSessionData>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _liveOutputs = MutableStateFlow<Map<String, String>>(emptyMap())
    val liveOutputs: StateFlow<Map<String, String>> = _liveOutputs.asStateFlow()

    fun createSession(title: String? = null, cwd: String = "/workspace"): TerminalSessionData? {
        if (!installer.isInstalled()) return null
        val runtime = runCatching { installer.installedRuntime() }.getOrNull() ?: return null
        val sessionId = UUID.randomUUID().toString()
        val sessionIndex = (_sessions.value.size + 1)
        val sessionTitle = title ?: "Terminal $sessionIndex"
        val workspace = File(app.filesDir, "workspaces/terminal").apply { mkdirs() }

        val pty = runCatching {
            installer.startPtyProcess(
                proot = runtime.proot,
                rootfs = runtime.rootfs,
                workspace = workspace,
                environment = mapOf("TERM" to "xterm-256color", "COLORTERM" to "truecolor"),
                guestCommand = listOf("/bin/bash", "-l"),
                guestWorkspacePath = cwd,
                cols = 80,
                rows = 24,
            )
        }.getOrElse {
            Log.e("SessionManager", "Failed to spawn interactive PTY shell", it)
            return null
        }

        ptyProcesses[sessionId] = pty
        outputBuffers[sessionId] = StringBuilder()

        val data = TerminalSessionData(
            id = sessionId,
            title = sessionTitle,
            workingDir = cwd,
            pid = pty.pid,
            isAlive = true,
        )

        _sessions.value = _sessions.value + data
        if (_activeSessionId.value == null) {
            _activeSessionId.value = sessionId
        }

        startPtyReader(sessionId, pty)

        return data
    }

    private fun startPtyReader(sessionId: String, pty: PtyProcess) {
        scope.launch {
            val buffer = ByteArray(4096)
            val stream = pty.stdout
            try {
                while (isActive && pty.isAlive()) {
                    val read = withContext(Dispatchers.IO) {
                        runCatching { stream.read(buffer) }.getOrDefault(-1)
                    }
                    if (read <= 0) break
                    val text = String(buffer, 0, read, Charsets.UTF_8)
                    val sb = outputBuffers.getOrPut(sessionId) { StringBuilder() }
                    synchronized(sb) {
                        sb.append(text)
                        if (sb.length > 100_000) {
                            sb.delete(0, sb.length - 100_000)
                        }
                    }
                    _liveOutputs.value = _liveOutputs.value + (sessionId to sb.toString())
                }
            } catch (e: Exception) {
                Log.w("SessionManager", "PTY stream closed for session $sessionId: ${e.message}")
            } finally {
                updateSessionStatus(sessionId, false)
            }
        }
    }

    fun selectSession(sessionId: String) {
        if (_sessions.value.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
        }
    }

    fun writeInput(sessionId: String, input: String) {
        val pty = ptyProcesses[sessionId] ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                pty.stdin.write(input.toByteArray(Charsets.UTF_8))
                pty.stdin.flush()
            }
        }
    }

    fun sendInterrupt(sessionId: String) {
        val pty = ptyProcesses[sessionId] ?: return
        pty.interrupt()
    }

    fun sendEof(sessionId: String) {
        writeInput(sessionId, "\u0004")
    }

    fun resize(sessionId: String, cols: Int, rows: Int) {
        val pty = ptyProcesses[sessionId] ?: return
        pty.setWindowSize(cols, rows)
    }

    fun closeSession(sessionId: String) {
        val pty = ptyProcesses.remove(sessionId)
        pty?.destroy()
        outputBuffers.remove(sessionId)
        _sessions.value = _sessions.value.filterNot { it.id == sessionId }
        _liveOutputs.value = _liveOutputs.value - sessionId
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = _sessions.value.firstOrNull()?.id
        }
    }

    private fun updateSessionStatus(sessionId: String, isAlive: Boolean) {
        _sessions.value = _sessions.value.map {
            if (it.id == sessionId) it.copy(isAlive = isAlive) else it
        }
    }

    fun terminateAll() {
        ptyProcesses.values.forEach { it.destroy() }
        ptyProcesses.clear()
        outputBuffers.clear()
        _sessions.value = emptyList()
        _liveOutputs.value = emptyMap()
        _activeSessionId.value = null
    }
}
