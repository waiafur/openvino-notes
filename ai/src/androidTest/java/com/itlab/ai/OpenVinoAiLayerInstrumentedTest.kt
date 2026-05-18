package com.itlab.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itlab.domain.app.FileSystemProvider
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
        val fileSystem = TestFileSystemProvider(context)
        return OpenVinoEngine(fileSystem, modelPath).also { engines.add(it) }
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

    private class TestFileSystemProvider(
        private val context: android.content.Context,
    ) : FileSystemProvider {
        override fun openAsset(path: String): InputStream = context.assets.open(path)

        override fun listAssets(path: String): Array<String> = context.assets.list(path) ?: emptyArray()

        override fun getFilesDir(): File = context.filesDir

        override fun getTotalRamMB(): Long = 1024
    }

    @Test
    fun testCopyYoloToTestAssets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // ПРАВИЛЬНОЕ МЕСТО — filesDir/models
        val modelsDir = File(context.filesDir, "models")

        assertTrue("Папка models не найдена по пути: ${modelsDir.absolutePath}", modelsDir.exists())

        // Проверяем обе модели
        val yolo26nDir = File(modelsDir, "yolo26n_openvino_model")
        val yolo26nXml = File(yolo26nDir, "yolo26n.xml")
        val yolo26nBin = File(yolo26nDir, "yolo26n.bin")

        if (yolo26nDir.exists()) {
            assertTrue("yolo26n.xml должен быть скопирован", yolo26nXml.exists())
            assertTrue("yolo26n.bin должен быть скопирован", yolo26nBin.exists())
            println("✅ YOLO26n: ${yolo26nXml.length()}, ${yolo26nBin.length()} bytes")
        } else {
            println("⚠️ YOLO26n не найден")
        }

        val yolov10nDir = File(modelsDir, "yolov10n_openvino_model")
        val yolov10nXml = File(yolov10nDir, "yolov10n.xml")
        val yolov10nBin = File(yolov10nDir, "yolov10n.bin")

        if (yolov10nDir.exists()) {
            assertTrue("yolov10n.xml должен быть скопирован", yolov10nXml.exists())
            assertTrue("yolov10n.bin должен быть скопирован", yolov10nBin.exists())
            println("✅ YOLOv10n: ${yolov10nXml.length()}, ${yolov10nBin.length()} bytes")
        } else {
            println("⚠️ YOLOv10n не найден")
        }

        // Хотя бы одна модель должна быть скопирована
        val hasAnyModel = (yolo26nDir.exists() && yolo26nXml.exists()) ||
            (yolov10nDir.exists() && yolov10nXml.exists())

        assertTrue("Ни одна модель не была скопирована в ${modelsDir.absolutePath}", hasAnyModel)
    }
}
