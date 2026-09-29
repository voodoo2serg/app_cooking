package ru.vkusdetstva.util.importers

import org.json.JSONArray
import org.json.JSONObject

/** Черновик рецепта, распознанный с фото или вставленный из буфера. */
data class RecipeDraft(
    val title: String,
    val story: String = "",
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val notes: String = ""
)

/** Разбор ответа модели. Чистые функции без Android-зависимостей — покрыты unit-тестами. */
object RecipeImportParser {

    private val fenceRegex = Regex("```[a-zA-Z]*\\s*([\\s\\S]*?)```")

    /** Достаёт JSON-объект из ответа модели: убирает ```-ограждения и текст вокруг. */
    fun extractJson(text: String): String? {
        val fenced = fenceRegex.find(text)?.groupValues?.get(1)
        val source = fenced ?: text
        val start = source.indexOf('{')
        val end = source.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return source.substring(start, end + 1)
    }

    /** Разбирает ответ модели в черновик; бросает IllegalArgumentException с понятной причиной. */
    fun parseModelResponse(text: String): RecipeDraft {
        val json = extractJson(text)
            ?: throw IllegalArgumentException("В ответе сервиса нет данных. Попробуйте ещё раз.")
        val obj = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("Сервис вернул ответ не в том формате. Попробуйте ещё раз.") }
        if (obj.optString("error").isNotBlank()) {
            throw IllegalArgumentException("На фото не видно рецепта. Снимите страницу ближе, ровно и при хорошем свете.")
        }
        val title = obj.optString("title").trim()
        if (title.isBlank()) {
            throw IllegalArgumentException("Не удалось прочитать название блюда. Проверьте фото или впишите название вручную.")
        }
        return RecipeDraft(
            title = title.take(120),
            story = obj.optString("story").trim(),
            ingredients = stringList(obj, "ingredients"),
            steps = stringList(obj, "steps"),
            notes = obj.optString("notes").trim()
        )
    }

    private fun stringList(obj: JSONObject, key: String): List<String> {
        val value = obj.opt(key) ?: return emptyList()
        return when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { i ->
                value.optString(i).trim().takeIf { it.isNotBlank() }
            }
            else -> value.toString().split('\n').map { it.trim() }.filter { it.isNotBlank() }
        }
    }
}

/** Разбор текста, вставленного из буфера (например, скопированного через Google Lens). */
object ClipboardRecipeParser {

    private val ingredientsMarker = Regex("ингредиент", RegexOption.IGNORE_CASE)
    private val stepsMarker = Regex("приготовлен|способ|как готовить|пошаг|этапы|шаги", RegexOption.IGNORE_CASE)

    fun parse(text: String): RecipeDraft? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val title = lines.first().take(120)
        val rest = lines.drop(1)
        val ingredientsStart = rest.indexOfFirst { ingredientsMarker.containsMatchIn(it) && it.length < 60 }
        val stepsStart = rest.indexOfFirst { stepsMarker.containsMatchIn(it) && it.length < 60 }
        val sections: Pair<List<String>, List<String>> = when {
            ingredientsStart >= 0 && stepsStart >= 0 && ingredientsStart < stepsStart ->
                rest.subList(ingredientsStart + 1, stepsStart) to rest.subList(stepsStart + 1, rest.size)
            ingredientsStart >= 0 && stepsStart >= 0 && stepsStart < ingredientsStart ->
                rest.subList(stepsStart + 1, ingredientsStart) to rest.subList(ingredientsStart + 1, rest.size)
            ingredientsStart >= 0 -> rest.subList(ingredientsStart + 1, rest.size) to emptyList()
            stepsStart >= 0 -> emptyList<String>() to rest.subList(stepsStart + 1, rest.size)
            else -> emptyList<String>() to rest
        }
        val (ingredients, steps) = sections
        return RecipeDraft(title = title, ingredients = ingredients, steps = steps)
    }
}
