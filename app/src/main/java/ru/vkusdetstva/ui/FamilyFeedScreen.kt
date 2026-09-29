package ru.vkusdetstva.ui


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.*
import ru.vkusdetstva.util.FamilyShare

/** Local feed of the current archive; a remote family feed requires an authenticated sync service. */
@Composable
fun FamilyFeedScreen(recipes: List<Recipe>, moments: List<FamilyMoment>, people: List<Person>,
                     author: AuthorProfile?, back: () -> Unit, open: (Long) -> Unit,
                     likeRecipe: (Long) -> Unit, likeMoment: (Long) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("feed_likes", 0) }
    var refresh by remember { mutableIntStateOf(0) }
    val posts = remember(recipes, moments) {
        (recipes.map { Triple(it.createdAt, "recipe", it.id) } +
            moments.map { Triple(it.createdAt, "moment", it.id) }).sortedByDescending { it.first }
    }
    Page("За семейным столом", back) {
        Text("Рецепты и истории в этом архиве. Общая лента на разных телефонах появится после подключения семейной синхронизации.",
            style = MaterialTheme.typography.bodySmall)
        Section("Любимые рецепты")
        recipes.sortedWith(compareByDescending<Recipe> { it.likes }.thenByDescending { it.timesCooked })
            .take(3).forEach { r -> ListTile("♥ ${r.likes} · ${r.title}", "Готовили ${r.timesCooked} раз") { open(r.id) } }
        Section("Новые записи")
        posts.forEach { (_, kind, id) ->
            val recipe = recipes.find { kind == "recipe" && it.id == id }
            val moment = moments.find { kind == "moment" && it.id == id }
            val title = recipe?.title ?: moment?.title ?: return@forEach
            val story = recipe?.story ?: moment?.story.orEmpty()
            val key = "$kind:$id"
            val liked = remember(refresh, key) { prefs.getBoolean(key, false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (story.isNotBlank()) Text(story, maxLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            if (!liked) {
                                prefs.edit().putBoolean(key, true).apply()
                                if (recipe != null) likeRecipe(id) else likeMoment(id)
                                refresh++
                            }
                        }, enabled = !liked) { Text("♥ ${recipe?.likes ?: moment?.likes ?: 0}") }
                        if (recipe != null) {
                            TextButton(onClick = { open(id) }) { Text("Рецепт") }
                            TextButton(onClick = { FamilyShare.shareText(context, title,
                                FamilyShare.recipeText(recipe, people.find { it.id == recipe.personId }, author)) }) {
                                Text("Отправить родне")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
