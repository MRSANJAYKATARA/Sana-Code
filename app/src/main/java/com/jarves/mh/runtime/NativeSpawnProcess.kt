package com.jarves.mh.runtime

import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

internal class NativeSpawnProcess private constructor(
    private val pid: Int,
    internal val outputFile: File,
    private val stdin: OutputStream,
) : Process() {
    @Volatile private var result: Int? = null

    override fun getOutputStream(): OutputStream = stdin
    override fun getInputStream(): InputStream = FileInputStream(outputFile)
    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int {
        result?.let { return it }
        return NativeSpawn.waitFor(pid, false).also { result = it }
    }

    override fun exitValue(): Int {
        result?.let { return it }
        val status = NativeSpawn.waitFor(pid, true)
        if (status == NativeSpawn.STILL_RUNNING) throw IllegalThreadStateException("Process is still running")
        return status.also { result = it }
    }

    override fun destroy() {
        NativeSpawn.kill(pid, 15)
    }

    /** Send the same interrupt signal produced by Ctrl+C in a real terminal. */
    internal fun interrupt() {
        NativeSpawn.kill(pid, 2)
    }

    override fun destroyForcibly(): Process {
        NativeSpawn.kill(pid, 9)
        return this
    }

    override fun isAlive(): Boolean = runCatching { exitValue(); false }.getOrDefault(true)

    companion object {
        fun start(argv: List<String>, environment: Map<String, String>, cwd: String, outputFile: File): NativeSpawnProcess {
            outputFile.parentFile?.mkdirs()
            val spawned = NativeSpawn.spawn(
                argv.toTypedArray(),
                environment.map { "${it.key}=${it.value}" }.toTypedArray(),
                cwd,
                outputFile.absolutePath,
            )
            check(spawned.size == 2 && spawned[0] > 0) { "Native runtime launch failed" }
            val input = ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.adoptFd(spawned[1]))
            return NativeSpawnProcess(spawned[0], outputFile, input)
        }
    }
}

class PtyProcess(
    val pid: Int,
    val masterFd: Int,
    val stdin: OutputStream,
    val stdout: InputStream,
) {
    fun setWindowSize(cols: Int, rows: Int): Boolean {
        return NativeSpawn.setPtyWindowSize(masterFd, cols, rows) == 0
    }

    fun interrupt() {
        NativeSpawn.kill(pid, 2)
    }

    fun destroy() {
        NativeSpawn.kill(pid, 15)
        closeFds()
    }

    fun destroyForcibly() {
        NativeSpawn.kill(pid, 9)
        closeFds()
    }

    fun isAlive(): Boolean {
        val status = NativeSpawn.waitFor(pid, true)
        return status == NativeSpawn.STILL_RUNNING
    }

    private fun closeFds() {
        runCatching { stdin.close() }
        runCatching { stdout.close() }
    }

    companion object {
        fun start(
            argv: List<String>,
            environment: Map<String, String>,
            cwd: String,
            cols: Int = 80,
            rows: Int = 24,
        ): PtyProcess {
            val pty = NativeSpawn.createPty(cols, rows)
                ?: throw IllegalStateException("Failed to allocate pseudo-terminal")
            val masterFd = pty[0]
            val slaveFd = pty[1]

            val pid = NativeSpawn.spawnWithPty(
                argv.toTypedArray(),
                environment.map { "${it.key}=${it.value}" }.toTypedArray(),
                cwd,
                slaveFd,
            )

            runCatching {
                ParcelFileDescriptor.adoptFd(slaveFd).close()
            }

            check(pid > 0) { "Failed to spawn process in PTY" }

            val masterPfd = ParcelFileDescriptor.adoptFd(masterFd)
            val stdin = ParcelFileDescriptor.AutoCloseOutputStream(masterPfd)
            val dupPfd = masterPfd.dup()
            val stdout = ParcelFileDescriptor.AutoCloseInputStream(dupPfd)

            return PtyProcess(pid, masterFd, stdin, stdout)
        }
    }
}

internal object NativeSpawn {
    const val STILL_RUNNING = -2

    init {
        System.loadLibrary("pocketspawn")
    }

    external fun spawn(argv: Array<String>, environment: Array<String>, cwd: String, outputFile: String): IntArray
    external fun waitFor(pid: Int, noHang: Boolean): Int
    external fun kill(pid: Int, signal: Int): Int

    external fun createPty(cols: Int, rows: Int): IntArray?
    external fun setPtyWindowSize(masterFd: Int, cols: Int, rows: Int): Int
    external fun spawnWithPty(argv: Array<String>, environment: Array<String>, cwd: String, slaveFd: Int): Int
}

