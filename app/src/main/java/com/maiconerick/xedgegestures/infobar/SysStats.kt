package com.maiconerick.xedgegestures.infobar

import android.content.Context
import android.os.BatteryManager
import java.io.File

/** Ported from xPlayTimeTracker's SysStats — same data, same probes. */
data class SysSnapshot(
    val cpuPct: Int? = null,
    val gpuPct: Int? = null,
    val fps: Int? = null,
    val drops: Int = 0,
    val ramUsedMb: Long = 0,
    val ramTotalMb: Long = 0,
    val tempC: Int? = null,
    val batteryPct: Int? = null,
    val charging: Boolean = false,
)

class SysStats(ctx: Context) {

    private val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private var lastTotal = 0L
    private var lastIdle = 0L
    private val lastCore = HashMap<String, Pair<Long, Long>>()

    private val cpuZones: List<File> by lazy {
        val out = ArrayList<File>()
        File("/sys/class/thermal").listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.forEach { z ->
                val type = readText(File(z, "type"))?.trim()?.lowercase() ?: return@forEach
                if (type.startsWith("cpu")) out.add(File(z, "temp"))
            }
        if (out.isEmpty()) listOfNotNull(
            File("/sys/class/thermal/thermal_zone0/temp").takeIf { it.exists() }
        ) else out
    }

    init { readCpuTicks() }

    private var battPct: Int? = null
    private var battCharging = false
    private var lastFps: Int? = null
    private var lastDrops: Int = 0

    fun sample(includeFps: Boolean = false): SysSnapshot {
        if (includeFps) FpsMeter.sample().let { lastFps = it?.fps; lastDrops = it?.dropped ?: 0 }
        val mem = readMeminfo()
        battPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        val status = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        battCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return SysSnapshot(
            cpuPct = cpuPercent(),
            gpuPct = gpuPercent(),
            fps = lastFps,
            drops = lastDrops,
            ramUsedMb = mem.first,
            ramTotalMb = mem.second,
            tempC = cpuTemp(),
            batteryPct = battPct,
            charging = battCharging,
        )
    }

    private fun cpuPercent(): Int? {
        val text = readText(File("/proc/stat")) ?: return null
        var max = -1
        for (line in text.lineSequence()) {
            if (!line.startsWith("cpu")) break
            val parts = line.trim().split(Regex("\\s+"))
            val name = parts.firstOrNull() ?: continue
            if (name == "cpu") continue
            val f = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (f.size < 5) continue
            val total = f.take(8).sum()
            val idle = f[3] + f[4]
            val prev = lastCore[name]
            lastCore[name] = total to idle
            if (prev != null) {
                val dTotal = total - prev.first
                val dIdle = idle - prev.second
                if (dTotal > 0) {
                    val pct = (((dTotal - dIdle) * 100) / dTotal).toInt()
                    if (pct > max) max = pct
                }
            }
        }
        return max.takeIf { it >= 0 }?.coerceIn(0, 100)
    }

    private fun readCpuTicks(): Pair<Long, Long>? {
        val line = readText(File("/proc/stat"))?.lineSequence()?.firstOrNull() ?: return null
        val f = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (f.size < 5) return null
        val total = f.take(8).sum()
        val idle = f[3] + f[4]
        if (lastTotal == 0L) { lastTotal = total; lastIdle = idle }
        return total to idle
    }

    private fun gpuPercent(): Int? {
        readText(File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"))?.let { raw ->
            raw.trim().takeWhile { it.isDigit() }.toIntOrNull()?.let { return it.coerceIn(0, 100) }
        }
        readText(File("/sys/class/kgsl/kgsl-3d0/gpubusy"))?.let { raw ->
            val p = raw.trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
            if (p.size >= 2 && p[1] > 0) return ((p[0] * 100) / p[1]).toInt().coerceIn(0, 100)
        }
        return null
    }

    private fun readMeminfo(): Pair<Long, Long> {
        var total = 0L; var available = 0L
        readText(File("/proc/meminfo"))?.lineSequence()?.forEach { l ->
            when {
                l.startsWith("MemTotal:") -> total = kb(l)
                l.startsWith("MemAvailable:") -> available = kb(l)
            }
        }
        return ((total - available) / 1024) to (total / 1024)
    }

    private fun kb(line: String): Long =
        line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: 0L

    private fun cpuTemp(): Int? {
        var max = Int.MIN_VALUE
        cpuZones.forEach { f ->
            readText(f)?.trim()?.toIntOrNull()?.let { raw ->
                val c = if (raw > 1000) raw / 1000 else raw
                if (c in 1..150 && c > max) max = c
            }
        }
        return if (max == Int.MIN_VALUE) null else max
    }

    private fun readText(f: File): String? =
        try { if (f.canRead()) f.readText() else null } catch (e: Exception) { null }
}
