package ru.vkusdetstva.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import ru.vkusdetstva.util.BookPdf
import ru.vkusdetstva.util.FamilyArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BookScreen(recipes: List<Recipe>, people: List<Person>, versions: List<RecipeVersion>,
               moments: List<FamilyMoment>, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var title by remember { mutableStateOf(prefs.getString("book_title", "Вкус нашего дома").orEmpty()) }
    val chosenRecipes = remember { mutableStateMapOf<Long, Boolean>() }
    val chosenMoments = remember { mutableStateMapOf<Long, Boolean>() }
    var step by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf("") }
    val selectedRecipes = recipes.filter { chosenRecipes[it.id] != false }
    val selectedMoments = moments.filter { chosenMoments[it.id] != false }
    val selectedPeople = people.filter { person -> selectedRecipes.any { it.personId == person.id } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) runCatching { BookPdf.write(context, uri, title, selectedRecipes, selectedPeople, versions, selectedMoments) }
            .onFailure { error = "Не удалось сохранить PDF: ${it.message.orEmpty()}" }
    }
    Page(if (step == 1) "Собираем семейную книгу" else "Проверяем связи", if (step == 1) back else ({ step = 1 })) {
        if (step == 1) {
            Text("Выберите материалы. Фотоистории «Как мы это едим» не требуют привязки к рецепту.")
            TextBox(title, { title = it; prefs.edit().putString("book_title", it).apply() }, "Название книги")
            Section("Рецепты · ${selectedRecipes.size} из ${recipes.size}")
            recipes.forEach { recipe ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(chosenRecipes[recipe.id] != false, { chosenRecipes[recipe.id] = it })
                    Text(recipe.title)
                }
            }
            Section("Как мы это едим · ${selectedMoments.size} из ${moments.size}")
            moments.forEach { moment ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(chosenMoments[moment.id] != false, { chosenMoments[moment.id] = it })
                    Text(moment.title)
                }
            }
            Action("Проверить людей и истории →", { step = 2 }, selectedRecipes.isNotEmpty())
        } else {
            Text("В книгу войдут ${selectedRecipes.size} рецептов, ${selectedPeople.size} человек и ${selectedMoments.size} фотоисторий.")
            Section("Люди и рецепты")
            selectedPeople.forEach { person ->
                ListTile(person.name, selectedRecipes.filter { it.personId == person.id }.joinToString { it.title }) { }
            }
            selectedRecipes.filter { it.personId == null || people.none { p -> p.id == it.personId } }
                .forEach { Text("Без автора: ${it.title}. Можно оставить так или указать человека в рецепте.") }
            Section("Отдельные фотоистории")
            selectedMoments.forEach { ListTile(it.title, it.people) { } }
            Spacer(Modifier.height(10.dp))
            Text("Книга сохранится в выбранную вами папку. Исходные рецепты и фотографии останутся в приложении.")
            Action("Создать PDF-книгу", { exporter.launch("${title.ifBlank { "Семейная книга" }}.pdf") })
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun SettingsScreen(back: () -> Unit, people: List<Person>, recipes: List<Recipe>,
                   versions: List<RecipeVersion>, moments: List<FamilyMoment>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var familyName by remember { mutableStateOf(prefs.getString("family_name", "Моя семья").orEmpty()) }
    var bookTitle by remember { mutableStateOf(prefs.getString("book_title", "Вкус нашего дома").orEmpty()) }
    var status by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            status = "Сохраняем архив…"
            status = runCatching { withContext(Dispatchers.IO) { FamilyArchive.export(context, uri, people, recipes, versions, moments) } }
                .fold({ "Архив сохранён" }, { "Ошибка: ${it.message}" })
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = "Восстанавливаем архив…"
            status = runCatching { withContext(Dispatchers.IO) { FamilyArchive.import(context, uri) } }
                .fold({ "Архив восстановлен" }, { "Ошибка: ${it.message}" })
        }
    }
    Page("Настройки", back) {
        Section("Ваша семья")
        TextBox(familyName, { familyName = it; prefs.edit().putString("family_name", it).apply() }, "Название семейного архива")
        TextBox(bookTitle, { bookTitle = it; prefs.edit().putString("book_title", it).apply() }, "Название книги по умолчанию")
        Section("Данные и приватность")
        Text("Рецепты, фотографии, аудио и фотоистории хранятся на устройстве. Учётная запись и облачная синхронизация не требуются.")
        Spacer(Modifier.height(10.dp))
        Text("Создайте архив ZIP для переноса и сохраните его вне телефона. Восстановление заменяет текущую коллекцию данными из архива.",
            style = MaterialTheme.typography.bodySmall)
        Action("Сохранить полный архив ZIP", { backup.launch("Вкус детства — архив.zip") })
        OutlinedButton(onClick = { confirmRestore = true }) { Text("Восстановить из ZIP") }
        if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false },
            title = { Text("Заменить текущую коллекцию?") },
            text = { Text("После выбора архива нынешние рецепты, люди и фотоистории будут заменены его содержимым. Сохраните текущий архив заранее.") },
            confirmButton = { TextButton(onClick = { confirmRestore = false; restore.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Выбрать архив") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Отмена") } })
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        Section("О приложении")
        Text("Вкус детства · версия 0.1.0")
    }
}
