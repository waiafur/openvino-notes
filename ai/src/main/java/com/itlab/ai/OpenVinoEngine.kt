package com.itlab.ai

import android.content.Context
import android.util.Log
import org.intel.openvino.CompiledModel
import org.intel.openvino.Core
import org.intel.openvino.InferRequest
import org.intel.openvino.Model
import org.intel.openvino.Output
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
class OpenVinoEngine(
    private val context: Context? = null,
    private val modelXmlPath: String = "",
    private val deviceName: String = "CPU",
) {
    fun runLlmSummary(text: String): String = text

    fun runLlmTagging(text: String): String = text

    fun runYoloTagging(imageSource: String): String = imageSource

    companion object {
        private const val TAG = "OpenVinoEngine"
        private const val DEFAULT_MODEL_ASSET_DIR = "models/yolo26n_openvino_model"
        private const val DEFAULT_MODEL_XML = "yolo26n.xml"
    }

    private var core: Core? = null
    private var model: Model? = null
    private var compiledModel: CompiledModel? = null
    private var inferRequest: InferRequest? = null

    private var isInitialized = false
    private var activeModelXmlPath = modelXmlPath

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
        debugLog { "Initializing OpenVINO Engine..." }
        debugLog { "Model path: $modelXmlPath" }
        isInitialized = false

        val appContext = context
        return when {
            appContext == null -> {
                Log.e(TAG, "Android context is required to initialize OpenVINO")
                false
            }
            else -> initializeWithResolvedModel(appContext)
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
        //if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message())
        //}
    }
}
