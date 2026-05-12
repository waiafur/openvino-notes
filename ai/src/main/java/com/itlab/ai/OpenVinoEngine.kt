package com.itlab.ai


import android.content.Context
import android.util.Log
import org.intel.openvino.*
import java.io.File
import java.io.FileOutputStream

class OpenVinoEngine(
    private val context: Context,  // ← нужен для доступа к assets
    private val modelXmlPath: String,
    private val deviceName: String = "CPU"
) {
    
    fun runLlmSummary(text: String): String = text

    fun runLlmTagging(text: String): String = text

    fun runYoloTagging(imageSource: String): String = imageSource

    companion object {
        private const val TAG = "OpenVinoEngine"
    }

    private var core: Core? = null
    private var model: Model? = null
    private var compiledModel: CompiledModel? = null
    private var inferRequest: InferRequest? = null

    private var isInitialized = false

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

    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "Initializing OpenVINO Engine...")
            Log.d(TAG, "Model path: $modelXmlPath")

            // Проверяем, что файл модели существует
            val xmlFile = File(modelXmlPath)
            if (!xmlFile.exists()) {
                Log.e(TAG, "❌ Model file not found: $modelXmlPath")
                return false
            }

            // Копируем plugins.xml из assets в filesDir
            val pluginsFile = File(context.filesDir, "plugins.xml")
            if (!pluginsFile.exists()) {
                try {
                    context.assets.open("plugins.xml").use { input ->
                        FileOutputStream(pluginsFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "plugins.xml copied to: ${pluginsFile.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "No plugins.xml in assets, trying without...")
                }
            }


            // Создаём Core с путём к plugins.xml (если файл есть)
            core = if (pluginsFile.exists()) {
                Log.d(TAG, "Creating Core with config: ${pluginsFile.absolutePath}")
                Core() // создание без явного указания пути к плагину
                //Core(pluginsFile.absolutePath) // создание с путём к плагину перемещённому через assets
            } else {
                Log.d(TAG, "Creating Core without config")
                Core()
            }

            val devices = core?.get_available_devices()
            Log.d(TAG, "Available devices: $devices")

            if (devices.isNullOrEmpty()) {
                Log.e(TAG, "No OpenVINO devices available")
                return false
            }

            // Читаем модель
            model = core?.read_model(modelXmlPath)
                ?: throw IllegalStateException("Failed to read model")

            // Получаем информацию о входе
            val input = model?.input()
                ?: throw IllegalStateException("No input in model")

            inputName = safeGetAnyName(input) ?: "input"
            inputShape = input.get_shape().map { it.toLong() }.toLongArray()
            inputElementType = input.get_element_type().toString()

            Log.d(TAG, "Input - name: $inputName, shape: ${inputShape.contentToString()}, type: $inputElementType")

            // Получаем информацию о выходе
            val output = model?.output()
                ?: throw IllegalStateException("No output in model")

            outputName = safeGetAnyName(output) ?: "output"
            outputShape = output.get_shape().map { it.toLong() }.toLongArray()
            outputElementType = output.get_element_type().toString()

            Log.d(TAG, "Output - name: $outputName, shape: ${outputShape.contentToString()}, type: $outputElementType")

            // Компилируем модель
            Log.d(TAG, "Compiling model for device: $deviceName")
            compiledModel = core?.compile_model(model, deviceName)
                ?: throw IllegalStateException("Failed to compile model")

            // Создаём infer request
            inferRequest = compiledModel?.create_infer_request()
                ?: throw IllegalStateException("Failed to create infer request")

            isInitialized = true
            Log.d(TAG, "✅ Engine initialized successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine: ${e.message}", e)
            isInitialized = false
            false
        }
    }

    private fun safeGetAnyName(output: Output): String? {
        return try {
            output.get_any_name()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get any name: ${e.message}")
            "<unnamed>"
        }
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
            Log.d(TAG, "Engine resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources: ${e.message}", e)
        }
    }

    fun test(): Boolean {
        return try {
            Log.d(TAG, "=== Starting OpenVINO Engine Test ===")
            Log.d(TAG, "Model path: $modelXmlPath")
            if (!File(modelXmlPath).exists()) {
                Log.e(TAG, "❌ Model file not found: $modelXmlPath")
                return false
            }
            initialize()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Test failed: ${e.message}", e)
            false
        }
    }

    protected fun finalize() {
        release()
    }
}
