package ru.vkusdetstva.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Дисковое автосохранение черновиков: любое изменение в форме тут же пишется в
 * SharedPreferences (apply — асинхронно и дёшево), поэтому ввод переживает
 * поворот экрана, входящий звонок, убийство процесса системой и даже
 * перезапуск приложения. Черновик стирается, когда пользователь сохраняет
 * результат, и восстанавливается, когда форма открывается снова.
 *
 * Ключи: "recipe-new", "recipe-import", "recipe-edit-<id>", "person-new",
 * "person-edit-<id>", "moment-new", "moment-edit-<id>", "event-new",
 * "event-edit-<id>", "version-<recipeId>", "author".
 */
object Drafts {
    private const val FILE = "drafts"

    fun save(context: Context, key: String, json: JSONObject) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(key, json.toString()).apply()
    }

    fun load(context: Context, key: String): JSONObject? {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(key, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun clear(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(key).apply()
    }
}

private fun JSONObject.stringList(key: String = "photos"): List<String> =
    optJSONArray(key)?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()

private fun JSONArray.longList(): List<Long> = (0 until length()).map { getLong(it) }

fun Recipe.toJson(): JSONObject = JSONObject()
    .put("title", title)
    .put("personId", personId ?: 0L)
    .put("story", story)
    .put("ingredients", ingredients)
    .put("steps", steps)
    .put("notes", notes)
    .put("photos", JSONArray(photos))
    .put("audioPath", audioPath.orEmpty())

fun recipeFromJson(o: JSONObject): Recipe = Recipe(
    title = o.optString("title"),
    personId = o.optLong("personId").takeIf { it != 0L },
    story = o.optString("story"),
    ingredients = o.optString("ingredients"),
    steps = o.optString("steps"),
    notes = o.optString("notes"),
    photos = o.stringList(),
    audioPath = o.optString("audioPath").takeIf { it.isNotEmpty() }
)

fun Person.toJson(): JSONObject = JSONObject()
    .put("name", name).put("relation", relation).put("years", years)
    .put("story", story).put("photos", JSONArray(photos))

fun personFromJson(o: JSONObject): Person = Person(
    name = o.optString("name"), relation = o.optString("relation"),
    years = o.optString("years"), story = o.optString("story"), photos = o.stringList())

fun AuthorProfile.toJson(): JSONObject = JSONObject()
    .put("name", name).put("tagline", tagline).put("bio", bio).put("photos", JSONArray(photos))

fun authorFromJson(o: JSONObject): AuthorProfile = AuthorProfile(
    name = o.optString("name"), tagline = o.optString("tagline"),
    bio = o.optString("bio"), photos = o.stringList())

fun FamilyMoment.toJson(): JSONObject = JSONObject()
    .put("title", title).put("story", story).put("people", people).put("photos", JSONArray(photos))

fun momentFromJson(o: JSONObject): FamilyMoment = FamilyMoment(
    title = o.optString("title"), story = o.optString("story"),
    people = o.optString("people"), photos = o.stringList())

fun FamilyEvent.toJson(): JSONObject = JSONObject()
    .put("title", title).put("story", story)
    .put("recipeIds", JSONArray(recipeIds)).put("photos", JSONArray(photos))

fun eventFromJson(o: JSONObject): FamilyEvent = FamilyEvent(
    title = o.optString("title"), story = o.optString("story"),
    recipeIds = o.optJSONArray("recipeIds")?.longList().orEmpty(), photos = o.stringList())

fun RecipeVersion.toJson(): JSONObject = JSONObject()
    .put("personName", personName).put("change", change).put("note", note).put("photos", JSONArray(photos))

fun versionFromJson(o: JSONObject): RecipeVersion = RecipeVersion(
    recipeId = 0L, personName = o.optString("personName"), change = o.optString("change"),
    note = o.optString("note"), photos = o.stringList())
