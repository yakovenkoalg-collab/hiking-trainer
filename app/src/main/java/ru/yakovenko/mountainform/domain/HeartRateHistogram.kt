package ru.yakovenko.mountainform.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Time spent at each heart rate during an activity: beats per minute → seconds. */
object HeartRateHistogram {
    /** A longer silence between samples is a pause or a dropout, not time at the last heart rate. */
    private const val MAX_SAMPLE_GAP_SECONDS = 30L
    private val json = Json

    /**
     * Each sample lasts until the next one, at most [MAX_SAMPLE_GAP_SECONDS]. The last sample lasts the typical
     * interval of the record. Samples outside [startEpochMillis]..[endEpochMillis] are ignored.
     */
    fun fromSamples(samples: List<Pair<Long, Int>>, startEpochMillis: Long, endEpochMillis: Long): Map<Int, Int> {
        val inRange = samples
            .filter { (time, bpm) -> time in startEpochMillis..endEpochMillis && bpm in 30..250 }
            .sortedBy { it.first }
        if (inRange.isEmpty()) return emptyMap()
        val gaps = inRange.zipWithNext { a, b -> (b.first - a.first) / 1000 }.filter { it in 1..MAX_SAMPLE_GAP_SECONDS }
        val typicalGap = gaps.sorted().getOrNull(gaps.size / 2) ?: 1L
        val result = sortedMapOf<Int, Long>()
        inRange.forEachIndexed { index, (time, bpm) ->
            val next = inRange.getOrNull(index + 1)?.first
            val seconds = when {
                next != null -> ((next - time) / 1000).coerceAtMost(MAX_SAMPLE_GAP_SECONDS)
                else -> minOf(typicalGap, (endEpochMillis - time) / 1000).coerceAtLeast(0)
            }
            if (seconds > 0) result[bpm] = (result[bpm] ?: 0L) + seconds
        }
        return result.mapValues { it.value.toInt() }
    }

    fun encode(histogram: Map<Int, Int>): String =
        if (histogram.isEmpty()) "{}" else json.encodeToString(histogram.toSortedMap() as Map<Int, Int>)

    fun decode(raw: String): Map<Int, Int> =
        runCatching { json.decodeFromString<Map<Int, Int>>(raw) }.getOrDefault(emptyMap())
}
