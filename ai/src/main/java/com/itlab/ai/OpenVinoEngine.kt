package com.itlab.ai

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intel.openvino.CompiledModel
import org.intel.openvino.Core
import org.intel.openvino.InferRequest
import org.intel.openvino.Model
import org.intel.openvino.Output
import org.intel.openvino.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.app.ActivityManager

@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
class OpenVinoEngine(
    private val context: Context,
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

        fun getOptimalModelPath(context: Context): String {
            val coreCount = Runtime.getRuntime().availableProcessors()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamMB = memInfo.totalMem / (1024 * 1024)

            return if (coreCount >= 4 || totalRamMB >= 2048) {
                "models/yolo26n_openvino_model/yolo26n.xml"
            } else {
                "models/yolov10n_openvino_model/yolov10n.xml"
            }
        }
    }

    private var core: Core? = null
    private var model: Model? = null
    private var compiledModel: CompiledModel? = null
    private var inferRequest: InferRequest? = null

    private var isInitialized = false
    private var activeModelXmlPath = modelXmlPath

    private var classNames: List<String> = emptyList()

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

    suspend fun runYoloTagging(imageSource: String): String =
        withContext(Dispatchers.Default) {
            debugLog { "Running YOLO tagging on: $imageSource" }

            val bitmap =
                try {
                    android.graphics.BitmapFactory.decodeFile(imageSource)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading image", e)
                    null
                }

            return@withContext if (bitmap != null) {
                val detections = detectYolo(bitmap)
                bitmap.recycle()

                if (detections.isNotEmpty()) {
                    val tags =
                        detections
                            .map { it.classId }
                            .distinct()
                            .mapNotNull { classId -> getClassName(classId) }
                            .joinToString(",")
                    debugLog { "Detected tags: $tags" }
                    tags
                } else {
                    debugLog { "No objects detected" }
                    ""
                }
            } else {
                Log.e(TAG, "Failed to load image: $imageSource")
                ""
            }
        }

    private suspend fun detectYolo(bitmap: Bitmap): List<YoloDetection> {
        if (!isInitialized) return emptyList()

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


    private fun loadClassNames(appContext: Context): List<String> =
        try {
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

    private fun getClassName(classId: Int): String? =
        if (classId in classNames.indices) {
            classNames[classId]
        } else {
            Log.w(TAG, "Unknown class ID: $classId")
            "class_$classId"
        }

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

            inputData[i] = b
            inputData[area + i] = g
            inputData[2 * area + i] = r
        }

        return inputData
    }

    private fun createInputTensor(inputData: FloatArray): Tensor {
        val shape = intArrayOf(1, 3, INPUT_SIZE, INPUT_SIZE)
        return Tensor(shape, inputData)
    }

    private fun parseOutputTensor(outputTensor: Tensor?): List<YoloDetection> {
        if (outputTensor == null) {
            Log.e(TAG, "Output tensor is null")
            return emptyList()
        }

        return runCatching {
            val outputData = getTensorDataAsFloatArray(outputTensor)

            if (outputData.isEmpty()) {
                Log.e(TAG, "Output data is empty")
                return@runCatching emptyList()
            }

            val detections = buildDetections(outputData)
            applyNMS(detections, IOU_THRESHOLD)
        }.getOrElse { exception ->
            Log.e(TAG, "Failed to parse output tensor", exception)
            emptyList()
        }
    }

    // Вспомогательная функция без break/continue
    private fun buildDetections(outputData: FloatArray): List<YoloDetection> {
        if (outputData.size < MAX_DETECTIONS * 6) {
            Log.w(TAG, "Unexpected output format, size: ${outputData.size}")
            return emptyList()
        }

        return (0 until MAX_DETECTIONS)
            .mapNotNull { i ->
                val base = i * 6
                if (base + 5 >= outputData.size) return@mapNotNull null

                val confidence = outputData[base + 4]
                if (confidence <= CONF_THRESHOLD) return@mapNotNull null

                YoloDetection(
                    x1 = (outputData[base] * INPUT_SIZE).coerceIn(0f, INPUT_SIZE.toFloat()),
                    y1 = (outputData[base + 1] * INPUT_SIZE).coerceIn(0f, INPUT_SIZE.toFloat()),
                    x2 = (outputData[base + 2] * INPUT_SIZE).coerceIn(0f, INPUT_SIZE.toFloat()),
                    y2 = (outputData[base + 3] * INPUT_SIZE).coerceIn(0f, INPUT_SIZE.toFloat()),
                    confidence = confidence,
                    classId = outputData[base + 5].toInt(),
                )
            }
    }

    private fun getTensorDataAsFloatArray(tensor: Tensor): FloatArray =
        try {
            when (val data = tensor.data()) {
                is FloatArray -> data
                is ByteArray -> {
                    val floatArray = FloatArray(data.size / 4)
                    java.nio.ByteBuffer
                        .wrap(data)
                        .asFloatBuffer()
                        .get(floatArray)
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

    private fun applyNMS(
        detections: List<YoloDetection>,
        iouThreshold: Float,
    ): List<YoloDetection> {
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

    private fun calculateIOU(
        box1: YoloDetection,
        box2: YoloDetection,
    ): Float {
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

    suspend fun initialize(): Boolean = withContext(Dispatchers.Default) {
        debugLog { "Initializing OpenVINO Engine..." }
        isInitialized = false

        classNames = loadClassNames()
        if (classNames.isEmpty()) {
            Log.w(TAG, "No class names loaded, using IDs instead")
        }
        val result = initializeWithResolvedModel()
        debugLog { "Resolved model path: $activeModelXmlPath" }
        result
    }

    private fun loadClassNames(): List<String> {
        return try {
            context.assets.open(COCO_NAMES_FILE).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.map { it.trim() }.toList()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load class names", e)
            emptyList()
        }
    }

    private fun initializeWithResolvedModel(): Boolean {
        val resolvedModelXmlPath = resolveModelXmlPath() ?: return false
        activeModelXmlPath = resolvedModelXmlPath

        return if (!isModelExists(activeModelXmlPath)) {
            Log.e(TAG, "Model file not found: $activeModelXmlPath")
            false
        } else {
            initializeOpenVino()
        }
    }
    private fun isModelExists(modelPath: String): Boolean {
        return try {
            context.assets.open(modelPath).close()
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun resolveModelXmlPath(): String? {
        val modelDir = File(context.filesDir, DEFAULT_MODEL_ASSET_DIR)
        val modelXml = File(modelDir, DEFAULT_MODEL_XML)

        return when {
            modelXmlPath.isNotBlank() -> modelXmlPath
            modelXml.exists() -> modelXml.absolutePath
            copyAssetDirectory(DEFAULT_MODEL_ASSET_DIR, modelDir) -> modelXml.absolutePath
            else -> {
                Log.e(TAG, "Bundled model assets are missing: $DEFAULT_MODEL_ASSET_DIR")
                null
            }
        }
    }

    private fun copyAssetDirectory(assetPath: String, targetDir: File): Boolean {
        val entries = context.assets.list(assetPath) ?: return false
        if (entries.isEmpty()) return false

        targetDir.mkdirs()
        return entries.all { entry ->
            val childAssetPath = "$assetPath/$entry"
            val childTarget = File(targetDir, entry)
            val childEntries = context.assets.list(childAssetPath) ?: emptyArray()
            if (childEntries.isEmpty()) {
                copyAssetFile(childAssetPath, childTarget)
            } else {
                copyAssetDirectory(childAssetPath, childTarget)
            }
        }
    }

    private fun copyAssetFile(assetPath: String, targetFile: File): Boolean {
        return try {
            targetFile.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset: $assetPath", e)
            false
        }
    }

    private fun initializeOpenVino(): Boolean {
        return try {
            val pluginsFile = preparePluginsFile()
            val activeCore = createCore(pluginsFile)
            val devices = activeCore.get_available_devices()
            debugLog { "Available devices: $devices" }

            if (devices.isNullOrEmpty()) {
                Log.e(TAG, "No OpenVINO devices available")
                false
            } else {
                loadAndCompileModel(activeCore)
                isInitialized = true
                debugLog { "Engine initialized successfully" }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine: ${e.message}", e)
            false
        }
    }

    private fun preparePluginsFile(): File {
        val pluginsFile = File(context.filesDir, "plugins.xml")
        if (!pluginsFile.exists()) {
            copyPluginsFile(pluginsFile)
        }
        return pluginsFile
    }

    private fun copyPluginsFile(pluginsFile: File) {
        try {
            context.assets.open("plugins.xml").use { input ->
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
            core?.close()
            inferRequest?.close()
            compiledModel?.close()
            model?.close()
            isInitialized = false
            debugLog { "Engine resources released" }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources: ${e.message}", e)
        }
    }

    suspend fun test(): Boolean =
        try {
            debugLog { "=== Starting OpenVINO Engine Test ===" }
            initialize()
        } catch (e: Exception) {
            Log.e(TAG, "Test failed: ${e.message}", e)
            false
        }

    private fun debugLog(message: () -> String) {
        Log.d(TAG, message())
    }
}

data class YoloDetection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val classId: Int,
)
