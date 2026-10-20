package com.maiconerick.xedgegestures.data.prefs

/** Layout helpers for placing triggers along an edge. */
object EdgeTriggers {

    /** A new trigger in the widest uncovered stretch of the edge, colored unlike its neighbors. */
    fun newTrigger(existing: List<EdgeTrigger>): EdgeTrigger {
        val (start, end) = widestGap(existing) ?: (0f to 100f)
        return EdgeTrigger(
            startPct = start, endPct = end,
            sizeDp = existing.firstOrNull()?.sizeDp ?: 30,
            colorArgb = nextColor(existing),
        )
    }

    /** [n] equal zones; zone i keeps the actions and color of the i-th existing trigger. */
    fun split(sorted: List<EdgeTrigger>, n: Int): List<EdgeTrigger> {
        val step = 100f / n
        val out = ArrayList<EdgeTrigger>(n)
        for (i in 0 until n) {
            val base = sorted.getOrNull(i)
                ?: EdgeTrigger(sizeDp = sorted.firstOrNull()?.sizeDp ?: 30, colorArgb = nextColor(sorted + out))
            out += base.copy(startPct = i * step, endPct = if (i == n - 1) 100f else (i + 1) * step)
        }
        return out
    }

    fun nextColor(existing: List<EdgeTrigger>): Long {
        val used = existing.map { it.colorArgb }.toSet()
        return TriggerPalette.firstOrNull { it !in used } ?: TriggerPalette[existing.size % TriggerPalette.size]
    }

    /** Widest range (at least 5%) not covered by any trigger, or null if the edge is full. */
    private fun widestGap(existing: List<EdgeTrigger>): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        var cursor = 0f
        for (t in existing.sortedBy { it.startPct }) {
            if (t.startPct - cursor > (best?.let { it.second - it.first } ?: 5f)) best = cursor to t.startPct
            cursor = maxOf(cursor, t.endPct)
        }
        if (100f - cursor > (best?.let { it.second - it.first } ?: 5f)) best = cursor to 100f
        return best
    }
}
