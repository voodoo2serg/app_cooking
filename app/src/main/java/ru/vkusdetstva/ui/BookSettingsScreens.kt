package ru.vkusdetstva.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.BuildConfig
import ru.vkusdetstva.data.AuthorProfile
import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import ru.vkusdetstva.data.FamilyEvent
import ru.vkusdetstva.util.FamilyArchive

import ru.vkusdetstva.util.importers.ImportEngine

import ru.vkusdetstva.feed.RemoteFamilyFeedClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(back: () -> Unit, people: List<Person>, recipes: List<Recipe>,
                   versions: List<RecipeVersion>, moments: List<FamilyMoment>,
                   events: List<FamilyEvent> = emptyList(), author: AuthorProfile? = null, darkTheme: Boolean = false,
                   onDarkThemeChange: (Boolean) -> Unit = {}, openAuthor: () -> Unit = {},
                   openImport: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var familyName by remember { mutableStateOf(prefs.getString("family_name", "Моя семья").orEmpty()) }
    var bookTitle by remember { mutableStateOf(prefs.getString("book_title", "Вкус нашего дома").orEmpty()) }
    var status by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    var feedUrl by remember { mutableStateOf(prefs.getString("family_feed_url", "").orEmpty()) }
    var feedCode by remember { mutableStateOf(prefs.getString("family_feed_code", "").orEmpty()) }
    var feedStatus by remember { mutableStateOf("") }
    val feedClient = remember { RemoteFamilyFeedClient(context.applicationContext) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            status = "Сохраняем архив…"
            status = runCatching { withContext(Dispatchers.IO) { FamilyArchive.export(context, uri, people, recipes, versions, moments, author, events) } }
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
        Section("Профиль автора")
        Card(Modifier.fillMaxWidth().clickable(onClick = openAuthor),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AuthorAvatar(author?.photos.orEmpty(), author?.name.orEmpty(), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(author?.name?.takeIf { it.isNotBlank() } ?: "Автор ещё не заполнен",
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(author?.name?.takeIf { it.isNotBlank() }?.let { n ->
                        listOf(author?.tagline.orEmpty(), "неподписанные рецепты — от его лица")
                            .filter { it.isNotBlank() }.joinToString(" · ")
                    } ?: "Рецепты без подписи выходят от лица автора",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Section("Оформление")
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Тёмная тема", style = MaterialTheme.typography.titleMedium)
                Text("Тёплые тёмные цвета для вечера", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
        }
        Section("Импорт рецептов из фото")
        Text("Сфотографируйте страницу книги — рецепт распознается и ляжет в форму черновиком. Нужен API-ключ выбранного сервиса, он хранится только на устройстве.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        var engine by remember {
            mutableStateOf(ImportEngine.byId(prefs.getString("import_engine", null)))
        }
        var importModel by remember {
            mutableStateOf(prefs.getString("import_model", null) ?: engine.defaultModel)
        }
        var importKey by remember { mutableStateOf(prefs.getString("import_api_key", "").orEmpty()) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportEngine.entries.forEach { option ->
                FilterChip(selected = engine == option, onClick = {
                    val previous = engine
                    engine = option
                    if (importModel.isBlank() || importModel == previous.defaultModel) {
                        importModel = option.defaultModel
                    }
                    prefs.edit().putString("import_engine", option.name).apply()
                }, label = { Text(option.label, maxLines = 1) })
            }
        }
        TextBox(importModel, {
            importModel = it
            prefs.edit().putString("import_model", it).apply()
        }, "Название модели (например, ${engine.defaultModel})")
        TextBox(importKey, {
            importKey = it
            prefs.edit().putString("import_api_key", it.trim()).apply()
        }, "API-ключ — хранится только на устройстве")
        Text("Бесплатный ключ Gemini: aistudio.google.com/app/apikey. Ключ OpenAI: platform.openai.com/api-keys.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Action("Открыть импорт из фото", openImport)
        Section("Ваша семья")
        TextBox(familyName, { familyName = it; prefs.edit().putString("family_name", it).apply() }, "Название семейного архива")
        TextBox(bookTitle, { bookTitle = it; prefs.edit().putString("book_title", it).apply() }, "Название книги по умолчанию")
        Section("Общая семейная лента")
        Text("Подключайте только тех, кому доверяете. В комнате нет дерева семьи, дат рождения и контактов — публикуются только выбранные рецепты и истории.",
            style = MaterialTheme.typography.bodySmall)
        TextBox(feedUrl, { feedUrl = it }, "HTTPS-адрес сервера семейной ленты")
        TextBox(feedCode, { feedCode = it }, "Код семьи")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    feedStatus = "Создаём семейную ленту…"
                    feedStatus = runCatching {
                        val code = withContext(Dispatchers.IO) { feedClient.createFamily(feedUrl) }
                        feedCode = code
                        "Готово. Код семьи: $code"
                    }.getOrElse { "Ошибка: ${it.message}" }
                }
            }, enabled = feedUrl.startsWith("https://")) { Text("Создать") }
            OutlinedButton(onClick = {
                scope.launch {
                    feedStatus = "Подключаемся…"
                    feedStatus = runCatching {
                        withContext(Dispatchers.IO) { feedClient.joinFamily(feedUrl, feedCode) }
                        "Подключено к общей семейной ленте"
                    }.getOrElse { "Ошибка: ${it.message}" }
                }
            }, enabled = feedUrl.startsWith("https://") && feedCode.isNotBlank()) { Text("Подключиться") }
        }
        if (feedStatus.isNotBlank()) Text(feedStatus, style = MaterialTheme.typography.bodySmall)
        Section("Данные и приватность")
        Text("Рецепты, события, фотографии, аудио и фотоистории хранятся на устройстве. Общая лента между телефонами потребует серверной синхронизации.")
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

        Text("Вкус детства · версия ${BuildConfig.VERSION_NAME} (код ${BuildConfig.VERSION_CODE})")
    }
}
