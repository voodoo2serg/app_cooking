package ru.vkusdetstva.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.Drafts
import ru.vkusdetstva.data.RecipeVersion
import ru.vkusdetstva.data.AuthorProfile
import ru.vkusdetstva.data.personFromJson
import ru.vkusdetstva.data.momentFromJson
import ru.vkusdetstva.data.toJson
import ru.vkusdetstva.util.FamilyShare
import androidx.compose.ui.platform.LocalContext

@Composable
fun PeopleScreen(people: List<Person>, back: () -> Unit, open: (Long) -> Unit, add: () -> Unit) {
    Page("Люди нашей семьи", back) {
        people.forEach { ListTile(it.name, it.relation, { open(it.id) }) }
        if (people.isEmpty()) Text("Добавьте человека, от которого сохранился семейный рецепт.")
        Action("+ Добавить человека", add)
    }
}

@Composable
fun PersonScreen(person: Person, recipes: List<Recipe>, back: () -> Unit,

                 edit: () -> Unit, openRecipe: (Long) -> Unit, delete: () -> Unit,
                 versions: List<RecipeVersion> = emptyList(), author: AuthorProfile? = null) {
    val context = LocalContext.current
    Page(person.name, back) {
        PhotoCarousel(person.photos, "Фотографии ${person.name}")
        Text(listOf(person.relation, person.years).filter { it.isNotBlank() }.joinToString(" · "))
        if (person.story.isNotBlank()) { Section("Воспоминания"); Text(person.story) }
        Section("Её или его рецепты · ${recipes.size}")
        recipes.forEach { ListTile(it.title, it.story.take(90), { openRecipe(it.id) }) }
        if (recipes.isNotEmpty()) {
            Text("Глава с этими рецептами уходит простым текстом — письмом или в соцсети.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                val text = recipes.joinToString("\n\n———\n\n") {
                    FamilyShare.recipeText(it, person, author)
                }
                FamilyShare.shareText(context, "Рецепты ${person.name}", text,
                    recipes.firstOrNull { r -> r.photos.isNotEmpty() }?.photos?.firstOrNull())
            }) { Text("Отправить родне") }
        }
        Action("Изменить историю и фотографии", edit)
        var confirm by remember { mutableStateOf(false) }
        TextButton(onClick = { confirm = true }) { Text("Удалить человека") }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Удалить человека?") },
            text = { Text("Рецепты останутся в книге, но потеряют связь с этим человеком.") },
            confirmButton = { TextButton(onClick = delete) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
    }
}

@Composable
fun PersonEditScreen(existing: Person?, back: () -> Unit, save: (Person) -> Unit) {
    val context = LocalContext.current
    val draftKey = if (existing == null) "person-new" else "person-edit-${existing.id}"
    val draft = remember(draftKey) { Drafts.load(context, draftKey) }
    val base = remember(draftKey) { draft?.let(::personFromJson) ?: existing ?: Person(name = "") }
    var name by remember(draftKey) { mutableStateOf(base.name) }
    var relation by remember(draftKey) { mutableStateOf(base.relation) }
    var years by remember(draftKey) { mutableStateOf(base.years) }
    var story by remember(draftKey) { mutableStateOf(base.story) }
    var photos by remember(draftKey) { mutableStateOf(base.photos) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(draftKey, name, relation, years, story, photos) {
        if (submitted) { Drafts.clear(context, draftKey); return@LaunchedEffect }
        val empty = name.isBlank() && relation.isBlank() && years.isBlank() && story.isBlank() && photos.isEmpty()
        if (empty) Drafts.clear(context, draftKey)
        else Drafts.save(context, draftKey,
            base.copy(name = name, relation = relation, years = years, story = story, photos = photos).toJson())
    }
    Page(if (existing == null) "Человек в семейной книге" else "История человека", back) {
        if (draft != null) Text("Черновик восстановлен — продолжайте с того места, где остановились.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoEditor(photos, { photos = it }, "Фотографии человека · можно несколько")
        TextBox(name, { name = it }, "Имя")
        TextBox(relation, { relation = it }, "Кем приходится: бабушка, мама…")
        TextBox(years, { years = it }, "Годы жизни — если хочется указать")
        TextBox(story, { story = it }, "Воспоминания и заметки", 4)
        QuoteSuggestions(dish = name, onPick = { frame -> story = frame + story })
        Action("Сохранить", {
            submitted = true
            save((existing ?: Person(name = name)).copy(name = name.trim(),
                relation = relation, years = years, story = story, photos = photos))
            Drafts.clear(context, draftKey)
        }, name.isNotBlank())
    }
}

@Composable
fun MomentsScreen(moments: List<FamilyMoment>, back: () -> Unit,
                  open: (Long) -> Unit, add: () -> Unit) {
    Page("Как мы это едим", back) {
        Text("Самое вкусное — вместе. Эти фотографии живут в семейном архиве без обязательной связи с рецептом.")
        Spacer(Modifier.height(10.dp))
        moments.forEach { moment ->
            ListTile(moment.title, moment.story.take(90), { open(moment.id) })
            PhotoCarousel(moment.photos, "Семья за столом")
        }
        if (moments.isEmpty()) Text("Добавьте фото семьи с готовым блюдом и запишите, что происходило за столом.")
        Action("+ Добавить фотоисторию", add)
    }
}

@Composable
fun MomentEditScreen(existing: FamilyMoment?, back: () -> Unit,
                     save: (FamilyMoment) -> Unit, delete: () -> Unit,
                     tags: List<String> = emptyList()) {
    val context = LocalContext.current
    val draftKey = if (existing == null) "moment-new" else "moment-edit-${existing.id}"
    val draft = remember(draftKey) { Drafts.load(context, draftKey) }
    val base = remember(draftKey) { draft?.let(::momentFromJson) ?: existing ?: FamilyMoment(title = "") }
    var title by remember(draftKey) { mutableStateOf(base.title) }
    var story by remember(draftKey) { mutableStateOf(base.story) }
    var people by remember(draftKey) { mutableStateOf(base.people) }
    var photos by remember(draftKey) { mutableStateOf(base.photos) }
    var eventTag by remember(draftKey) { mutableStateOf(base.eventTag) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(draftKey, title, story, people, photos, eventTag) {
        if (submitted) { Drafts.clear(context, draftKey); return@LaunchedEffect }
        val empty = title.isBlank() && story.isBlank() && people.isBlank() && photos.isEmpty() && eventTag.isBlank()
        if (empty) Drafts.clear(context, draftKey)
        else Drafts.save(context, draftKey,
            base.copy(title = title, story = story, people = people, photos = photos, eventTag = eventTag).toJson())
    }
    Page(if (existing == null) "Семья за столом" else "Фотоистория", back) {
        if (draft != null) Text("Черновик восстановлен — продолжайте с того места, где остановились.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoEditor(photos, { photos = it }, "Фото семьи и готового блюда")
        TextBox(title, { title = it }, "Название фотоистории")
        TextBox(story, { story = it }, "Что происходило за столом?", 3)
        TextBox(people, { people = it }, "Кто на фотографиях?")
        EventTagField(eventTag, { eventTag = it }, tags.filter { it != eventTag })
        Text("Тег события собирает эту историю вместе с блюдами того же праздника.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Снимки останутся в семейном архиве. Вы сможете включить их в книгу отдельно от рецептов.")
        Action("Сохранить фотоисторию", {
            submitted = true
            save((existing ?: FamilyMoment(title = title)).copy(
                title = title.trim(), story = story, people = people, photos = photos,
                eventTag = eventTag.trim()))
            Drafts.clear(context, draftKey)
        }, title.isNotBlank())
        if (existing != null) TextButton(onClick = delete) { Text("Удалить фотоисторию") }
    }
}
