package com.maiconerick.xedgegestures.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Persistent `su` shell. First `su` invocation prompts Magisk (from the
 * activity's context) and the grant then covers every subsequent command
 * without new prompts. If the shell dies (Magisk revoke, OOM, whatever) we
 * respawn transparently. All I/O is guarded by [lock] so parallel gesture
 * fires don't clobber each other's marker-fenced captures.
 */
object RootShell {

    private const val TAG = "xEdgeRootShell"
    private const val DEFAULT_TIMEOUT_MS = 4_000L

    @Volatile private var verifiedRoot: Boolean? = null
    private val lock = Any()

    private var proc: Process? = null
    private var stdin: DataOutputStream? = null
    private var stdout: BufferedReader? = null
    private var stderr: BufferedReader? = null

    fun isGranted(): Boolean? = verifiedRoot

    /** Runs `id` through the persistent shell; caches uid=0 result. */
    fun testGrant(): Boolean {
        val r = runFull("id")
        val ok = r.exit == 0 && r.stdout.contains("uid=0")
        verifiedRoot = ok
        Log.i(TAG, "testGrant exit=${r.exit} out=${r.stdout.trim().take(120)} err=${r.stderr.trim().take(120)} -> $ok")
        return ok
    }

    fun exec(cmd: String): Boolean {
        val r = runFull(cmd)
        if (r.exit != 0) Log.w(TAG, "exec fail: $cmd exit=${r.exit} err=${r.stderr.trim().take(160)}")
        else Log.d(TAG, "exec ok: $cmd")
        return r.exit == 0
    }

    fun capture(cmd: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        val r = runFull(cmd, timeoutMs)
        return if (r.exit == 0) r.stdout else null
    }

    data class Result(val exit: Int, val stdout: String, val stderr: String)

    /** Runs a command, returning exit + captured stdout/stderr. */
    fun runFull(cmd: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result = synchronized(lock) {
        if (!ensure()) return Result(-1, "", "su unavailable")
        val marker = "__END_${System.nanoTime()}__"
        return try {
            // Route stderr to a marker-fenced file we read back after; the
            // simplest way to keep stdout clean while still capturing errors.
            val errFile = "/data/local/tmp/xedge_err_${System.nanoTime()}"
            stdin!!.writeBytes("($cmd) 2>$errFile; ec=\$?; echo $marker \$ec\n")
            stdin!!.flush()

            val out = StringBuilder()
            var exit = -1
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val line = readLineOrNull(deadline) ?: continue
                if (line.startsWith(marker)) {
                    exit = line.removePrefix(marker).trim().toIntOrNull() ?: -1
                    break
                }
                out.append(line).append('\n')
            }
            if (exit == -1) {
                Log.w(TAG, "capture timeout, tearing shell down: $cmd")
                teardown()
                return Result(-1, out.toString(), "timeout")
            }
            val err = readErr(errFile)
            Result(exit, out.toString(), err)
        } catch (t: Throwable) {
            Log.w(TAG, "runFull failed: $cmd", t)
            teardown()
            Result(-1, "", t.message ?: "io error")
        }
    }

    private fun readErr(errFile: String): String {
        // Cat + delete the stderr scratch file through the same shell.
        val marker = "__ERREND_${System.nanoTime()}__"
        return try {
            stdin!!.writeBytes("cat $errFile 2>/dev/null; echo; echo $marker\nrm -f $errFile\n")
            stdin!!.flush()
            val out = StringBuilder()
            val deadline = System.currentTimeMillis() + 1_500
            while (System.currentTimeMillis() < deadline) {
                val line = readLineOrNull(deadline) ?: continue
                if (line == marker) return out.toString().trimEnd()
                out.append(line).append('\n')
            }
            out.toString()
        } catch (_: Throwable) { "" }
    }

    private fun readLineOrNull(deadline: Long): String? {
        val r = stdout ?: return null
        // BufferedReader has no timed readLine; poll ready() so we honor deadline.
        while (System.currentTimeMillis() < deadline) {
            if (r.ready()) return r.readLine()
            Thread.sleep(4)
        }
        return null
    }

    private fun ensure(): Boolean {
        if (proc?.isAlive == true) return true
        teardown()
        return try {
            val p = Runtime.getRuntime().exec("su")
            proc = p
            stdin = DataOutputStream(p.outputStream)
            stdout = BufferedReader(InputStreamReader(p.inputStream))
            stderr = BufferedReader(InputStreamReader(p.errorStream))
            // Prime the shell so Magisk grant flows through before the first real command.
            stdin!!.writeBytes("echo __xedge_ready__\n")
            stdin!!.flush()
            val deadline = System.currentTimeMillis() + 3_000
            var ready = false
            while (System.currentTimeMillis() < deadline) {
                val line = readLineOrNull(deadline) ?: continue
                if (line.contains("__xedge_ready__")) { ready = true; break }
            }
            if (!ready) {
                Log.w(TAG, "su spawned but never became ready (Magisk denial?)")
                teardown()
                verifiedRoot = false
                return false
            }
            Log.i(TAG, "su shell ready")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "su spawn failed", t)
            teardown()
            verifiedRoot = false
            false
        }
    }

    private fun teardown() {
        try { stdin?.close() } catch (_: Throwable) {}
        try { stdout?.close() } catch (_: Throwable) {}
        try { stderr?.close() } catch (_: Throwable) {}
        try { proc?.destroy() } catch (_: Throwable) {}
        stdin = null; stdout = null; stderr = null; proc = null
    }
}
