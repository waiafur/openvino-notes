package com.itlab.ai

import android.annotation.SuppressLint
import android.content.ContentValues

import android.util.Log
import org.openvino.java.OpenVINO
import org.openvino.java.core.Core
import java.io.File
import android.content.Context
import com.sun.jna.Native
import com.sun.jna.ptr.PointerByReference
import testDirectCoreCreate
import java.io.FileOutputStream


// TODO
// 1. Сделать сэмпл моделей в этом тесте
// 2. Добавить всё новое в лок депенденсис и раскомментировать
// 3. Понять почему 17 сдк и сделать в зависимости от того зачем оно
// 4. Начать интегрировать методы для интерфейса Златы Л
// Не забыть убрать из аппа вызов теста!

object OpenVINOTest {

    private const val TAG = "OpenVINOTest"
    fun OpenVINOTest(context: Context) {
        testVersion(context)
        testCoreAPI(context)
        testTensorCreation(context)
        testModelLoading(context, "resnet50-v2-7.xml")
        inspectAPI()
        testDevices(context)
        testDirectCoreCreate(context)
        checkSymbols(context)
    }

    // --- Тест версии (работает) ---
    fun testVersion(context: Context) {
        try {
            System.setProperty("jna.library.path", context.applicationInfo.nativeLibraryDir)
            val vino = OpenVINO.load()
            Log.d(TAG, "OpenVINO library loaded successfully!")

            val version = vino.version
            Log.i(TAG, "---- OpenVINO INFO ----")
            Log.i(TAG, "Description: ${version.description}")
            Log.i(TAG, "Build number: ${version.buildNumber}")

        } catch (e: Exception) {
            Log.e(TAG, "OpenVINO test failed!", e)
        }
    }

    fun inspectAPI() {
        try {
            val vino = OpenVINO.load()
            val core = Core()

            Log.d(TAG, "=== Core methods ===")
            core.javaClass.methods.forEach { method ->
                Log.d(TAG, "  ${method.name} (${method.parameterTypes.joinToString { it.simpleName }})")
            }

            // Попробуем создать тензор
            Log.d(TAG, "=== Tensor methods ===")
            val shape = arrayOf(1L, 3L, 224L, 224L)
            // Здесь нужно подобрать правильный способ создания тензора

        } catch (e: Exception) {
            Log.e(TAG, "Inspect failed", e)
        }
    }
    // --- Тест Core API ---
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun testCoreAPI(context: Context) {
        System.setProperty("OPENVINO_LOG_LEVEL", "DEBUG")
        System.setProperty("OPENVINO_LOG_LEVEL", "0") // "0" это уровень DEBUG
        try {
            val apkPath = context.packageCodePath
            val nativeLibDir = apkPath + "!/lib/arm64-v8a"
            val opencvPath = "${context.applicationInfo.sourceDir}!/lib/arm64-v8a/libopencv_java4.so"
            //System.load(opencvPath)
            Log.d(TAG, "Native lib dir: $nativeLibDir")

            // 2. Проверяем, есть ли файл (опционально, для отладки)
            val opencvFile = File("$nativeLibDir/libopencv_java4.so")
            Log.d(TAG, "OpenCV file exists: ${opencvFile.exists()}")

            // 3. Устанавливаем путь для JNA
            System.setProperty("jna.library.path", nativeLibDir)

            // 4. Загружаем OpenVINO
            val vino = OpenVINO.load()
            Log.d(TAG, "OpenVINO loaded")

            // 5. Загружаем OpenCV (передаём папку)
            OpenVINO.loadCvDll(nativeLibDir)
            Log.d(TAG, "OpenCV loaded")

            // 6. Создаём Core
            val core = Core("")
            Log.d(TAG, "Core created successfully!")

            core.free()

        } catch (e1: Exception) {
            Log.e(TAG, "Standard Core creation failed", e1)

            // Вариант 2: если есть конструктор с параметрами
            try {
                Log.d(TAG, "Trying alternative approach...")
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                val pluginsPath = "$nativeLibDir/plugins.xml"
                // Некоторые версии OpenVINO требуют путь к plugins.xml
                 val core = Core(pluginsPath)

            } catch (e2: Exception) {
                Log.e(TAG, "Alternative also failed", e2)
            }
        }
    }
    fun testDevices(context: Context) {
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val core = Core("$nativeLibDir/plugins.xml")
            val devices = core.availableDevices
            Log.d(TAG, "Devices: ${devices.joinToString()}")
            core.free()
        } catch (e: Exception) {
            Log.e(TAG, "Failed: ${e.message}")
        }
    }

    // --- Тест создания тензора ---
    fun testTensorCreation(context: Context) {
        try {
            System.setProperty("jna.library.path", context.applicationInfo.nativeLibraryDir)
            val vino = OpenVINO.load()

            // Пробуем разные способы создания тензора
            // Вариант 1: через массив Long
            val dimensions = longArrayOf(1, 3, 224, 224)

            // Вариант 2: через IntArray
            val intDims = intArrayOf(1, 3, 224, 224)

            Log.d(TAG, "Trying to create tensor...")

            // Здесь нужно подобрать правильный конструктор
            // Возможно, тензор создаётся через Core, а не напрямую

            Log.i(TAG, "Tensor test completed")

        } catch (e: Exception) {
            Log.e(TAG, "Tensor test failed!", e)
        }
    }

    // --- Копирование файла из assets ---
    private fun copyFileFromAssets(context: Context, fileName: String): String? {
        return try {
            val destFile = File(context.cacheDir, fileName)
            if (destFile.exists()) {
                destFile.delete()
            }
            context.assets.open(fileName).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file: $fileName", e)
            null
        }
    }

    // --- Загрузка модели (если есть файл) ---
    fun testModelLoading(context: Context, modelFileName: String) {
        try {
            System.setProperty("jna.library.path", context.applicationInfo.nativeLibraryDir)
            val vino = OpenVINO.load()
            val core = Core()

            val modelPath = copyFileFromAssets(context, modelFileName)
            if (modelPath == null) {
                Log.e(TAG, "Failed to copy model")
                return
            }

            // Пробуем загрузить модель
            Log.d(TAG, "Loading model from: $modelPath")
            val model = core.readModel(modelPath)
            Log.d(TAG, "Model loaded: ${model}")

            // Выводим информацию о модели
            Log.d(TAG, "Model class: ${model.javaClass.name}")
            model.javaClass.declaredMethods.forEach { method ->
                Log.d(TAG, "  Model method: ${method.name}")
            }

            core.free()
            Log.i(TAG, "Model loading test completed")

        } catch (e: Exception) {
            Log.e(TAG, "Model loading failed!", e)
        }
    }
    interface OpenVINOTestNative : com.sun.jna.Library {
        fun ov_core_create(core: PointerByReference): Int
    }
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun checkSymbols(context: Context) {
        try {
            // Загружаем библиотеку по полному пути из APK
            val apkPath = context.packageCodePath
            val libPath = "$apkPath!/lib/arm64-v8a/libopenvino_c.so"

            Log.d(TAG, "Loading library from: $libPath")
            System.load(libPath)
            Log.d(TAG, "Library loaded successfully")

            // Теперь загружаем интерфейс
            val native = Native.load("openvino_c", OpenVINOTestNative::class.java)
            Log.d(TAG, "Native interface loaded, calling ov_core_create...")

            val corePtr = PointerByReference()
            val status = native.ov_core_create(corePtr)

            Log.d(TAG, "ov_core_create status: $status")

            if (status == 0 && corePtr.value != null) {
                Log.d(TAG, "✅ Core created! Pointer: ${corePtr.value}")
            } else {
                Log.e(TAG, "❌ ov_core_create failed with status: $status")
            }

        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
        }
    }

}

