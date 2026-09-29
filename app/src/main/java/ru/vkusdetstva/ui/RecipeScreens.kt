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
import ru.vkusdetstva.data.Drafts
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import ru.vkusdetstva.data.recipeFromJson
import ru.vkusdetstva.data.versionFromJson
import ru.vkusdetstva.data.toJson
import ru.vkusdetstva.util.AudioNote
import ru.vkusdetstva.util.FamilyShare

import ru.vkusdetstva.feed.RemoteFamilyFeedClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecipeEditScreen(existing: Recipe?, people: List<Person>, back: () -> Unit, save: (Recipe) -> Unit,
                     tags: List<String> = emptyList()) {
    val context = LocalContext.current
    // Черновик живёт на диске: поворот экрана, звонок и даже перезапуск приложения
    // не теряют ни слова. Ключ различает новый рецепт, черновик после импорта
    // и редактирование уже существующего рецепта.
    val draftKey = when {
        existing == null -> "recipe-new"
        existing.id == 0L -> "recipe-import"
        else -> "recipe-edit-${existing.id}"
    }
    val draft = remember(draftKey) { Drafts.load(context, draftKey) }
    val base = remember(draftKey) { draft?.let(::recipeFromJson) ?: existing ?: Recipe(title = "") }
    var title by remember(draftKey) { mutableStateOf(base.title) }
    var personId by remember(draftKey) { mutableStateOf(base.personId) }
    var story by remember(draftKey) { mutableStateOf(base.story) }
    var eventTag by remember(draftKey) { mutableStateOf(base.eventTag) }
    val ingredientLines = remember(draftKey) {
        mutableStateListOf<IngredientLine>().apply { addAll(parseIngredientLines(base.ingredients)) }
    }
    var steps by remember(draftKey) { mutableStateOf(base.steps) }
    var notes by remember(draftKey) { mutableStateOf(base.notes) }
    var photos by remember(draftKey) { mutableStateOf(base.photos) }
    var audioPath by remember(draftKey) { mutableStateOf(base.audioPath) }
    var recording by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val recorder = remember { AudioNote(context) }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { recorder.start() }.onSuccess { recording = true }
    }
    // Автосохранение: каждое изменение тут же пишется на диск; пустая форма — черновик стирается;
    // после успешного сохранения submitted запрещает «воскрешение» черновика.
    LaunchedEffect(draftKey, title, personId, story, steps, notes, photos, audioPath, eventTag, ingredientLines.toList()) {
        if (submitted) { Drafts.clear(context, draftKey); return@LaunchedEffect }
        val empty = title.isBlank() && story.isBlank() && steps.isBlank() && notes.isBlank() &&
            ingredientLines.isEmpty() && photos.isEmpty() && audioPath == null && eventTag.isBlank()
        if (empty) Drafts.clear(context, draftKey)
        else Drafts.save(context, draftKey, base.copy(title = title, personId = personId, story = story,
            ingredients = formatIngredientLines(ingredientLines), steps = steps, notes = notes,
            photos = photos, audioPath = audioPath, eventTag = eventTag).toJson())
    }
    Page(if (existing == null) "Новый семейный рецепт" else "Изменить рецепт", back) {
        if (draft != null) Text("Черновик восстановлен — продолжайте с того места, где остановились.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        QuoteSuggestions(dish = title, onPick = { frame -> story = frame + story })
        EventTagField(eventTag, { eventTag = it }, tags.filter { it != eventTag })
        Text("Событие — это тег: всё, что отмечено одинаково, соберётся в разделе «События».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            submitted = true
            save((existing ?: Recipe(title = title)).copy(title = title.trim(), personId = personId,
                story = story, ingredients = formatIngredientLines(ingredientLines), steps = steps, notes = notes,
                photos = photos, audioPath = audioPath, eventTag = eventTag.trim()))
            Drafts.clear(context, draftKey) }, title.isNotBlank())
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RecipeScreen(recipe: Recipe, person: Person?, bookAuthor: AuthorProfile? = null,
                 versions: List<RecipeVersion> = emptyList(), back: () -> Unit = {},
                 edit: () -> Unit = {}, addVersion: () -> Unit = {}, cooked: () -> Unit = {},
                 update: (Recipe) -> Unit = {}, delete: () -> Unit = {},
                 toBasket: () -> Unit = {}) {
    val context = LocalContext.current
    val audio = remember { AudioNote(context) }
    val scope = rememberCoroutineScope()
    val feedClient = remember { RemoteFamilyFeedClient(context.applicationContext) }
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
        Text("Простой текст с фото — уходит письмом или в соцсети через меню «Поделиться».",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { FamilyShare.shareText(context, recipe.title,
            FamilyShare.recipeText(recipe, person, bookAuthor), recipe.photos.firstOrNull()) }) {
            Text("Отправить родне")
        }
        Section("Корзина")
        OutlinedButton(onClick = toBasket) { Text("Ингредиенты этого блюда — в корзину") }
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
        if (feedClient.configured()) {
            OutlinedButton(onClick = {
                val authorName = person?.name ?: bookAuthor?.name?.takeIf { it.isNotBlank() } ?: "Семья"
                scope.launch {
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
    val context = LocalContext.current
    val draftKey = "version-${recipe.id}"
    val draft = remember(draftKey) { Drafts.load(context, draftKey) }
    val base = remember(draftKey) {
        draft?.let(::versionFromJson) ?: RecipeVersion(recipeId = recipe.id, personName = "", change = "")
    }
    var name by remember(draftKey) { mutableStateOf(base.personName) }
    var change by remember(draftKey) { mutableStateOf(base.change) }
    var note by remember(draftKey) { mutableStateOf(base.note) }
    var photos by remember(draftKey) { mutableStateOf(base.photos) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(draftKey, name, change, note, photos) {
        if (submitted) { Drafts.clear(context, draftKey); return@LaunchedEffect }
        val empty = name.isBlank() && change.isBlank() && note.isBlank() && photos.isEmpty()
        if (empty) Drafts.clear(context, draftKey)
        else Drafts.save(context, draftKey,
            base.copy(personName = name, change = change, note = note, photos = photos).toJson())
    }
    Page("Моя версия: ${recipe.title}", back) {
        TextBox(name, { name = it }, "Кто приготовил")
        TextBox(change, { change = it }, "Что изменили в рецепте?", 2)
        TextBox(note, { note = it }, "Как получилось?", 2)
        PhotoEditor(photos, { photos = it }, "Фото вашей версии")
        Action("Сохранить семейную версию", {
            submitted = true
            save(RecipeVersion(recipeId = recipe.id, personName = name.trim(), change = change.trim(),
                note = note, photos = photos))
            Drafts.clear(context, draftKey)
        }, name.isNotBlank() && change.isNotBlank())
    }
}
