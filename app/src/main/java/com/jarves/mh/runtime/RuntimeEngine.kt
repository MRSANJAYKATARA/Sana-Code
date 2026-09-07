package com.jarves.mh.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class RuntimeStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    INSTALLED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    RECOVERING,
    ERROR
}

enum class LinuxDistro(
    val id: String,
    val displayName: String,
    val defaultShell: String,
    val description: String,
) {
    UBUNTU("ubuntu", "Ubuntu 24.04 LTS", "/usr/bin/bash", "Standard Linux environment for AI development & coding"),
    KALI("kali", "Kali Linux Rootless ARM64", "/usr/bin/bash", "Security & penetration testing workstation with XFCE GUI"),
}

data class RuntimeState(
    val status: RuntimeStatus = RuntimeStatus.NOT_INSTALLED,
    val distro: LinuxDistro = LinuxDistro.UBUNTU,
    val message: String = "",
    val progress: Float = 0f,
    val activeProcessesCount: Int = 0,
    val hasGuiServer: Boolean = false,
    val error: String? = null,
)

class RuntimeEngine(
    private val context: Context,
    private val installer: RuntimeInstaller,
) {
    private val _state = MutableStateFlow(RuntimeState())
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState(): RuntimeState {
        val installed = installer.isInstalled()
        val newState = if (installed) {
            val valid = validateIntegrity()
            if (valid) {
                _state.value.copy(
                    status = RuntimeStatus.INSTALLED,
                    message = "Linux runtime is installed and verified",
                    error = null,
                )
            } else {
                _state.value.copy(
                    status = RuntimeStatus.ERROR,
                    message = "Filesystem verification failed",
                    error = "Critical Linux rootfs files are missing or corrupt",
                )
            }
        } else {
            _state.value.copy(
                status = RuntimeStatus.NOT_INSTALLED,
                message = "Linux runtime is not installed",
                error = null,
            )
        }
        _state.value = newState
        return newState
    }

    fun updateStatus(status: RuntimeStatus, message: String = "", progress: Float = 0f, error: String? = null) {
        _state.value = _state.value.copy(
            status = status,
            message = message,
            progress = progress,
            error = error,
        )
    }

    fun validateIntegrity(): Boolean {
        return runCatching {
            val runtime = installer.installedRuntime()
            val rootfs = runtime.rootfs
            val hasSh = File(rootfs, "bin/sh").exists() || File(rootfs, "usr/bin/sh").exists()
            val hasBash = File(rootfs, "bin/bash").exists() || File(rootfs, "usr/bin/bash").exists()
            val hasEtc = File(rootfs, "etc").isDirectory
            hasSh && hasBash && hasEtc
        }.getOrDefault(false)
    }

    fun switchDistro(distro: LinuxDistro) {
        _state.value = _state.value.copy(distro = distro)
        refreshState()
    }

    fun getDistroRootfs(distro: LinuxDistro): File {
        return when (distro) {
            LinuxDistro.UBUNTU -> File(context.filesDir, "runtime/rootfs")
            LinuxDistro.KALI -> File(context.filesDir, "runtime/kali-rootfs")
        }
    }
}
