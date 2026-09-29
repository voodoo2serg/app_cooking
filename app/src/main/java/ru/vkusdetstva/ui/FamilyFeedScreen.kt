package ru.vkusdetstva.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.vkusdetstva.data.*
import ru.vkusdetstva.util.FamilyShare
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Одна запись ленты: рецепт или фотоистория. */
private data class FeedPost(val key: String, val createdAt: Long, val isRecipe: Boolean,
                            val id: Long, val title: String, val story: String,
                            val photos: List<String>, val likes: Int, val who: String)

/** Кто подписан под записью: человек, автор книги или просто «Наша семья». */
private fun postWho(recipe: Recipe, person: Person?, authorName: String): String =
    person?.name ?: authorName.ifBlank { "Наша семья" }

/**
 * Лента семейного стола — как в знакомых соцсетях: свежие записи сверху,
 * крупное фото, короткий текст и лайк, который можно поставить и снять.
 */
@Composable
fun FamilyFeedScreen(recipes: List<Recipe>, moments: List<FamilyMoment>, people: List<Person>,
                     author: AuthorProfile?, back: () -> Unit, open: (Long) -> Unit,
                     likeRecipe: (Long, Int) -> Unit, likeMoment: (Long, Int) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("feed_likes", 0) }
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy", Locale("ru")) }
    val authorName = author?.name.orEmpty()

    val posts = remember(recipes, moments, authorName) {
        (recipes.map { recipe ->
            FeedPost("recipe:${recipe.id}", recipe.createdAt, isRecipe = true, id = recipe.id,
                title = recipe.title, story = recipe.story, photos = recipe.photos,
                likes = recipe.likes, who = postWho(recipe, people.find { it.id == recipe.personId }, authorName))
        } + moments.map { moment ->
            FeedPost("moment:${moment.id}", moment.createdAt, isRecipe = false, id = moment.id,
                title = moment.title, story = moment.story, photos = moment.photos,
                likes = moment.likes, who = moment.people.ifBlank { "Наша семья" })
        }).sortedByDescending { it.createdAt }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            Text("ВКУС ДЕТСТВА", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        Text("Лента семейного стола", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = BrandDimens.pagePadding))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
            start = BrandDimens.pagePadding, end = BrandDimens.pagePadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (posts.isEmpty()) {
                item {
                    Text("Лента пока пуста. Запишите рецепт или добавьте фотоисторию — и здесь появится вся семейная жизнь.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(posts, key = { it.key }) { post ->
                var liked by remember(post.key) { mutableStateOf(prefs.getBoolean(post.key, false)) }
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)) {
                    Column {
                        // Шапка записи: круглая монограмма, имя, дата.
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape)
                                .background(BrandColors.Terracotta), contentAlignment = Alignment.Center) {
                                Text(post.who.trim().take(1).uppercase().ifBlank { "В" },
                                    color = BrandColors.Cream, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(post.who, style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(dateFormat.format(Date(post.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val photo = post.photos.firstOrNull { File(it).exists() }
                        if (photo != null) {
                            AsyncImage(File(photo), contentDescription = post.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(230.dp)
                                    .clickable(enabled = post.isRecipe) { open(post.id) })
                        }
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(post.title, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable(enabled = post.isRecipe) { open(post.id) })
                            if (post.story.isNotBlank()) Spacer(Modifier.height(4.dp))
                            if (post.story.isNotBlank()) Text(post.story, maxLines = 5,
                                color = MaterialTheme.colorScheme.onSurface)
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = {
                                    val delta = if (liked) -1 else 1
                                    liked = !liked
                                    prefs.edit().putBoolean(post.key, liked).apply()
                                    if (post.isRecipe) likeRecipe(post.id, delta) else likeMoment(post.id, delta)
                                }) {
                                    Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = if (liked) "Убрать лайк" else "Поставить лайк",
                                        tint = if (liked) BrandColors.Terracotta
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${post.likes}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                if (post.isRecipe) {
                                    TextButton(onClick = { open(post.id) }) { Text("Рецепт") }
                                }
                                TextButton(onClick = {
                                    if (post.isRecipe) {
                                        val recipe = recipes.find { it.id == post.id } ?: return@TextButton
                                        FamilyShare.shareText(context, recipe.title,
                                            FamilyShare.recipeText(recipe,
                                                people.find { it.id == recipe.personId }, author),
                                            recipe.photos.firstOrNull())
                                    } else {
                                        val moment = moments.find { it.id == post.id } ?: return@TextButton
                                        FamilyShare.shareText(context, moment.title, buildString {
                                            append(moment.title); append("\n")
                                            if (moment.story.isNotBlank()) append(moment.story.trim())
                                            append("\n\nИз семейного архива «Вкус детства»")
                                        }, moment.photos.firstOrNull())
                                    }
                                }) { Text("Отправить родне") }
                            }
                        }
                    }
                }
            }
        }
    }
}
