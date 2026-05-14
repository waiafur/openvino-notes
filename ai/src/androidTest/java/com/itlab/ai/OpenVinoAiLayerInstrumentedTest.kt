package com.itlab.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OpenVinoAiLayerInstrumentedTest {
    @Test
    fun summarize_returnsTrimmedSummary_onDevice() =
        runBlocking {
            val service = OpenVinoNoteAiService(OpenVinoEngine(), ResultProcessor())

            val result = service.summarize("  Summary text  ")

            assertEquals("Summary text", result)
        }

    @Test
    fun tagTXT_normalizesCaseAndSeparators_onDevice() =
        runBlocking {
            val service = OpenVinoNoteAiService(OpenVinoEngine(), ResultProcessor())

            val result = service.tagTXT(" Kotlin, Notes\nAI ")

            assertEquals(setOf("kotlin", "notes", "ai"), result)
        }

    @Test
    fun tagIMGs_aggregatesAndDeduplicatesTags_onDevice() =
        runBlocking {
            val service = OpenVinoNoteAiService(OpenVinoEngine(), ResultProcessor())

            val result = service.tagIMGs(listOf("Cat, Pet", "pet, animal", "  CAT"))

            assertEquals(setOf("cat", "pet", "animal"), result)
        }

    @Test
    fun initialize_returnsFalseWhenModelIsMissing_onDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missingModel = File(context.filesDir, "missing-model.xml")

        val engine =
            OpenVinoEngine(
                context = context,
                modelXmlPath = missingModel.absolutePath,
            )

        val initialized = engine.initialize()

        assertFalse(initialized)
    }

    @Test
    fun initialize_loadsBundledYoloModel_onDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val copiedModel = File(context.filesDir, "models/yolo26n_openvino_model/yolo26n.xml")
        val engine = OpenVinoEngine(context = context)

        val initialized = engine.initialize()

        assertTrue(copiedModel.exists())
        assertTrue(initialized)
        assertTrue(engine.isReady())
        engine.release()
    }
}
