package com.itlab.ai

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.itlab.domain.ai.NoteAiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class NoteAiServiceImpl(
    private val context: Context
) : NoteAiService {

    companion object {
        private const val TAG = "NoteAiServiceImpl"
    }

    // Инициализация YOLO детектора (лениво, чтобы не тормозить старт)
    private val yoloDetector by lazy {
        YoloDetector(context).apply {
            initialize()
            Log.d(TAG, "YOLO детектор инициализирован: ${isReady()}")
        }
    }

    override suspend fun summarize(text: String): String = withContext(Dispatchers.IO) {
        // TODO: Cotype для пересказа
        return@withContext "Функция пересказа в разработке"
    }

    override suspend fun tagTXT(text: String): Set<String> = withContext(Dispatchers.IO) {
        // TODO: Cotype для тегирования текста
        return@withContext emptySet()
    }

    override suspend fun tagIMGs(img: List<String>): Set<String> = withContext(Dispatchers.IO) {
        if (img.isEmpty()) {
            Log.d(TAG, "Нет изображений для анализа")
            return@withContext emptySet()
        }

        Log.d(TAG, "Анализ ${img.size} изображений")

        val allTags = mutableSetOf<String>()

        for (imageUri in img) {
            try {
                val uri = imageUri.toUri()
                val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }

                if (bitmap != null) {
                    val detections = yoloDetector.detect(bitmap)
                    Log.d(TAG, "Обнаружено ${detections.size} объектов")

                    val tags = detections.map { detection ->
                        getClassName(detection.classId)
                    }.toSet()

                    allTags.addAll(tags)
                    bitmap.recycle()
                } else {
                    Log.e(TAG, "Не удалось загрузить изображение: $imageUri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки изображения: $imageUri", e)
            }
        }

        Log.d(TAG, "Сгенерированные теги: $allTags")
        return@withContext allTags
    }

    private fun getClassName(classId: Int): String {
        return when (classId) {
            0 -> "person"
            1 -> "bicycle"
            2 -> "car"
            3 -> "motorcycle"
            4 -> "airplane"
            5 -> "bus"
            6 -> "train"
            7 -> "truck"
            8 -> "boat"
            9 -> "traffic light"
            10 -> "fire hydrant"
            11 -> "stop sign"
            12 -> "parking meter"
            13 -> "bench"
            14 -> "bird"
            15 -> "cat"
            16 -> "dog"
            17 -> "horse"
            18 -> "sheep"
            19 -> "cow"
            20 -> "elephant"
            else -> "object_$classId"
        }
    }
}
