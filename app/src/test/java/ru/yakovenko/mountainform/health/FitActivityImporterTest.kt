package ru.yakovenko.mountainform.health

import com.garmin.fit.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FitActivityImporterTest {
    @Test
    fun runningCadenceConvertsGarminCyclesToStepsPerMinute() {
        val cadence = FitActivityImporter.actualCadence(
            runningCadence = 84.toShort(),
            fractionalCadence = 0.984f,
            sport = Sport.RUNNING,
            cadence = 84.toShort(),
        )

        assertEquals(169.968, cadence!!, 0.001)
    }

    @Test
    fun nonRunningCadenceIsNotDoubled() {
        assertEquals(
            82.5,
            FitActivityImporter.actualCadence(82.toShort(), 0.5f, Sport.CYCLING, 82.toShort())!!,
            0.001,
        )
        assertNull(FitActivityImporter.actualCadence(null, null, Sport.RUNNING, null))
    }

    @Test
    fun zipImportSelectsFitEntriesAndIgnoresOtherFiles() {
        val fitBytes = byteArrayOf(1, 2, 3, 4)
        val archive = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("activity/record.fit"))
                zip.write(fitBytes)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("activity/readme.txt"))
                zip.write(byteArrayOf(9))
                zip.closeEntry()
            }
        }.toByteArray()

        val entries = FitActivityImporter.inputFiles("garmin.zip", archive)

        assertEquals("record.fit", entries.single().first)
        assertArrayEquals(fitBytes, entries.single().second)
    }
}
