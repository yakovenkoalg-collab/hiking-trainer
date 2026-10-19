package ru.yakovenko.mountainform.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateHistogramTest {
    private val start = 1_000_000L

    @Test
    fun eachSampleLastsUntilTheNextOneAndLongSilencesAreCapped() {
        val samples = listOf(
            start to 140, start + 1_000 to 140, start + 2_000 to 150, // 1 s each
            start + 7_000 to 151,                                     // the 150 lasted 5 s
            start + 127_000 to 152,                                   // a 120 s gap counts as 30 s
            start + 128_000 to 153,                                   // last sample: typical gap of 1 s
        )
        val histogram = HeartRateHistogram.fromSamples(samples, start, start + 200_000)
        assertEquals(mapOf(140 to 2, 150 to 5, 151 to 30, 152 to 1, 153 to 1), histogram)
    }

    @Test
    fun samplesOutsideTheActivityAndImplausibleValuesAreIgnored() {
        val samples = listOf(start - 5_000 to 120, start to 140, start + 1_000 to 0, start + 2_000 to 141, start + 9_000 to 160)
        val histogram = HeartRateHistogram.fromSamples(samples, start, start + 5_000)
        assertEquals(setOf(140, 141), histogram.keys)
        assertTrue(HeartRateHistogram.fromSamples(emptyList(), start, start + 5_000).isEmpty())
    }

    @Test
    fun histogramSurvivesStorage() {
        val histogram = mapOf(149 to 30, 140 to 600)
        val stored = HeartRateHistogram.encode(histogram)
        assertEquals(histogram, HeartRateHistogram.decode(stored))
        assertEquals("{}", HeartRateHistogram.encode(emptyMap()))
        assertTrue(HeartRateHistogram.decode("{}").isEmpty())
        assertTrue(HeartRateHistogram.decode("not json").isEmpty())
    }
}
