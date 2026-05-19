package com.itlab.ai

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.junit.Assert.assertNotNull
import java.io.FileOutputStream
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class OpenVinoAiLayerInstrumentedTest {
    private val engines = mutableListOf<OpenVinoEngine>()

    @After
    fun tearDown() {
        engines.forEach { it.release() }
        engines.clear()
    }

    private fun createEngine(modelPath: String = ""): OpenVinoEngine {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return OpenVinoEngine(context, modelPath).also { engines.add(it) }
    }

    @Test
    fun summarize_returnsTrimmedSummary() =
        runBlocking {
            val engine = createEngine()
            engine.initialize() // Явно инициализируем
            val service = OpenVinoNoteAiService(engine, ResultProcessor())
            val result = service.summarize("  Summary text  ")
            assertEquals("Summary text", result)
        }

    @Test
    fun tagTXT_normalizesCaseAndSeparators() =
        runBlocking {
            val engine = createEngine()
            engine.initialize()
            val service = OpenVinoNoteAiService(engine, ResultProcessor())
            val result = service.tagTXT(" Kotlin, Notes\nAI ")
            assertEquals(setOf("kotlin", "notes", "ai"), result)
        }

    @Test
    fun tagIMGs_aggregatesAndDeduplicatesTags() =
        runBlocking {
            val processor = ResultProcessor()
            val result = processor.normalizeTags(("Cat, Pet, pet, animal,   CAT"))
            assertEquals(setOf("cat", "pet", "animal"), result)
        }

    @Test
    fun initialize_returnsFalseWhenModelIsMissing() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val missingModel = File(context.filesDir, "missing-model.xml")
            val engine = createEngine(missingModel.absolutePath)

            val initialized =
                withContext(Dispatchers.Default) {
                    engine.initialize()
                }

            assertFalse(initialized)
        }

    @Test
    fun initialize_loadsBundledYoloModel() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val copiedModel = File(context.filesDir, "models/yolo26n_openvino_model/yolo26n.xml")
            val engine = createEngine()

            val initialized =
                withContext(Dispatchers.Default) {
                    engine.initialize()
                }

            assertTrue(copiedModel.exists())
            assertTrue(initialized)
            assertTrue(engine.isReady())
        }

    @Test
    fun copyYoloToTestAssets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Проверяем наличие моделей в assets
        val modelsInAssets = context.assets.list("models")
        assertNotNull("Папка models не найдена в assets", modelsInAssets)

        // Проверяем YOLO26n
        val yolo26nFiles = context.assets.list("models/yolo26n_openvino_model")
        val hasYolo26n = yolo26nFiles?.contains("yolo26n.xml") == true &&
            yolo26nFiles?.contains("yolo26n.bin") == true

        // Проверяем YOLOv10n
        val yolov10nFiles = context.assets.list("models/yolov10n_openvino_model")
        val hasYolov10n = yolov10nFiles?.contains("yolov10n.xml") == true &&
            yolov10nFiles?.contains("yolov10n.bin") == true

        assertTrue("Должна быть хотя бы одна модель", hasYolo26n || hasYolov10n)
        if (hasYolo26n) println("✅ YOLO26n найдена в assets")
        if (hasYolov10n) println("✅ YOLOv10n найдена в assets")
    }
}
