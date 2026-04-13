import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.itlab.ai.OpenVINOTest
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.openvino.java.OpenVINO
import org.openvino.java.core.Core
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

interface OpenVINOTestNative : com.sun.jna.Library {
    fun ov_core_create(core: PointerByReference): Int
}

fun extractLibraryFromAPK(context: Context, libName: String): File? {
    return try {
        val apkPath = context.packageCodePath
        val destDir = context.cacheDir
        val destFile = File(destDir, libName)

        // Если уже извлечено, не извлекаем заново
        if (destFile.exists()) {
            Log.d(TAG, "Library already extracted: ${destFile.absolutePath}")
            return destDir
        }

        Log.d(TAG, "Extracting $libName from APK: $apkPath")

        ZipFile(apkPath).use { zip ->
            // Ищем файл в APK (проверяем оба возможных пути)
            var entry = zip.getEntry("lib/arm64-v8a/$libName")
            if (entry == null) {
                entry = zip.getEntry("lib/armeabi-v7a/$libName")
            }
            if (entry == null) {
                Log.e(TAG, "Library $libName not found in APK")
                return null
            }

            zip.getInputStream(entry).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        // Даём права на выполнение
        destFile.setExecutable(true)

        Log.d(TAG, "Extracted to: ${destFile.absolutePath}")
        destFile

    } catch (e: Exception) {
        Log.e(TAG, "Failed to extract library: $libName", e)
        null
    }
}

@SuppressLint("UnsafeDynamicallyLoadedCode")
fun testDirectCoreCreate(context: Context) {
    try {
        // 1. Извлекаем библиотеку из APK
        val libFile = extractLibraryFromAPK(context, "libopencv_java4.so")
        if (libFile == null) {
            Log.e(TAG, "Failed to extract libopenvino_c.so")
            return
        }

        // 2. Загружаем библиотеку
        //System.load(libFile.absolutePath)
        val vino = OpenVINO.load()
        Log.d(TAG, "OpenVINO loaded")

        // 5. Загружаем OpenCV (передаём папку)
        OpenVINO.loadCvDll(libFile.absolutePath)
        Log.d(TAG, "OpenCV loaded")
        Log.d(TAG, "Library loaded from: ${libFile.absolutePath}")

        // 3. Создаём JNA интерфейс
        //val native = Native.load(libFile.absolutePath, OpenVINOTestNative::class.java)
        //Log.d(TAG, "Native interface loaded")

        // 4. Вызываем ov_core_create
        //val corePtr = PointerByReference()
        //val status = native.ov_core_create(corePtr)

        //Log.d(TAG, "ov_core_create status: $status")

        //if (status == 0 && corePtr.value != null) {
          //  Log.d(TAG, "✅ Core created successfully! Pointer: ${corePtr.value}")
        //} else {
          //  Log.e(TAG, "❌ ov_core_create failed with status: $status")
        //}
        val core = Core("")
        Log.d(TAG, "Core created successfully!")

        core.free()

    } catch (e: Exception) {
        Log.e(TAG, "Direct core creation failed", e)
    }
}
