package ru.yakovenko.mountainform.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {
    @Test
    fun seededCatalogHasInstructionsAndIllustrationsForEveryExercise() {
        val catalog = seedExerciseCatalog(Json)

        assertEquals(13, catalog.size)
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        assertTrue(catalog.all { it.setup.isNotBlank() && it.execution.isNotBlank() })
        assertTrue(catalog.all { it.breathing.isNotBlank() && it.illustrationKey.isNotBlank() })
        assertFalse(catalog.any { it.frameCount < 2 })
    }

    @Test
    fun warmupAndCooldownResolveToWalkingCatalogItem() {
        fun step(id: String) = ExerciseStep(id, id, "", "")

        assertEquals("walk", step("warmup").catalogId())
        assertEquals("walk", step("walk-warmup").catalogId())
        assertEquals("walk", step("cooldown").catalogId())
    }
}
