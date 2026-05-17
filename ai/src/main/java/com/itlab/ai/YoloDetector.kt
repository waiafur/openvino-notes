package com.itlab.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.intel.openvino.Core
import org.intel.openvino.Tensor
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import androidx.core.graphics.scale

class YoloDetector(private val context: Context) {

    companion object {
        private const val TAG = "YoloDetector"
        private const val INPUT_SIZE = 640
        private const val CONF_THRESHOLD = 0.35f
    }

    private var core: Core? = null
    private var compiledModel: org.intel.openvino.CompiledModel? = null
    private var inferRequest: org.intel.openvino.InferRequest? = null
    private var isInitialized = false

    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "Инициализация YOLO...")

            val modelFile = copyModelFromAssets()
            if (!modelFile.exists()) {
                Log.e(TAG, "Модель не найдена")
                return false
            }

            core = Core()
            val model = core?.read_model(modelFile.absolutePath) ?: return false
            compiledModel = core?.compile_model(model, "CPU") ?: return false
            inferRequest = compiledModel?.create_infer_request()

            isInitialized = true
            Log.d(TAG, "✅ YOLO готов")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка", e)
            false
        }
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        if (!isInitialized) return emptyList()

        return try {
            val inputData = preprocess(bitmap)
            val inputTensor = Tensor(intArrayOf(1, 3, INPUT_SIZE, INPUT_SIZE), inputData)
            inferRequest?.set_input_tensor(inputTensor)
            inferRequest?.infer()


            val outputTensor = inferRequest?.get_output_tensor()
            val outputData = getTensorData(outputTensor) ?: return emptyList()

            parseDetections(outputData)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка детекции", e)
            emptyList()
        }
    }

    private fun getTensorData(tensor: Tensor?): FloatArray? {
        return try {
            tensor?.data() as? FloatArray
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось получить данные тензора", e)
            null
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatArray {
        val resized = bitmap.scale(INPUT_SIZE, INPUT_SIZE)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val inputData = FloatArray(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        val area = INPUT_SIZE * INPUT_SIZE

        for (i in pixels.indices) {
            val r = (pixels[i] shr 16 and 0xFF) / 255f
            val g = (pixels[i] shr 8 and 0xFF) / 255f
            val b = (pixels[i] and 0xFF) / 255f

            inputData[i] = b
            inputData[area + i] = g
            inputData[2 * area + i] = r
        }
        return inputData
    }

    private fun parseDetections(outputData: FloatArray): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numDetections = 300

        for (i in 0 until numDetections) {
            val base = i * 6
            if (base + 5 >= outputData.size) break

            val x1 = outputData[base] * INPUT_SIZE
            val y1 = outputData[base + 1] * INPUT_SIZE
            val x2 = outputData[base + 2] * INPUT_SIZE
            val y2 = outputData[base + 3] * INPUT_SIZE
            val confidence = outputData[base + 4]
            val classId = outputData[base + 5].toInt()

            if (confidence > CONF_THRESHOLD) {
                detections.add(Detection(x1, y1, x2, y2, confidence, classId))
            }
        }
        return detections
    }

    private fun copyModelFromAssets(): File {
        val modelDir = File(context.filesDir, "yolo26n_openvino_model")
        val modelFile = File(modelDir, "yolo26n.xml")

        if (modelFile.exists()) return modelFile

        modelDir.mkdirs()

        try {
            val files = context.assets.list("yolo26n_openvino_model") ?: return modelFile
            for (fileName in files) {
                context.assets.open("yolo26n_openvino_model/$fileName").use { input ->
                    FileOutputStream(File(modelDir, fileName)).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка копирования", e)
        }

        return modelFile
    }

    fun isReady(): Boolean = isInitialized

    fun release() {
        compiledModel = null
        core = null
        isInitialized = false
    }
}

data class Detection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val classId: Int
)
