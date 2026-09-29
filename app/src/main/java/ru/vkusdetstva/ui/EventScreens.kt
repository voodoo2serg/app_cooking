package ru.vkusdetstva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.FamilyEvent
import ru.vkusdetstva.data.Recipe

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
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var story by remember(existing?.id) { mutableStateOf(existing?.story.orEmpty()) }
    var photos by remember(existing?.id) { mutableStateOf(existing?.photos.orEmpty()) }
    val selected = remember(existing?.id) { mutableStateListOf<Long>().apply { addAll(existing?.recipeIds.orEmpty()) } }
    Page(if (existing == null) "Новый семейный стол" else existing.title, back) {
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
            save((existing ?: FamilyEvent(title = title)).copy(title = title.trim(), story = story,
                photos = photos, recipeIds = selected.toList()))
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
