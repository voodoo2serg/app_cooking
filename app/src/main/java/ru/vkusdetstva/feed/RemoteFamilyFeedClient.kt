package ru.vkusdetstva.feed

import android.content.Context
import org.json.JSONObject
import ru.vkusdetstva.data.Recipe
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class RemoteFeedPost(
    val id: Long,
    val kind: String,
    val title: String,
    val body: String,
    val author: String,
    val createdAt: Long,
    val likes: Int
)

class RemoteFamilyFeedClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", 0)
    private val baseUrl get() = prefs.getString("family_feed_url", "").orEmpty().trimEnd('/')
    private val familyCode get() = prefs.getString("family_feed_code", "").orEmpty()
    private val deviceId: String
        get() {
            val existing = prefs.getString("family_feed_device", null)
            if (!existing.isNullOrBlank()) return existing
            return UUID.randomUUID().toString().also {
                prefs.edit().putString("family_feed_device", it).apply()
            }
        }

    fun configured(): Boolean = baseUrl.startsWith("https://") && familyCode.isNotBlank()

    fun createFamily(url: String): String {
        val normalized = url.trim().trimEnd('/')
        require(normalized.startsWith("https://")) { "Для общей ленты нужен HTTPS." }
        val response = request("$normalized/families", "POST", "{}")
        val code = JSONObject(response).getString("code")
        prefs.edit().putString("family_feed_url", normalized).putString("family_feed_code", code).apply()
        return code
    }

    fun joinFamily(url: String, code: String) {
        val normalized = url.trim().trimEnd('/')
        require(normalized.startsWith("https://")) { "Для общей ленты нужен HTTPS." }
        val clean = code.trim()
        request("$normalized/families/$clean/posts?limit=1", "GET", null)
        prefs.edit().putString("family_feed_url", normalized).putString("family_feed_code", clean).apply()
    }

    fun posts(): List<RemoteFeedPost> {
        if (!configured()) return emptyList()
        val json = JSONObject(request("$baseUrl/families/$familyCode/posts", "GET", null))
        val arr = json.getJSONArray("posts")
        return (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            RemoteFeedPost(
                p.getLong("id"), p.getString("kind"), p.getString("title"),
                p.optString("body"), p.optString("author"), p.getLong("created_at"), p.optInt("likes")
            )
        }
    }

    fun publish(recipe: Recipe, author: String) {
        check(configured()) { "Семейная лента не подключена." }
        val payload = JSONObject()
            .put("client_key", "recipe:${recipe.id}")
            .put("kind", "recipe")
            .put("title", recipe.title)
            .put("body", recipe.story.take(4000))
            .put("author", author.take(120))
            .put("created_at", recipe.createdAt)
        request("$baseUrl/families/$familyCode/posts", "POST", payload.toString())
    }

    fun like(postId: Long, liked: Boolean) {
        check(configured())
        val payload = JSONObject().put("device_id", deviceId).put("liked", liked)
        request("$baseUrl/families/$familyCode/posts/$postId/like", "POST", payload.toString())
    }

    private fun request(url: String, method: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Сервер семейной ленты: HTTP $code")
            text
        } finally {
            connection.disconnect()
        }
    }
}
