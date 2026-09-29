package ru.vkusdetstva.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.vkusdetstva.data.AuthorProfile
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import ru.vkusdetstva.util.AudioNote
import ru.vkusdetstva.util.FamilyShare

import ru.vkusdetstva.feed.RemoteFamilyFeedClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecipeEditScreen(existing: Recipe?, people: List<Person>, back: () -> Unit, save: (Recipe) -> Unit) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var personId by remember(existing?.id) { mutableStateOf(existing?.personId) }
    var story by remember(existing?.id) { mutableStateOf(existing?.story.orEmpty()) }
    val ingredientLines = remember(existing?.id) {
        mutableStateListOf<IngredientLine>().apply { addAll(parseIngredientLines(existing?.ingredients.orEmpty())) }
    }
    var steps by remember(existing?.id) { mutableStateOf(existing?.steps.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var photos by remember(existing?.id) { mutableStateOf(existing?.photos.orEmpty()) }
    var audioPath by remember(existing?.id) { mutableStateOf(existing?.audioPath) }
    var recording by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val recorder = remember { AudioNote(context) }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { recorder.start() }.onSuccess { recording = true }
    }
    Page(if (existing == null) "Новый семейный рецепт" else "Изменить рецепт", back) {
        TextBox(title, { title = it }, "Название")
        Text("От кого рецепт", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TextButton(onClick = { personId = null }) { Text(if (personId == null) "✓ От лица автора" else "От лица автора") }
        }
        Text("Если не выбрать никого — рецепт будет подписан именем автора книги из профиля.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        people.forEach { person ->
            TextButton(onClick = { personId = person.id }) {
                Text((if (personId == person.id) "✓ " else "") + person.name)
            }
        }
        TextBox(story, { story = it }, "История блюда", 3)
        Section("Ингредиенты")
        RecipeIngredientEditor(ingredientLines)
        TextBox(steps, { steps = it }, "Шаги приготовления: каждый с новой строки", 4)
        TextBox(notes, { notes = it }, "Заметки: можно менять позднее", 2)
        Section("Фотографии блюда")
        PhotoEditor(photos, { photos = it }, "Одно или несколько фото")
        Section("Голосовая заметка")
        if (recording) {
            OutlinedButton(onClick = { audioPath = recorder.stop(); recording = false }) { Text("■ Остановить запись") }
        } else {
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    runCatching { recorder.start() }.onSuccess { recording = true }
                } else permission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(if (audioPath == null) "🎙 Записать голос" else "🎙 Перезаписать") }
        }
        if (audioPath != null) Text("Аудиозаметка сохранена", style = MaterialTheme.typography.bodySmall)
        Action("Сохранить рецепт", { if (recording) { audioPath = recorder.stop(); recording = false }
            save((existing ?: Recipe(title = title)).copy(title = title.trim(), personId = personId,
                story = story, ingredients = formatIngredientLines(ingredientLines), steps = steps, notes = notes,
                photos = photos, audioPath = audioPath)) }, title.isNotBlank())
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RecipeScreen(recipe: Recipe, person: Person?, bookAuthor: AuthorProfile? = null,
                 versions: List<RecipeVersion> = emptyList(), back: () -> Unit = {},
                 edit: () -> Unit = {}, addVersion: () -> Unit = {}, cooked: () -> Unit = {},
                 update: (Recipe) -> Unit = {}, delete: () -> Unit = {}) {
    val context = LocalContext.current
    val audio = remember { AudioNote(context) }
    val scope = rememberCoroutineScope()
    val shareScope = rememberCoroutineScope()
    val feedClient = remember { RemoteFamilyFeedClient(context.applicationContext) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf("") }
    var feedStatus by remember(recipe.id) { mutableStateOf("") }
    DisposableEffect(audio) { onDispose { audio.release() } }
    var notes by remember(recipe.id) { mutableStateOf(recipe.notes) }
    var taste by remember(recipe.id) { mutableFloatStateOf(recipe.taste.toFloat()) }
    var ease by remember(recipe.id) { mutableFloatStateOf(recipe.ease.toFloat()) }
    var memory by remember(recipe.id) { mutableFloatStateOf(recipe.memory.toFloat()) }
    Page(recipe.title, back) {
        Text(when {
            person != null -> "Рецепт от ${person.name}"
            bookAuthor != null && bookAuthor.name.isNotBlank() -> "От лица автора · ${bookAuthor.name}"
            else -> "Семейный рецепт"
        })
        PhotoCarousel(recipe.photos, "Фото блюда")
        Text(recipe.story)
        Section("Жизнь рецепта")
        Text("Готовили ${recipe.timesCooked} раз · семейных версий: ${versions.size}")
        versions.sortedBy { it.createdAt }.forEach { version ->
            ListTile(version.personName, version.change + if (version.note.isNotBlank()) " · ${version.note}" else "") { }
            PhotoCarousel(version.photos, "Фото версии")
        }
        Action("Я приготовил(а) — +1 раз", cooked)
        OutlinedButton(onClick = addVersion) { Text("+ Записать свою версию") }
        Section("Ингредиенты")
        Text(recipe.ingredients.ifBlank { "Пока не добавлены" })
        Section("Приготовление")
        Text(recipe.steps.ifBlank { "Пока не добавлены" })
        Section("Отправка родне")
        OutlinedButton(onClick = { FamilyShare.shareText(context, recipe.title,
            FamilyShare.recipeText(recipe, person, bookAuthor)) }) { Text("Отправить рецепт текстом") }
        OutlinedButton(onClick = {
            scope.launch {
                sharing = true; shareError = ""
                val result = runCatching { withContext(Dispatchers.IO) {
                    FamilyShare.createPdf(context, recipe.title, listOf(recipe), listOfNotNull(person), versions, bookAuthor)
                } }
                sharing = false
                result.fold({ FamilyShare.sharePdf(context, recipe.title, it) },
                    { shareError = "Не удалось создать PDF: ${it.message}" })
            }
        }, enabled = !sharing) { Text(if (sharing) "Собираем PDF…" else "Отправить рецепт в PDF") }
        if (shareError.isNotBlank()) Text(shareError, color = MaterialTheme.colorScheme.error)
        Section("Оценки семьи · от 1 до 5")
        Rating("Легендарный вкус", taste, { taste = it }, { update(recipe.copy(taste = taste.toInt())) })
        Rating("Простота приготовления", ease, { ease = it }, { update(recipe.copy(ease = ease.toInt())) })
        Rating("Семейная память", memory, { memory = it }, { update(recipe.copy(memory = memory.toInt())) })
        Section("Заметка о блюде")
        TextBox(notes, { notes = it }, "Можно исправить в любой момент", 2)
        OutlinedButton(onClick = { update(recipe.copy(notes = notes)) }) { Text("Сохранить заметку") }
        if (recipe.audioPath != null) {
            Section("Голосовая заметка")
            OutlinedButton(onClick = { runCatching { audio.play(recipe.audioPath) } }) { Text("▶ Слушать") }
        }
        OutlinedButton(onClick = {
            scope.launch {
                sharing = true; shareError = ""
                val result = runCatching { withContext(Dispatchers.IO) {
                    FamilyShare.createPdf(context, recipe.title, listOf(recipe), listOfNotNull(person),
                        versions.filter { it.recipeId == recipe.id }, bookAuthor)
                } }
                sharing = false
                result.fold({ FamilyShare.sharePdf(context, recipe.title, it) },
                    { shareError = "Не удалось создать PDF: ${it.message}" })
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (sharing) "Готовим PDF…" else "Отправить родне · PDF")
        }
        if (shareError.isNotBlank()) Text(shareError, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        if (feedClient.configured()) {
            OutlinedButton(onClick = {
                val authorName = person?.name ?: bookAuthor?.name?.takeIf { it.isNotBlank() } ?: "Семья"
                shareScope.launch {
                    feedStatus = "Публикуем…"
                    feedStatus = runCatching {
                        withContext(Dispatchers.IO) { feedClient.publish(recipe, authorName) }
                        "Опубликовано в общей семейной ленте"
                    }.getOrElse { "Ошибка: ${it.message}" }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Опубликовать в семейной ленте") }
            if (feedStatus.isNotBlank()) Text(feedStatus, style = MaterialTheme.typography.bodySmall)
        }
        Action("Изменить рецепт и фото", edit)
        var confirm by remember { mutableStateOf(false) }
        TextButton(onClick = { confirm = true }) { Text("Удалить рецепт") }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Удалить рецепт?") },
            text = { Text("Рецепт и его версии исчезнут из коллекции.") },
            confirmButton = { TextButton(onClick = delete) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
    }
}

@Composable
private fun Rating(label: String, value: Float, change: (Float) -> Unit, save: () -> Unit) {
    Text("$label · ${value.toInt()} / 5")
    Slider(value = value.coerceIn(1f, 5f), onValueChange = { change(it.toInt().toFloat()) },
        onValueChangeFinished = save, valueRange = 1f..5f, steps = 3)
}

@Composable
fun VersionEditScreen(recipe: Recipe, back: () -> Unit, save: (RecipeVersion) -> Unit) {
    var name by remember { mutableStateOf("") }
    var change by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(emptyList<String>()) }
    Page("Моя версия: ${recipe.title}", back) {
        TextBox(name, { name = it }, "Кто приготовил")
        TextBox(change, { change = it }, "Что изменили в рецепте?", 2)
        TextBox(note, { note = it }, "Как получилось?", 2)
        PhotoEditor(photos, { photos = it }, "Фото вашей версии")
        Action("Сохранить семейную версию", {
            save(RecipeVersion(recipeId = recipe.id, personName = name.trim(), change = change.trim(),
                note = note, photos = photos))
        }, name.isNotBlank() && change.isNotBlank())
    }
}
