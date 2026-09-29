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
import androidx.compose.ui.platform.LocalContext
import ru.vkusdetstva.util.FamilyShare

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
                 edit: () -> Unit, openRecipe: (Long) -> Unit, delete: () -> Unit) {
    val context = LocalContext.current
    Page(person.name, back) {
        PhotoCarousel(person.photos, "Фотографии ${person.name}")
        Text(listOf(person.relation, person.years).filter { it.isNotBlank() }.joinToString(" · "))
        if (person.story.isNotBlank()) { Section("Воспоминания"); Text(person.story) }
        Section("Её или его рецепты · ${recipes.size}")
        recipes.forEach { ListTile(it.title, it.story.take(90), { openRecipe(it.id) }) }
        if (recipes.isNotEmpty()) {
            OutlinedButton(onClick = { FamilyShare.sharePersonChapterPdf(context, person, recipes) },
                modifier = Modifier.fillMaxWidth()) { Text("Отправить родне · глава PDF") }
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
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var relation by remember(existing?.id) { mutableStateOf(existing?.relation.orEmpty()) }
    var years by remember(existing?.id) { mutableStateOf(existing?.years.orEmpty()) }
    var story by remember(existing?.id) { mutableStateOf(existing?.story.orEmpty()) }
    var photos by remember(existing?.id) { mutableStateOf(existing?.photos.orEmpty()) }
    Page(if (existing == null) "Человек в семейной книге" else "История человека", back) {
        PhotoEditor(photos, { photos = it }, "Фотографии человека · можно несколько")
        TextBox(name, { name = it }, "Имя")
        TextBox(relation, { relation = it }, "Кем приходится: бабушка, мама…")
        TextBox(years, { years = it }, "Годы жизни — если хочется указать")
        TextBox(story, { story = it }, "Воспоминания и заметки", 4)
        Action("Сохранить", { save((existing ?: Person(name = name)).copy(name = name.trim(),
            relation = relation, years = years, story = story, photos = photos)) }, name.isNotBlank())
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
                     save: (FamilyMoment) -> Unit, delete: () -> Unit) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var story by remember(existing?.id) { mutableStateOf(existing?.story.orEmpty()) }
    var people by remember(existing?.id) { mutableStateOf(existing?.people.orEmpty()) }
    var photos by remember(existing?.id) { mutableStateOf(existing?.photos.orEmpty()) }
    Page(if (existing == null) "Семья за столом" else "Фотоистория", back) {
        PhotoEditor(photos, { photos = it }, "Фото семьи и готового блюда")
        TextBox(title, { title = it }, "Название события")
        TextBox(story, { story = it }, "Что происходило за столом?", 3)
        TextBox(people, { people = it }, "Кто на фотографиях?")
        Text("Снимки останутся в семейном архиве. Вы сможете включить их в книгу отдельно от рецептов.")
        Action("Сохранить фотоисторию", { save((existing ?: FamilyMoment(title = title)).copy(
            title = title.trim(), story = story, people = people, photos = photos)) }, title.isNotBlank())
        if (existing != null) TextButton(onClick = delete) { Text("Удалить фотоисторию") }
    }
}
