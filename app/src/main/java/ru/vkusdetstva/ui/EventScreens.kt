package ru.vkusdetstva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.Drafts
import ru.vkusdetstva.data.FamilyEvent
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.eventFromJson
import ru.vkusdetstva.data.toJson

@Composable
fun EventsScreen(events: List<FamilyEvent>, recipes: List<Recipe>, back: () -> Unit,
                 open: (Long) -> Unit, add: () -> Unit, shopping: (Long) -> Unit) {
    Page("События и семейные столы", back) {
        Text("Соберите блюда к празднику и сохраните фотографии общего стола отдельно от фото блюд.")
        events.forEach { event ->
            Section(event.title)
            PhotoCarousel(event.photos, "Стол: ${event.title}")
            Text(event.story)
            val dishes = recipes.filter { it.id in event.recipeIds }
            Text("${dishes.size} блюд: ${dishes.joinToString { it.title }}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { open(event.id) }) { Text("Изменить") }
                TextButton(onClick = { shopping(event.id) }, enabled = dishes.isNotEmpty()) {
                    Text("Список в магазин")
                }
            }
        }
        if (events.isEmpty()) Text("Создайте первый стол: Новый год, Пасха, свадьба или обычный семейный ужин.")
        Action("+ Создать событие", add)
    }
}

@Composable
fun EventEditScreen(existing: FamilyEvent?, recipes: List<Recipe>, back: () -> Unit,
                    save: (FamilyEvent) -> Unit, delete: () -> Unit) {
    val context = LocalContext.current
    val draftKey = if (existing == null) "event-new" else "event-edit-${existing.id}"
    val draft = remember(draftKey) { Drafts.load(context, draftKey) }
    val base = remember(draftKey) { draft?.let(::eventFromJson) ?: existing ?: FamilyEvent(title = "") }
    var title by remember(draftKey) { mutableStateOf(base.title) }
    var story by remember(draftKey) { mutableStateOf(base.story) }
    var photos by remember(draftKey) { mutableStateOf(base.photos) }
    val selected = remember(draftKey) {
        mutableStateListOf<Long>().apply { addAll(base.recipeIds) }
    }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(draftKey, title, story, photos, selected.toList()) {
        if (submitted) { Drafts.clear(context, draftKey); return@LaunchedEffect }
        val empty = title.isBlank() && story.isBlank() && photos.isEmpty() && selected.isEmpty()
        if (empty) Drafts.clear(context, draftKey)
        else Drafts.save(context, draftKey, base.copy(title = title, story = story,
            photos = photos, recipeIds = selected.toList()).toJson())
    }
    Page(if (existing == null) "Новый семейный стол" else existing.title, back) {
        if (draft != null) Text("Черновик восстановлен — продолжайте с того места, где остановились.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextBox(title, { title = it }, "Событие: Пасха, Новый год, свадьба…")
        TextBox(story, { story = it }, "Что происходило за столом?", 3)
        PhotoEditor(photos, { photos = it }, "Фото общего стола и праздника")
        Section("Блюда на столе · ${selected.size}")
        recipes.forEach { recipe ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = recipe.id in selected, onCheckedChange = { checked ->
                    if (checked) selected.add(recipe.id) else selected.remove(recipe.id)
                })
                Text(recipe.title)
            }
        }
        if (recipes.isEmpty()) Text("Сначала запишите рецепт, затем добавьте его к этому столу.")
        Action("Сохранить событие", {
            submitted = true
            save((existing ?: FamilyEvent(title = title)).copy(title = title.trim(), story = story,
                photos = photos, recipeIds = selected.toList()))
            Drafts.clear(context, draftKey)
        }, title.isNotBlank())
        if (existing != null) {
            var confirm by remember { mutableStateOf(false) }
            TextButton(onClick = { confirm = true }) { Text("Удалить событие") }
            if (confirm) AlertDialog(onDismissRequest = { confirm = false },
                title = { Text("Удалить событие?") },
                text = { Text("Блюда останутся в семейном архиве.") },
                confirmButton = { TextButton(onClick = delete) { Text("Удалить") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
        }
    }
}
