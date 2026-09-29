package ru.vkusdetstva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.vkusdetstva.util.importers.ClipboardRecipeParser
import ru.vkusdetstva.util.importers.ImportEngine
import ru.vkusdetstva.util.importers.RecipeDraft
import ru.vkusdetstva.util.importers.RecipeImportClient

/**
 * Импорт рецепта из фото страницы книги или тетради: снимки уходят в выбранный
 * сервис распознавания (ключ — в настройках), назад приходит черновик рецепта.
 * Второй путь — вставка текста из буфера (скопированного, например, через Google Lens).
 */
@Composable
fun ImportScreen(back: () -> Unit, openSettings: () -> Unit,
                 onDraft: (RecipeDraft, List<String>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    val clipboard = LocalClipboardManager.current
    var photos by remember { mutableStateOf(listOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val hasKey = remember { !prefs.getString("import_api_key", "").isNullOrBlank() }

    Page("Импорт рецепта из фото", back) {
        Text("Сфотографируйте страницу кулинарной книги или рукописной тетради — рецепт сам ляжет в форму, останется проверить. Фотографии уходят в выбранный сервис распознавания только в момент импорта.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoEditor(photos, { photos = it }, "Фотографии страниц · можно несколько")
        if (!hasKey) {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Нужен API-ключ сервиса распознавания",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Бесплатный ключ Gemini создаётся на aistudio.google.com/app/apikey. Ключ хранится только на устройстве.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = openSettings) { Text("Открыть настройки") }
                }
            }
        }
        Action("Распознать рецепт", {
            loading = true
            message = ""
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val engine = ImportEngine.byId(prefs.getString("import_engine", null))
                        RecipeImportClient.recognize(context, engine,
                            prefs.getString("import_model", engine.defaultModel).orEmpty(),
                            prefs.getString("import_api_key", "").orEmpty(), photos)
                    }
                }
                loading = false
                result.fold(onSuccess = { draft -> onDraft(draft, photos) },
                    onFailure = { error -> message = error.message ?: "Не получилось распознать" })
            }
        }, enabled = photos.isNotEmpty() && !loading)
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Читаем страницу — это может занять до минуты…",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Section("Или вставьте готовый текст")
        Text("Сфотографируйте страницу в Google Lens, нажмите «Копировать текст» и вставьте сюда — название, ингредиенты и шаги разложатся сами.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            val draft = ClipboardRecipeParser.parse(clipboard.getText()?.text.orEmpty())
            if (draft == null) message = "Буфер обмена пуст — сначала скопируйте текст рецепта."
            else onDraft(draft, emptyList())
        }, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text("Вставить рецепт из буфера")
        }
        Spacer(Modifier.height(24.dp))
    }
}
