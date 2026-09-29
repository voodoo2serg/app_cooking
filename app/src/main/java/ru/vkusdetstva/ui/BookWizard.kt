package ru.vkusdetstva.ui

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import ru.vkusdetstva.util.book.*
import ru.vkusdetstva.billing.DigitalBookBilling
import org.json.JSONObject
import java.io.File

private val coverColors = linkedMapOf(
    "Терракота" to 0xFFA74731, "Янтарь" to 0xFF9A5E16, "Коралл" to 0xFFB64F43,
    "Лес" to 0xFF365847, "Ночь" to 0xFF2E4A62, "Слива" to 0xFF6D3B52, "Кирпич" to 0xFF8C3B2E
)

private fun loadBookOptions(context: android.content.Context): BookOptions {
    val mainPrefs = context.getSharedPreferences("settings", 0)
    val family = mainPrefs.getString("family_name", null)?.takeIf { it.isNotBlank() } ?: "Моя семья"

    val title = mainPrefs.getString("book_title", null)?.takeIf { it.isNotBlank() } ?: "Фамильные рецепты"
    val base = BookOptions(familyName = family, title = title)
    val raw = context.getSharedPreferences("book_options", 0).getString("json", null) ?: return base
    return runCatching {
        val o = JSONObject(raw)
        base.copy(
            title = o.optString("title", base.title),
            familyName = o.optString("familyName", base.familyName),
            edition = Edition.valueOf(o.optString("edition", base.edition.name)),
            coverTemplate = CoverTemplate.valueOf(o.optString("coverTemplate", base.coverTemplate.name)),
            coverBackground = CoverBackground.valueOf(o.optString("coverBackground", base.coverBackground.name)),
            coverColor = o.optInt("coverColor", base.coverColor),
            accentColor = o.optInt("accentColor", base.accentColor),
            pageTone = PageTone.valueOf(o.optString("pageTone", base.pageTone.name)),
            headingFont = FontChoice.valueOf(o.optString("headingFont", base.headingFont.name)),
            bodyFont = FontChoice.valueOf(o.optString("bodyFont", base.bodyFont.name)),
            includePeople = o.optBoolean("includePeople", true),
            includeMoments = o.optBoolean("includeMoments", true),
            includeLined = o.optBoolean("includeLined", true),
            linedPageCount = o.optInt("linedPageCount", 4),
            showRatings = o.optBoolean("showRatings", true),
            showTimesCooked = o.optBoolean("showTimesCooked", true),
            maxVersions = o.optInt("maxVersions", 3)
        )
    }.getOrDefault(base)
}

private fun saveBookOptions(context: android.content.Context, options: BookOptions) {
    val json = JSONObject()
        .put("title", options.title)
        .put("familyName", options.familyName)
        .put("edition", options.edition.name)
        .put("coverTemplate", options.coverTemplate.name)
        .put("coverBackground", options.coverBackground.name)
        .put("coverColor", options.coverColor)
        .put("accentColor", options.accentColor)
        .put("pageTone", options.pageTone.name)
        .put("headingFont", options.headingFont.name)
        .put("bodyFont", options.bodyFont.name)
        .put("includePeople", options.includePeople)
        .put("includeMoments", options.includeMoments)
        .put("includeLined", options.includeLined)
        .put("linedPageCount", options.linedPageCount)
        .put("showRatings", options.showRatings)
        .put("showTimesCooked", options.showTimesCooked)
        .put("maxVersions", options.maxVersions)
    context.getSharedPreferences("book_options", 0).edit().putString("json", json.toString()).apply()
}

@Composable
fun BookWizard(people: List<Person>, recipes: List<Recipe>, versions: List<RecipeVersion>,
               moments: List<FamilyMoment>, authorName: String = "", back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf(loadBookOptions(context)) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var lastPdf by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val billing = remember { DigitalBookBilling(context.applicationContext) }
    val billingState by billing.state.collectAsState()
    DisposableEffect(billing) { onDispose { billing.close() } }
    val debugBuild = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val canExport = debugBuild || billingState.unlocked

    val update: (BookOptions) -> Unit = {
        options = it
        saveBookOptions(context, it)
    }

    fun fileName(booklet: Boolean): String {
        val base = options.title.ifBlank { "Семейная книга" }
        return if (booklet) "$base — печать A4 пополам.pdf" else "$base.pdf"
    }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            progress = 0f
            status = "Собираем книгу…"
            val result = runCatching {
                val model = BookComposer.compose(people, recipes, versions, moments, options, authorName.takeIf { it.isNotBlank() })
                withContext(Dispatchers.IO) {
                    BookRenderer.render(context, model, uri, booklet = false) { done, total ->
                        progress = if (total == 0) 0f else done.toFloat() / total
                    }
                }
                model.pages.size
            }
            busy = false
            result.fold({ pages ->
                status = "Книга сохранена · около $pages страниц"
                lastPdf = uri
            }, { status = "Ошибка: ${it.message}" })
        }
    }
    val saveBooklet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            progress = 0f
            status = "Собираем печатные листы…"
            val result = runCatching {
                val model = BookComposer.compose(people, recipes, versions, moments, options, authorName.takeIf { it.isNotBlank() })
                withContext(Dispatchers.IO) {
                    BookRenderer.render(context, model, uri, booklet = true) { done, total ->
                        progress = if (total == 0) 0f else done.toFloat() / total
                    }
                }
            }
            busy = false
            result.fold({
                status = "Готово: печатайте A4-альбомно, складывайте пополам, скрепляйте по центру"
                lastPdf = uri
            }, { status = "Ошибка: ${it.message}" })
        }
    }

    LaunchedEffect(options.coverTemplate, options.coverBackground, options.coverColor,
        options.accentColor, options.headingFont, options.title, options.familyName,
        recipes.size, people.size, moments.size) {
        if (recipes.isNotEmpty()) {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { BookRenderer.coverPreview(options, people, recipes, moments, 210, 298) }.getOrNull()
            }
            preview = bitmap
        }
    }

    Page("Собираем книгу", back) {
        Section("Издание")
        val editions = listOf(
            Triple(Edition.BY_POPULARITY, "Легенды стола", "Главы по тому, как часто готовят"),
            Triple(Edition.BY_PEOPLE, "По людям", "Глава на каждого: рецепты, фото, годы"),
            Triple(Edition.CHRONOLOGY, "Как росла книга", "По годам — от первого рецепта")
        )
        editions.forEach { (edition, name, description) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { update(options.copy(edition = edition)) },
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = options.edition == edition, onClick = { update(options.copy(edition = edition)) })
                Column {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Section("Оформление")
        TextBox(options.title, { update(options.copy(title = it)) }, "Название книги на обложке")
        TextBox(options.familyName, { update(options.copy(familyName = it)) }, "Название семьи")

        ChoiceChips("Шаблон обложки", listOf(
            "Классика" to CoverTemplate.CLASSIC, "Фото" to CoverTemplate.PHOTO, "Архив" to CoverTemplate.ARCHIVE
        ), options.coverTemplate) { update(options.copy(coverTemplate = it)) }

        ChoiceChips("Фон обложки", listOf(
            "Цвет" to CoverBackground.SOLID, "Фото" to CoverBackground.PHOTO, "Узор" to CoverBackground.PATTERN
        ), options.coverBackground) { update(options.copy(coverBackground = it)) }

        ColorSwatches("Цвет обложки", options.coverColor) { update(options.copy(coverColor = it)) }
        ColorSwatches("Акцентный цвет внутри книги", options.accentColor) { update(options.copy(accentColor = it)) }

        ChoiceChips("Бумага страниц", listOf("Белая" to PageTone.WHITE, "Кремовая" to PageTone.CREAM),
            options.pageTone) { update(options.copy(pageTone = it)) }
        ChoiceChips("Заголовки", listOf("С засечками" to FontChoice.SERIF, "Рубленые" to FontChoice.SANS),
            options.headingFont) { update(options.copy(headingFont = it)) }
        ChoiceChips("Основной текст", listOf("Рубленый" to FontChoice.SANS, "С засечками" to FontChoice.SERIF),
            options.bodyFont) { update(options.copy(bodyFont = it)) }

        Section("Состав книги")
        SwitchRow("Раздел «Люди нашей книги»", options.includePeople) { update(options.copy(includePeople = it)) }
        SwitchRow("Раздел «Как мы это едим»", options.includeMoments) { update(options.copy(includeMoments = it)) }
        SwitchRow("Пустые страницы для новых рецептов", options.includeLined) { update(options.copy(includeLined = it)) }
        SwitchRow("Оценки семьи на разворотах", options.showRatings) { update(options.copy(showRatings = it)) }
        SwitchRow("Счётчик «Готовили N раз»", options.showTimesCooked) { update(options.copy(showTimesCooked = it)) }
        ChoiceChips("Семейных версий на рецепт", listOf("1" to 1, "3" to 3, "5" to 5, "Все" to 99),
            options.maxVersions) { update(options.copy(maxVersions = it)) }

        if (recipes.isNotEmpty()) {
            Section("Обложка · нажмите, чтобы сменить шаблон")
            preview?.let { bitmap ->
                Image(bitmap.asImageBitmap(), contentDescription = "Превью обложки",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .width(120.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            val next = when (options.coverTemplate) {
                                CoverTemplate.CLASSIC -> CoverTemplate.PHOTO
                                CoverTemplate.PHOTO -> CoverTemplate.ARCHIVE
                                CoverTemplate.ARCHIVE -> CoverTemplate.CLASSIC
                            }
                            update(options.copy(coverTemplate = next))
                        })
            }
            Text("Шаблон: ${when (options.coverTemplate) {
                CoverTemplate.CLASSIC -> "Классика"
                CoverTemplate.PHOTO -> "Фото"
                CoverTemplate.ARCHIVE -> "Архив"
            }} · цвет: ${coverColors.entries.firstOrNull { it.value.toInt() == options.coverColor }?.key ?: "свой"}",
                style = MaterialTheme.typography.bodySmall)
        }

        Section("Электронная книга")
        Text("Соберите красивый PDF для хранения в семейном архиве и отправки родным.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        if (!canExport) {
            Text("Предпросмотр книги бесплатный. Полный PDF открывается разовой покупкой — без подписки.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { context.findActivity()?.let { billing.launchPurchase(it) } },
                enabled = billingState.productAvailable && !busy,
                modifier = Modifier.fillMaxWidth()) {
                Text(if (billingState.price.isNotBlank()) "Открыть полный PDF · ${billingState.price}"
                    else if (billingState.productAvailable) "Открыть полный PDF"
                    else "Экспорт скоро будет доступен")
            }
            if (billingState.message.isNotBlank()) Text(billingState.message, style = MaterialTheme.typography.bodySmall)
        }
        Action("Сохранить электронную книгу PDF", { savePdf.launch(fileName(false)) },
            recipes.isNotEmpty() && !busy && canExport)
        if (lastPdf != null && !busy) {
            OutlinedButton(onClick = {
                runCatching {
                    val exports = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(exports, "family-book.pdf")
                    context.contentResolver.openInputStream(lastPdf!!)?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    val shared = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, shared)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Отправить книгу"))
                }.onFailure { status = "Ошибка: ${it.message}" }
            }, modifier = Modifier.fillMaxWidth()) { Text("Поделиться файлом") }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall,
                color = if (status.startsWith("Ошибка")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        if (recipes.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Добавьте первый семейный рецепт — и книга соберётся сама.",
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun <T> ChoiceChips(label: String, values: List<Pair<String, T>>, current: T, onPick: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        values.forEach { (name, value) ->
            FilterChip(selected = current == value, onClick = { onPick(value) }, label = { Text(name) })
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ColorSwatches(label: String, current: Int, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        coverColors.forEach { (name, color) ->
            val value = color.toInt()
            Box(Modifier.size(32.dp).clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color(value))
                .clickable { onPick(value) },
                contentAlignment = Alignment.Center) {
                if (value == current) {
                    Box(Modifier.size(12.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFFFFFFFF)))
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
