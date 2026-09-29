package ru.vkusdetstva.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import ru.vkusdetstva.data.*
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FamilyArchive {
    fun export(context: Context, uri: Uri, people: List<Person>, recipes: List<Recipe>,
               versions: List<RecipeVersion>, moments: List<FamilyMoment>, author: AuthorProfile? = null,
               events: List<FamilyEvent> = emptyList()) {
        val files = linkedMapOf<String, File>()
        fun media(path: String?): String {
            val file = path?.let(::File) ?: return ""
            require(file.isFile) { "Не найден медиафайл: ${file.name}" }
            val key = "media/${files.size}_${file.name}"
            files[key] = file
            return key
        }
        fun photos(paths: List<String>) = JSONArray().apply { paths.forEach { put(media(it)) } }
        val prefs = context.getSharedPreferences("settings", 0)
        val root = JSONObject().put("format", 1)
            .put("familyName", prefs.getString("family_name", "Моя семья"))
            .put("bookTitle", prefs.getString("book_title", "Вкус нашего дома"))
        root.put("people", JSONArray().apply { people.forEach { p -> put(JSONObject()
            .put("id", p.id).put("name", p.name).put("relation", p.relation).put("years", p.years)
            .put("story", p.story).put("photos", photos(p.photos))) } })
        root.put("recipes", JSONArray().apply { recipes.forEach { r -> put(JSONObject()
            .put("id", r.id).put("title", r.title).put("personId", r.personId ?: 0)
            .put("story", r.story).put("ingredients", r.ingredients).put("steps", r.steps)
            .put("notes", r.notes).put("photos", photos(r.photos)).put("audio", media(r.audioPath))
            .put("timesCooked", r.timesCooked).put("taste", r.taste).put("ease", r.ease)
            .put("memory", r.memory).put("createdAt", r.createdAt).put("likes", r.likes)
            .put("eventTag", r.eventTag)) } })
        root.put("versions", JSONArray().apply { versions.forEach { v -> put(JSONObject()
            .put("id", v.id).put("recipeId", v.recipeId).put("personName", v.personName)
            .put("change", v.change).put("note", v.note).put("photos", photos(v.photos))
            .put("createdAt", v.createdAt)) } })
        root.put("moments", JSONArray().apply { moments.forEach { m -> put(JSONObject()
            .put("id", m.id).put("title", m.title).put("story", m.story).put("people", m.people)
            .put("photos", photos(m.photos)).put("createdAt", m.createdAt).put("likes", m.likes)
            .put("eventTag", m.eventTag)) } })
        root.put("events", JSONArray().apply { events.forEach { event -> put(JSONObject()
            .put("id", event.id).put("title", event.title).put("story", event.story)
            .put("recipeIds", JSONArray(event.recipeIds)).put("photos", photos(event.photos))
            .put("createdAt", event.createdAt)) } })
        author?.let { a -> root.put("author", JSONObject()
            .put("name", a.name).put("tagline", a.tagline).put("bio", a.bio)
            .put("photos", photos(a.photos))) }
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(root.toString().toByteArray()); zip.closeEntry()
                files.forEach { (name, file) -> zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
            }
        } ?: error("Невозможно записать архив")
    }

    suspend fun import(context: Context, uri: Uri) {
        val folder = File(context.cacheDir, "restore_${UUID.randomUUID()}").apply { mkdirs() }
        val media = mutableMapOf<String, File>()
        var manifest: JSONObject? = null
        var total = 0L
        var entries = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(!entry.isDirectory && (entry.name == "manifest.json" || Regex("media/[A-Za-z0-9_.-]{1,100}").matches(entry.name)))
                    require(++entries <= 1000) { "Слишком много файлов в архиве" }
                    require(entry.name != "manifest.json" || manifest == null) { "Повторное описание архива" }
                    require(entry.name == "manifest.json" || entry.name !in media) { "Повторный файл в архиве" }
                    val limit = if (entry.name == "manifest.json") 8_000_000L else 25_000_000L
                    val bytes = java.io.ByteArrayOutputStream().use { out ->
                        val buffer = ByteArray(8192)
                        var size = 0L
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            size += n; total += n
                            require(size <= limit && total <= 250_000_000L) { "Архив слишком большой" }
                            out.write(buffer, 0, n)
                        }
                        out.toByteArray()
                    }
                    if (entry.name == "manifest.json") manifest = JSONObject(String(bytes)) else {
                        val file = File(folder, entry.name.substringAfter('/')); file.writeBytes(bytes); media[entry.name] = file
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            } } ?: error("Архив недоступен")
            val data = requireNotNull(manifest) { "Нет описания архива" }
            require(data.getInt("format") == 1)
            fun getPhotos(obj: JSONObject): List<String> = obj.getJSONArray("photos").let { arr ->
                (0 until arr.length()).mapNotNull { media[arr.getString(it)]?.absolutePath }
            }
            val people = data.getJSONArray("people").objects().map { p -> Person(p.getLong("id"), p.getString("name"),
                p.getString("relation"), p.getString("years"), p.getString("story"), getPhotos(p)) }
            val recipes = data.getJSONArray("recipes").objects().map { r -> Recipe(
                r.getLong("id"), r.getString("title"), r.getLong("personId").takeIf { it != 0L },
                r.getString("story"), r.getString("ingredients"), r.getString("steps"), r.getString("notes"),
                getPhotos(r), media[r.getString("audio")]?.absolutePath, r.getInt("timesCooked"),
                r.getInt("taste"), r.getInt("ease"), r.getInt("memory"), r.getLong("createdAt"),
                r.optInt("likes"), r.optString("eventTag")) }
            val versions = data.getJSONArray("versions").objects().map { v -> RecipeVersion(
                v.getLong("id"), v.getLong("recipeId"), v.getString("personName"), v.getString("change"),
                v.getString("note"), getPhotos(v), v.getLong("createdAt")) }
            val moments = data.getJSONArray("moments").objects().map { m -> FamilyMoment(
                m.getLong("id"), m.getString("title"), m.getString("story"), m.getString("people"),
                getPhotos(m), m.getLong("createdAt"), m.optInt("likes"), m.optString("eventTag")) }
            val events = data.optJSONArray("events")?.objects().orEmpty().map { event ->
                val ids = event.getJSONArray("recipeIds")
                FamilyEvent(event.getLong("id"), event.getString("title"), event.optString("story"),
                    (0 until ids.length()).map(ids::getLong), getPhotos(event), event.getLong("createdAt"))
            }
            val author = data.optJSONObject("author")?.let { a -> AuthorProfile(1L,
                a.optString("name"), a.optString("tagline"), a.optString("bio"), getPhotos(a)) }
            val dest = File(context.filesDir, "restored_${UUID.randomUUID()}").apply { mkdirs() }
            val moved = media.mapValues { (_, file) -> File(dest, file.name).also { file.copyTo(it) } }
            fun remap(paths: List<String>) = paths.map { old -> moved.values.find { it.name == File(old).name }?.absolutePath ?: old }
            val db = AppDatabase.get(context)
            db.withTransaction {
                val dao = db.dao()

                dao.clearVersions(); dao.clearRecipes(); dao.clearPeople(); dao.clearMoments(); dao.clearEvents(); dao.clearAuthor()
                people.forEach { dao.addPerson(it.copy(photos = remap(it.photos))) }
                recipes.forEach { dao.addRecipe(it.copy(photos = remap(it.photos), audioPath = it.audioPath?.let { path -> remap(listOf(path)).first() })) }
                versions.forEach { dao.addVersion(it.copy(photos = remap(it.photos))) }
                moments.forEach { dao.addMoment(it.copy(photos = remap(it.photos))) }
                // Архив мог быть старого формата: события-сущности превращаем в теги у блюд.
                events.forEach { event ->
                    val tag = event.title.trim()
                    if (tag.isNotBlank()) {
                        recipes.filter { it.id in event.recipeIds && it.eventTag.isBlank() }
                            .forEach { dao.updateRecipe(it.copy(eventTag = tag)) }
                    }
                    dao.addEvent(event.copy(photos = remap(event.photos)))
                }
                author?.let { dao.upsertAuthor(it.copy(photos = remap(it.photos))) }
            }
            context.getSharedPreferences("settings", 0).edit()
                .putString("family_name", data.optString("familyName", "Моя семья"))
                .putString("book_title", data.optString("bookTitle", "Вкус нашего дома")).apply()
        } finally { folder.deleteRecursively() }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
