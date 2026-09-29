package ru.vkusdetstva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.AuthorProfile
import ru.vkusdetstva.data.Drafts
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.authorFromJson
import ru.vkusdetstva.data.toJson

@Composable
fun AuthorScreen(profile: AuthorProfile, recipes: List<Recipe>, back: () -> Unit, edit: () -> Unit) {
    Page(if (profile.name.isBlank()) "Профиль автора" else profile.name, back) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            AuthorAvatar(profile.photos, profile.name, 88.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (profile.tagline.isNotBlank()) profile.tagline else "Автор семейной книги",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary)
                Text("Круглая аватарка видна в шапке приложения",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        PhotoCarousel(profile.photos, "Фотография автора")
        if (profile.bio.isNotBlank()) {
            Section("Кто такой автор")
            Text(profile.bio)
        }
        Section("Рецепты от лица автора · ${recipes.size}")
        if (recipes.isEmpty()) Text("Пока нет неподписанных рецептов — каждый новый рецепт без имени выходит отсюда.")
        recipes.forEach { recipe ->
            ListTile(recipe.title, if (recipe.story.isBlank()) "Семейный рецепт" else recipe.story.take(90), { })
        }
        Action("Изменить профиль автора", edit)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AuthorEditScreen(existing: AuthorProfile?, back: () -> Unit, save: (AuthorProfile) -> Unit) {
    val context = LocalContext.current
    val draft = remember("author") { Drafts.load(context, "author") }
    val base = remember("author") { draft?.let(::authorFromJson) ?: existing ?: AuthorProfile() }
    var name by remember("author") { mutableStateOf(base.name) }
    var tagline by remember("author") { mutableStateOf(base.tagline) }
    var bio by remember("author") { mutableStateOf(base.bio) }
    var photos by remember("author") { mutableStateOf(base.photos) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect("author", name, tagline, bio, photos) {
        if (submitted) { Drafts.clear(context, "author"); return@LaunchedEffect }
        val empty = name.isBlank() && tagline.isBlank() && bio.isBlank() && photos.isEmpty()
        if (empty) Drafts.clear(context, "author")
        else Drafts.save(context, "author",
            base.copy(name = name, tagline = tagline, bio = bio, photos = photos).toJson())
    }
    Page("Профиль автора книги", back) {
        if (draft != null) Text("Черновик восстановлен — продолжайте с того места, где остановились.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Автор — тот, кто ведёт семейную книгу. Рецепты, которые не подписаны чьим-то именем, выходят от лица автора.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            AuthorAvatar(photos, name, 72.dp)
            Spacer(Modifier.width(12.dp))
            Text("Первое фото становится круглой аватаркой — в шапке приложения, в профиле и в настройках.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PhotoEditor(photos, { photos = it }, "Фотография автора · можно несколько")
        TextBox(name, { name = it }, "Имя или подпись: кто ведёт книгу")
        TextBox(tagline, { tagline = it }, "Кем приходитесь: внук, дочка, хранитель рецептов…")
        TextBox(bio, { bio = it }, "Расскажите, кто такой автор — пара строк о себе и о книге", 4)
        Action("Сохранить профиль автора", {
            submitted = true
            save(AuthorProfile(name = name.trim(), tagline = tagline.trim(),
                bio = bio.trim(), photos = photos))
            Drafts.clear(context, "author")
        }, name.isNotBlank())
        if (existing == null && name.isBlank()) {
            Text("Имя появится в книге: на странице рецепта, в главе автора и на титуле издания.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}
