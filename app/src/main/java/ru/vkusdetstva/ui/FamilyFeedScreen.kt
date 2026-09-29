package ru.vkusdetstva.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.vkusdetstva.data.*
import ru.vkusdetstva.feed.RemoteFamilyFeedClient
import ru.vkusdetstva.feed.RemoteFeedPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface FeedEntry {
    val key: String
    val createdAt: Long
    data class RecipeEntry(val recipe: Recipe, val author: String) : FeedEntry {
        override val key = "recipe:${recipe.id}"
        override val createdAt = recipe.createdAt
    }
    data class MomentEntry(val moment: FamilyMoment) : FeedEntry {
        override val key = "moment:${moment.id}"
        override val createdAt = moment.createdAt
    }
}

@Composable
fun FamilyFeedScreen(recipes: List<Recipe>, people: List<Person>, moments: List<FamilyMoment>,
                     authorName: String, back: () -> Unit, openRecipe: (Long) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("family_feed_likes", Context.MODE_PRIVATE) }
    val remote = remember { RemoteFamilyFeedClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var remotePosts by remember { mutableStateOf<List<RemoteFeedPost>>(emptyList()) }
    var remoteError by remember { mutableStateOf("") }
    suspend fun refreshRemote() {
        if (!remote.configured()) return
        runCatching { withContext(Dispatchers.IO) { remote.posts() } }
            .onSuccess { remotePosts = it; remoteError = "" }
            .onFailure { remoteError = it.message.orEmpty() }
    }
    LaunchedEffect(Unit) { refreshRemote() }
    val entries = remember(recipes, people, moments, authorName) {
        buildList<FeedEntry> {
            recipes.forEach { recipe ->
                val by = people.firstOrNull { it.id == recipe.personId }?.name ?: authorName.ifBlank { "Семья" }
                add(FeedEntry.RecipeEntry(recipe, by))
            }
            moments.forEach { add(FeedEntry.MomentEntry(it)) }
        }.sortedByDescending { it.createdAt }
    }
    Page("Семейная лента", back) {
        Text("Рецепты, праздники и застолья в одном месте. Никаких сложных профилей: пост и простое сердечко.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (remote.configured()) {
            Section("Общая лента семьи")
            remotePosts.forEach { post ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(post.title, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(post.author, if (post.kind == "recipe") "Рецепт" else "За столом")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                        if (post.body.isNotBlank()) Text(post.body)
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { remote.like(post.id, true) } }
                                refreshRemote()
                            }
                        }) { Text("♥ ${post.likes}") }
                    }
                }
            }
            if (remoteError.isNotBlank()) Text("Общая лента временно недоступна: $remoteError",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else {
            Text("Общую семейную ленту можно подключить в Настройках. Локальные записи ниже остаются только на этом устройстве.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (entries.isEmpty()) Text("Добавьте рецепт или фотоисторию — они появятся здесь автоматически.")
        entries.forEach { entry ->
            var liked by remember(entry.key) { mutableStateOf(prefs.getBoolean(entry.key, false)) }
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    when (entry) {
                        is FeedEntry.RecipeEntry -> {
                            Text(entry.recipe.title, style = MaterialTheme.typography.titleMedium)
                            Text("Рецепт · ${entry.author}", style = MaterialTheme.typography.bodySmall)
                            entry.recipe.photos.firstOrNull()?.let {
                                Spacer(Modifier.height(8.dp))
                                AsyncImage(File(it), contentDescription = entry.recipe.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(190.dp))
                            }
                            if (entry.recipe.story.isNotBlank()) Text(entry.recipe.story.take(280))
                            TextButton(onClick = { openRecipe(entry.recipe.id) }) { Text("Открыть рецепт →") }
                        }
                        is FeedEntry.MomentEntry -> {
                            Text(entry.moment.title, style = MaterialTheme.typography.titleMedium)
                            Text("Семья за столом", style = MaterialTheme.typography.bodySmall)
                            entry.moment.photos.firstOrNull()?.let {
                                Spacer(Modifier.height(8.dp))
                                AsyncImage(File(it), contentDescription = entry.moment.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(190.dp))
                            }
                            if (entry.moment.story.isNotBlank()) Text(entry.moment.story.take(280))
                        }
                    }
                    TextButton(onClick = {
                        liked = !liked
                        prefs.edit().putBoolean(entry.key, liked).apply()
                    }) { Text(if (liked) "♥ Нравится" else "♡ Нравится") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
