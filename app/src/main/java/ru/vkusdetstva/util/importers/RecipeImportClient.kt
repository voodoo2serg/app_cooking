package ru.vkusdetstva.util.importers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * «Лёгкий аутсорс» распознавания: фото страницы уходит одним HTTPS-запросом
 * в выбранный сервис (Google Gemini или OpenAI), назад приходит JSON-рецепт.
 * Свой сервер не нужен — пользователь вставляет свой API-ключ в настройках.
 */
enum class ImportEngine(val label: String, val defaultModel: String) {
    Gemini("Google Gemini", "gemini-2.5-flash"),
    OpenAI("OpenAI · GPT", "gpt-4o-mini");

    companion object {
        fun byId(id: String?): ImportEngine = entries.firstOrNull { it.name == id } ?: Gemini
    }
}

object RecipeImportClient {

    private const val MAX_PHOTOS = 5
    private const val MAX_SIDE_PX = 1600

    private const val PROMPT = """Ты помогаешь вести семейную книгу рецептов. На фотографиях — страницы с рецептом, печатные или рукописные. Извлеки рецепт целиком и верни только JSON без пояснений, строго по схеме:
{"title":"название блюда","story":"короткая история или примечание о рецепте, если есть на странице, иначе пустая строка","ingredients":["ингредиент — количество"],"steps":["шаг приготовления"],"notes":"заметки, если есть, иначе пустая строка"}
Правила: сохраняй формулировки, количества и единицы как в оригинале, не переводи и не пересказывай; каждый ингредиент — отдельный элемент списка; каждый шаг — отдельный элемент; рукописные цифры читай внимательно. Если на фотографиях нет рецепта, верни {"error":"no_recipe"}."""

    /** Распознаёт рецепт по фотографиям страниц. Вызывать из Dispatchers.IO. */
    fun recognize(context: Context, engine: ImportEngine, model: String,
                  apiKey: String, photoPaths: List<String>): RecipeDraft {
        require(apiKey.isNotBlank()) { "Сначала укажите API-ключ в настройках импорта." }
        require(photoPaths.isNotEmpty()) { "Добавьте хотя бы одну фотографию страницы." }
        val images = photoPaths.take(MAX_PHOTOS).map { encodePage(File(it)) }
        val text = when (engine) {
            ImportEngine.Gemini -> gemini(model.ifBlank { engine.defaultModel }, apiKey, images)
            ImportEngine.OpenAI -> openai(model.ifBlank { engine.defaultModel }, apiKey, images)
        }
        return RecipeImportParser.parseModelResponse(text)
    }

    private fun gemini(model: String, apiKey: String, images: List<String>): String {
        val parts = JSONArray().put(JSONObject().put("text", PROMPT))
        images.forEach { data ->
            parts.put(JSONObject().put("inline_data",
                JSONObject().put("mime_type", "image/jpeg").put("data", data)))
        }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig",
                JSONObject().put("temperature", 0.2).put("response_mime_type", "application/json"))
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val response = post(url, body.toString().toByteArray(), mapOf("x-goog-api-key" to apiKey))
        val obj = JSONObject(response)
        val candidates = obj.optJSONArray("candidates") ?: throw IOException("Gemini вернул пустой ответ.")
        val answerParts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until answerParts.length()) sb.append(answerParts.getJSONObject(i).optString("text"))
        return sb.toString()
    }

    private fun openai(model: String, apiKey: String, images: List<String>): String {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", PROMPT))
        images.forEach { data ->
            content.put(JSONObject().put("type", "image_url")
                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$data")))
        }
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val url = URL("https://api.openai.com/v1/chat/completions")
        val response = post(url, body.toString().toByteArray(), mapOf("Authorization" to "Bearer $apiKey"))
        val obj = JSONObject(response)
        return obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun post(url: URL, body: ByteArray, headers: Map<String, String>): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.readText().orEmpty().take(400)
                throw IOException(friendlyError(code, error))
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyError(code: Int, body: String): String = when (code) {
        401, 403 -> "Сервис не принял ключ ($code). Проверьте API-ключ в настройках импорта."
        404 -> "Модель не найдена (404). Проверьте название модели в настройках импорта."
        429 -> "Лимит запросов у сервиса исчерпан (429). Подождите и попробуйте снова."
        else -> "Ошибка сервиса ($code): ${body.ifBlank { "нет деталей" }}"
    }

    /** Читает фото, уменьшает до ~1600px по длинной стороне, сжимает в JPEG и кодирует base64. */
    private fun encodePage(file: File): String {
        require(file.exists()) { "Фотография недоступна: ${file.name}" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IOException("Не удалось прочитать фотографию: ${file.name}")
        val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_SIDE_PX) {
            val scale = MAX_SIDE_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            out.toByteArray()
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
