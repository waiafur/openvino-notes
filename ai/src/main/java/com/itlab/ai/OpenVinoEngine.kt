package com.itlab.ai

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.intel.openvino.CompiledModel
import org.intel.openvino.Core
import org.intel.openvino.InferRequest
import org.intel.openvino.Model
import org.intel.openvino.Output
import org.intel.openvino.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.FloatBuffer
import androidx.core.graphics.scale

@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
class OpenVinoEngine(
    private val context: Context? = null,
    private val modelXmlPath: String = "",
    private val deviceName: String = "CPU",
) {
    companion object {
        private const val TAG = "OpenVinoEngine"
        private const val DEFAULT_MODEL_ASSET_DIR = "models/yolo26n_openvino_model"
        private const val DEFAULT_MODEL_XML = "yolo26n.xml"
        private const val COCO_NAMES_FILE = "coco.names"

        // Параметры YOLO
        private const val INPUT_SIZE = 640
        private const val CONF_THRESHOLD = 0.35f
        private const val IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 300
    }

    private var core: Core? = null
    private var model: Model? = null
    private var compiledModel: CompiledModel? = null
    private var inferRequest: InferRequest? = null

    private var isInitialized = false
    private var activeModelXmlPath = modelXmlPath

    // Список классов из coco.names
    private var classNames: List<String> = emptyList()

    // Информация о входах/выходах модели
    var inputName: String = ""
        private set
    var inputShape: LongArray = longArrayOf()
        private set
    var inputElementType: String = ""
        private set

    var outputName: String = ""
        private set
    var outputShape: LongArray = longArrayOf()
        private set
    var outputElementType: String = ""
        private set
    fun runLlmSummary(text: String): String = text

    fun runLlmTagging(text: String): String = text

    // Основной метод для YOLO детекции (вызывается из app модуля)
    fun runYoloTagging(imageSource: String): String {
        debugLog { "Running YOLO tagging on: $imageSource" }

        val bitmap = try {
            android.graphics.BitmapFactory.decodeFile(imageSource)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image", e)
            null
        }

        if (bitmap == null) {
            Log.e(TAG, "Failed to load image: $imageSource")
            return ""
        }

        val detections = detectYolo(bitmap)
        bitmap.recycle()

        if (detections.isEmpty()) {
            debugLog { "No objects detected" }
            return ""
        }

        // Преобразуем детекции в строку тегов через запятую
        val tags = detections
            .map { it.classId }
            .distinct()
            .mapNotNull { classId -> getClassName(classId) }
            .joinToString(",")

        debugLog { "Detected tags: $tags" }
        return tags
    }

    // Внутренний метод для детекции
    private fun detectYolo(bitmap: Bitmap): List<YoloDetection> {
        if (!isInitialized) {
            Log.e(TAG, "Engine not initialized")
            return emptyList()
        }

        return try {
            debugLog { "Starting YOLO detection..." }

            val inputData = preprocessBitmap(bitmap)
            val inputTensor = createInputTensor(inputData)

            inferRequest?.set_input_tensor(inputTensor)
            inferRequest?.infer()

            val outputTensor = inferRequest?.get_output_tensor()
            val detections = parseOutputTensor(outputTensor)

            debugLog { "✅ Detection complete, found ${detections.size} objects" }
            detections

        } catch (e: Exception) {
            Log.e(TAG, "YOLO detection failed", e)
            emptyList()
        }
    }

    // Загрузка классов из coco.names
    private fun loadClassNames(appContext: Context): List<String> {
        return try {
            val names = mutableListOf<String>()
            appContext.assets.open(COCO_NAMES_FILE).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        names.add(line.trim())
                    }
                }
            }
            debugLog { "Loaded ${names.size} classes from $COCO_NAMES_FILE" }
            names
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load $COCO_NAMES_FILE", e)
            emptyList()
        }
    }

    // Получение имени класса по ID
    private fun getClassName(classId: Int): String? {
        return if (classId in classNames.indices) {
            classNames[classId]
        } else {
            Log.w(TAG, "Unknown class ID: $classId")
            "class_$classId"
        }
    }

    // Подготовка изображения для инференса
    @SuppressLint("UseKtx")
    private fun preprocessBitmap(bitmap: Bitmap): FloatArray {
        debugLog { "Preprocessing bitmap: ${bitmap.width}x${bitmap.height}" }

        val resized = bitmap.scale(INPUT_SIZE, INPUT_SIZE)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val inputData = FloatArray(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        val area = INPUT_SIZE * INPUT_SIZE

        for (i in pixels.indices) {
            val r = (pixels[i] shr 16 and 0xFF) / 255.0f
            val g = (pixels[i] shr 8 and 0xFF) / 255.0f
            val b = (pixels[i] and 0xFF) / 255.0f

            // Формат BGR для YOLO
            inputData[i] = b
            inputData[area + i] = g
            inputData[2 * area + i] = r
        }

        return inputData
    }

    // Создание тензора
    private fun createInputTensor(inputData: FloatArray): Tensor {
        val shape = intArrayOf(1, 3, INPUT_SIZE, INPUT_SIZE)
        return Tensor(shape, inputData)
    }

    // Парсинг выходного тензора
    private fun parseOutputTensor(outputTensor: Tensor?): List<YoloDetection> {
        if (outputTensor == null) {
            Log.e(TAG, "Output tensor is null")
            return emptyList()
        }

        return try {
            val outputData = getTensorDataAsFloatArray(outputTensor)
            if (outputData.isEmpty()) {
                Log.e(TAG, "Output data is empty")
                return emptyList()
            }

            val detections = mutableListOf<YoloDetection>()

            // Формат: [batch, num_detections, 6] где 6 = [x1,y1,x2,y2,confidence,classId]
            if (outputData.size >= MAX_DETECTIONS * 6) {
                for (i in 0 until MAX_DETECTIONS) {
                    val base = i * 6
                    if (base + 5 >= outputData.size) break

                    val x1 = outputData[base] * INPUT_SIZE
                    val y1 = outputData[base + 1] * INPUT_SIZE
                    val x2 = outputData[base + 2] * INPUT_SIZE
                    val y2 = outputData[base + 3] * INPUT_SIZE
                    val confidence = outputData[base + 4]
                    val classId = outputData[base + 5].toInt()

                    if (confidence > CONF_THRESHOLD) {
                        detections.add(YoloDetection(
                            x1 = x1.coerceIn(0f, INPUT_SIZE.toFloat()),
                            y1 = y1.coerceIn(0f, INPUT_SIZE.toFloat()),
                            x2 = x2.coerceIn(0f, INPUT_SIZE.toFloat()),
                            y2 = y2.coerceIn(0f, INPUT_SIZE.toFloat()),
                            confidence = confidence,
                            classId = classId
                        ))
                    }
                }
            } else {
                Log.w(TAG, "Unexpected output format, size: ${outputData.size}")
            }

            applyNMS(detections, IOU_THRESHOLD)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse output tensor", e)
            emptyList()
        }
    }

    // Получение данных из тензора
    private fun getTensorDataAsFloatArray(tensor: Tensor): FloatArray {
        return try {
            // Пробуем получить как ByteArray и конвертировать
            when (val data = tensor.data()) {
                is FloatArray -> data
                is ByteArray -> {
                    // Конвертируем ByteArray в FloatArray
                    val floatArray = FloatArray(data.size / 4)
                    java.nio.ByteBuffer.wrap(data).asFloatBuffer().get(floatArray)
                    floatArray
                }
                else -> {
                    Log.e(TAG, "Unknown tensor data type: ${data?.javaClass?.simpleName}")
                    floatArrayOf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tensor data", e)
            floatArrayOf()
        }
    }

    // Non-Maximum Suppression
    private fun applyNMS(detections: List<YoloDetection>, iouThreshold: Float): List<YoloDetection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<YoloDetection>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue

            val box1 = sorted[i]
            selected.add(box1)

            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue

                val box2 = sorted[j]
                val iou = calculateIOU(box1, box2)

                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }

        return selected
    }

    // Вычисление Intersection over Union
    private fun calculateIOU(box1: YoloDetection, box2: YoloDetection): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        if (x2 <= x1 || y2 <= y1) return 0f

        val intersection = (x2 - x1) * (y2 - y1)
        val area1 = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val area2 = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        val union = area1 + area2 - intersection

        return if (union > 0) intersection / union else 0f
    }

    // Инициализация (добавляем загрузку coco.names)
    fun initialize(): Boolean {
        debugLog { "Initializing OpenVINO Engine..." }
        debugLog { "Model path: $modelXmlPath" }
        isInitialized = false

        val appContext = context
        return when {
            appContext == null -> {
                Log.e(TAG, "Android context is required to initialize OpenVINO")
                false
            }
            else -> {
                // Загружаем имена классов
                classNames = loadClassNames(appContext)
                if (classNames.isEmpty()) {
                    Log.w(TAG, "No class names loaded, using IDs instead")
                }
                initializeWithResolvedModel(appContext)
            }
        }
    }

    private fun initializeWithResolvedModel(appContext: Context): Boolean {
        val resolvedModelXmlPath = resolveModelXmlPath(appContext) ?: return false
        activeModelXmlPath = resolvedModelXmlPath

        return if (!File(activeModelXmlPath).exists()) {
            Log.e(TAG, "❌ Model file not found: $activeModelXmlPath")
            false
        } else {
            initializeOpenVino(appContext)
        }
    }

    private fun resolveModelXmlPath(appContext: Context): String? {
        val modelDir = File(appContext.filesDir, DEFAULT_MODEL_ASSET_DIR)
        val modelXml = File(modelDir, DEFAULT_MODEL_XML)
        val resolvedPath =
            when {
                modelXmlPath.isNotBlank() -> modelXmlPath
                modelXml.exists() -> modelXml.absolutePath
                copyAssetDirectory(appContext, DEFAULT_MODEL_ASSET_DIR, modelDir) -> modelXml.absolutePath
                else -> {
                    Log.e(TAG, "Bundled model assets are missing: $DEFAULT_MODEL_ASSET_DIR")
                    null
                }
            }

        return resolvedPath
    }

    private fun copyAssetDirectory(
        appContext: Context,
        assetPath: String,
        targetDir: File,
    ): Boolean {
        val entries = appContext.assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            return false
        }

        targetDir.mkdirs()
        return entries.all { entry ->
            val childAssetPath = "$assetPath/$entry"
            val childTarget = File(targetDir, entry)
            val childEntries = appContext.assets.list(childAssetPath).orEmpty()
            if (childEntries.isEmpty()) {
                copyAssetFile(appContext, childAssetPath, childTarget)
            } else {
                copyAssetDirectory(appContext, childAssetPath, childTarget)
            }
        }
    }

    private fun copyAssetFile(
        appContext: Context,
        assetPath: String,
        targetFile: File,
    ): Boolean =
        try {
            targetFile.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset: $assetPath", e)
            false
        }

    private fun initializeOpenVino(appContext: Context): Boolean =
        try {
            val pluginsFile = preparePluginsFile(appContext)
            val activeCore = createCore(pluginsFile)
            val devices = activeCore.get_available_devices()
            debugLog { "Available devices: $devices" }

            if (devices.isNullOrEmpty()) {
                Log.e(TAG, "No OpenVINO devices available")
                false
            } else {
                loadAndCompileModel(activeCore)
                isInitialized = true
                debugLog { "✅ Engine initialized successfully" }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine: ${e.message}", e)
            false
        }

    private fun preparePluginsFile(appContext: Context): File {
        val pluginsFile = File(appContext.filesDir, "plugins.xml")
        if (!pluginsFile.exists()) {
            copyPluginsFile(appContext, pluginsFile)
        }
        return pluginsFile
    }

    private fun copyPluginsFile(
        appContext: Context,
        pluginsFile: File,
    ) {
        try {
            appContext.assets.open("plugins.xml").use { input ->
                FileOutputStream(pluginsFile).use { output ->
                    input.copyTo(output)
                }
            }
            debugLog { "plugins.xml copied to: ${pluginsFile.absolutePath}" }
        } catch (e: IOException) {
            Log.w(TAG, "No plugins.xml in assets, trying without explicit config", e)
        }
    }

    private fun createCore(pluginsFile: File): Core =
        if (pluginsFile.exists()) {
            debugLog { "Creating Core with config: ${pluginsFile.absolutePath}" }
            Core(pluginsFile.absolutePath)
        } else {
            debugLog { "Creating Core without config" }
            Core()
        }

    private fun loadAndCompileModel(activeCore: Core) {
        core = activeCore
        model = activeCore.read_model(activeModelXmlPath) ?: error("Failed to read model")

        val activeModel = model ?: error("Model was not stored")
        configureInput(activeModel.input() ?: error("No input in model"))
        configureOutput(activeModel.output() ?: error("No output in model"))

        debugLog { "Compiling model for device: $deviceName" }
        compiledModel = activeCore.compile_model(activeModel, deviceName) ?: error("Failed to compile model")
        inferRequest = compiledModel?.create_infer_request() ?: error("Failed to create infer request")
    }

    private fun configureInput(input: Output) {
        inputName = safeGetAnyName(input) ?: "input"
        inputShape = input.get_shape().map { it.toLong() }.toLongArray()
        inputElementType = input.get_element_type().toString()

        debugLog { "Input - name: $inputName, shape: ${inputShape.contentToString()}, type: $inputElementType" }
    }

    private fun configureOutput(output: Output) {
        outputName = safeGetAnyName(output) ?: "output"
        outputShape = output.get_shape().map { it.toLong() }.toLongArray()
        outputElementType = output.get_element_type().toString()

        debugLog { "Output - name: $outputName, shape: ${outputShape.contentToString()}, type: $outputElementType" }
    }

    private fun safeGetAnyName(output: Output): String? =
        try {
            output.get_any_name()
        } catch (e: Exception) {
            Log.w(TAG, "Tensor has no OpenVINO name, using <unnamed>: ${e.message}")
            "<unnamed>"
        }

    fun getModelInfo(): String {
        if (!isInitialized) {
            return "Engine not initialized"
        }
        return buildString {
            appendLine("Model loaded successfully")
            appendLine("Device: $deviceName")
            appendLine("Input: $inputName $inputElementType ${inputShape.contentToString()}")
            appendLine("Output: $outputName $outputElementType ${outputShape.contentToString()}")
        }
    }

    fun isReady(): Boolean = isInitialized

    fun release() {
        try {
            inferRequest = null
            compiledModel = null
            model = null
            core = null
            isInitialized = false
            debugLog { "Engine resources released" }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources: ${e.message}", e)
        }
    }

    fun test(): Boolean =
        try {
            debugLog { "=== Starting OpenVINO Engine Test ===" }
            initialize()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Test failed: ${e.message}", e)
            false
        }

    protected fun finalize() {
        release()
    }

    private fun debugLog(message: () -> String) {
        Log.d(TAG, message())
    }
}

// Data class для результатов детекции
data class YoloDetection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val classId: Int
)
