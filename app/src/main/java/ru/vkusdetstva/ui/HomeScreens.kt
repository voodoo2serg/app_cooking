package ru.vkusdetstva.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import java.io.File
import org.json.JSONArray
import ru.vkusdetstva.util.ShoppingListBuilder
import ru.vkusdetstva.util.FamilyShare

@Composable
fun HomeScreen(recipes: List<Recipe>, people: List<Person>, add: () -> Unit,
               go: (String) -> Unit, openRecipe: (Long) -> Unit, authorName: String = "") {
    Page("У каждого блюда — своя история") {
        Text("Сохраняйте рецепты, фотографии и голоса тех, кто собирал семью за одним столом.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        val readiness = BookReadiness.calculate(recipes, people, authorName)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().clickable { go("book") }) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("ВАША КНИГА ГОТОВА НА ${readiness.percent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { readiness.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("≈ ${readiness.estimatedPages} страниц · ${recipes.size} рецептов · ${people.size} героев семейного стола",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                readiness.hints.firstOrNull()?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("СЕМЕЙНЫЙ АРХИВ", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text("Вкус, который остаётся с нами", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text("Начните с блюда, которое хочется передать дальше.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(16.dp))
                Button(onClick = add, modifier = Modifier.heightIn(min = 48.dp)) { Text("+ Записать рецепт") }
            }
        }
        Section("Рецепты · ${recipes.size}")
        if (recipes.isEmpty()) {
            ListTile("Пока нет рецептов", "Добавьте первый семейный рецепт с историей и фото", add)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recipes.take(8)) { recipe ->
                    RecipePreview(recipe, people.find { it.id == recipe.personId }?.name
                        ?: if (authorName.isNotBlank()) "От лица автора · $authorName" else "") { openRecipe(recipe.id) }
                }
            }
            TextButton(onClick = { go("recipes") }) { Text("Все рецепты →") }
        }
        Section("Люди нашей семьи · ${people.size}")
        if (people.isEmpty()) {
            ListTile("Чьи рецепты вы храните?", "Добавьте бабушку, маму или другого близкого человека") { go("people") }
        } else {
            people.take(3).forEach { person ->
                ListTile(person.name, "${person.relation} · ${recipes.count { it.personId == person.id }} рецептов") { go("people") }
            }
            TextButton(onClick = { go("people") }) { Text("Все люди →") }
        }
        Section("Продолжить историю")

        ListTile("Импорт из фото книги", "Распознать рецепт со страницы — печатной или рукописной") { go("import") }
        ListTile("Подбор по ингредиентам", "Выберите продукты из разделов и найдите подходящее блюдо") { go("pantry") }
        ListTile("Как мы это едим", "Фотографии семьи за столом и воспоминания") { go("moments") }
        ListTile("События и столы", "Пасха, Новый год, свадьба — несколько блюд за одним столом") { go("events") }
        ListTile("Лента семейного стола", "Рецепты и застолья этого архива · отметьте любимые") { go("feed") }
        ListTile("Список в магазин", "Блюда на ужин → продукты понятными упаковками") { go("shopping") }
        ListTile("Собрать семейную книгу", "Выберите рецепты и проверьте связи с людьми") { go("book") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ShoppingListScreen(recipes: List<Recipe>, back: () -> Unit, initialRecipeIds: List<Long> = emptyList()) {
    val context = LocalContext.current
    val selected = remember(initialRecipeIds) { mutableStateListOf<Long>().apply { addAll(initialRecipeIds) } }
    val list = remember(recipes, selected.toList()) { ShoppingListBuilder.build(recipes.filter { it.id in selected }) }
    Page("Список в магазин", back) {
        Text("Выберите блюда. Список можно отправить ребёнку или другому близкому человеку.")
        recipes.forEach { recipe ->
            CheckboxRow(recipe.title, recipe.id in selected) {
                if (recipe.id in selected) selected.remove(recipe.id) else selected.add(recipe.id)
            }
        }
        Section("Купить · ${list.size}")
        if (selected.isEmpty()) Text("Сначала выберите хотя бы одно блюдо.")
        list.groupBy { it.section }.forEach { (section, items) ->
            Text(section, style = MaterialTheme.typography.titleMedium)
            items.forEach { Text("• ${it.name} — ${it.amount}") }
        }
        if (list.isNotEmpty()) {
            Text("Упаковки ориентировочные: перед покупкой сверьте количество с рецептами.",
                style = MaterialTheme.typography.bodySmall)
            Action("Отправить список", {
                FamilyShare.shareText(context, "Список в магазин", ShoppingListBuilder.asMessage(list))
            })
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onChange).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onChange() })
        Text(label)
    }
}

@Composable
private fun RecipePreview(recipe: Recipe, author: String, onClick: () -> Unit) {
    Card(Modifier.width(236.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)) {
        val image = recipe.photos.firstOrNull()
        if (image != null) {
            AsyncImage(File(image), contentDescription = "Блюдо ${recipe.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(145.dp))
        } else {
            Box(Modifier.fillMaxWidth().height(145.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Text("Семейный рецепт", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(author.ifBlank { "Наша семейная книга" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Готовили ${recipe.timesCooked} раз", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun RecipeListScreen(recipes: List<Recipe>, people: List<Person>, back: () -> Unit,
                     open: (Long) -> Unit, add: () -> Unit, authorName: String = "") {
    var query by remember { mutableStateOf("") }
    Page("Рецепты", back) {
        TextBox(query, { query = it }, "Название или продукт")
        recipes.filter { recipe -> query.isBlank() ||
            recipe.title.contains(query, true) || recipe.ingredients.contains(query, true) ||
            people.find { it.id == recipe.personId }?.name?.contains(query, true) == true ||
            (recipe.personId == null && authorName.contains(query, true) && query.isNotBlank())
        }.forEach { recipe ->
            ListTile(recipe.title, people.find { it.id == recipe.personId }?.name
                ?: if (authorName.isNotBlank()) "От лица автора · $authorName" else "Семейный рецепт") { open(recipe.id) }
        }
        if (recipes.isEmpty()) Text("Начните с одного семейного рецепта — добавьте его вручную и прикрепите фото.")
        Action("+ Добавить рецепт", add)
    }
}

@Composable
fun SearchScreen(recipes: List<Recipe>, people: List<Person>, back: () -> Unit,
                 openRecipe: (Long) -> Unit, openPerson: (Long) -> Unit,
                 selectIngredient: (String) -> Unit, authorName: String = "") {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    val normalized = IngredientCatalog.normalize(query)
    Page("Поиск в семейном архиве", back) {
        TextBox(query, { query = it }, "Блюдо, человек или ингредиент")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Все", "Блюда", "Люди", "Продукты")) { label ->
                FilterChip(category == label, onClick = { category = label }, label = { Text(label) })
            }
        }
        if (query.isBlank()) {
            Text("Найдите пирог по названию, рецепт по имени бабушки или продукт для выпечки.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Section("Быстрый переход")
            ListTile("Выбрать продукты", "Каталог ингредиентов по разделам") { selectIngredient("") }
        } else {
            val foundPeople = people.filter { IngredientCatalog.normalize(it.name + " " + it.relation).contains(normalized) }
            val foundRecipes = recipes.filter { recipe ->
                IngredientCatalog.normalize(recipe.title + " " + recipe.ingredients + " " +
                    (people.find { it.id == recipe.personId }?.name ?: authorName)).contains(normalized) ||
                    IngredientCatalog.find(query)?.let { selected ->
                        recipe.ingredients.lines().any { IngredientCatalog.find(it)?.name == selected.name }
                    } == true
            }
            val foundIngredients = IngredientCatalog.all.filter {
                IngredientCatalog.normalize(it.name).contains(normalized) || it.aliases.any { alias ->
                    IngredientCatalog.normalize(alias).contains(normalized)
                }
            }.take(12)
            if (category == "Все" || category == "Блюда") {
                Section("Блюда · ${foundRecipes.size}")
                foundRecipes.forEach { recipe ->
                    ListTile(recipe.title, people.find { it.id == recipe.personId }?.name.orEmpty()) { openRecipe(recipe.id) }
                }
            }
            if (category == "Все" || category == "Люди") {
                Section("Люди · ${foundPeople.size}")
                foundPeople.forEach { person -> ListTile(person.name, person.relation) { openPerson(person.id) } }
            }
            if (category == "Все" || category == "Продукты") {
                Section("Ингредиенты · ${foundIngredients.size}")
                foundIngredients.forEach { ingredient ->
                    ListTile(ingredient.name, ingredient.category) { selectIngredient(ingredient.name) }
                }
            }
            if (foundRecipes.isEmpty() && foundPeople.isEmpty() && foundIngredients.isEmpty()) {
                Text("Ничего не найдено. Попробуйте другое слово.")
            }
        }
    }
}

@Composable
fun PantryScreen(recipes: List<Recipe>, back: () -> Unit, open: (Long) -> Unit,
                 initialIngredient: String? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pantry", 0) }
    val selected = remember(initialIngredient) { mutableStateListOf<String>().apply {
        val saved = JSONArray(prefs.getString("selected", "[]"))
        for (i in 0 until saved.length()) add(saved.getString(i))
        if (!initialIngredient.isNullOrBlank() && !contains(initialIngredient)) add(initialIngredient)
    } }
    val customNames = remember { mutableStateListOf<String>().apply {
        val saved = JSONArray(prefs.getString("custom", "[]"))
        for (i in 0 until saved.length()) add(saved.getString(i))
    } }
    LaunchedEffect(selected.toList(), customNames.toList()) {
        prefs.edit().putString("selected", JSONArray(selected.toList()).toString())
            .putString("custom", JSONArray(customNames.toList()).toString()).apply()
    }
    var category by remember { mutableStateOf(IngredientCatalog.groups.keys.first()) }
    var filter by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val visible = if (filter.isBlank()) IngredientCatalog.groups[category].orEmpty() else
        IngredientCatalog.all.filter { it.name.contains(filter, true) || it.aliases.any { a -> a.contains(filter, true) } }
    val ranked = recipes.map { IngredientCatalog.match(it, selected.toSet()) }
        .filter { it.matched > 0 && it.missing.size <= 3 }
        .sortedWith(compareBy<RecipeMatch> { it.missing.size }.thenByDescending { it.matched })
    Page("Подбор по ингредиентам", back) {
        Text("Что есть дома? Выберите продукты, а мы найдём рецепты вашей семьи и покажем, чего не хватает.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        TextBox(filter, { filter = it }, "Найти продукт в каталоге")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(IngredientCatalog.groups.keys.toList()) { name ->
                FilterChip(category == name && filter.isBlank(), onClick = { category = name; filter = "" },
                    label = { Text(name) })
            }
        }
        Section(if (filter.isBlank()) category else "Результаты каталога")
        if (visible.isEmpty()) Text("Такого продукта нет в каталоге — добавьте его ниже.")
        visible.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    FilterChip(selected.contains(item.name),
                        onClick = { if (selected.contains(item.name)) selected.remove(item.name) else selected.add(item.name) },
                        label = { Text(item.name, maxLines = 2) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(custom, { custom = it }, label = { Text("Другой продукт") },
                singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val name = custom.trim()
                if (name.isNotBlank() && selected.none { it.equals(name, true) }) selected.add(name)
                if (name.isNotBlank() && IngredientCatalog.find(name) == null &&
                    customNames.none { it.equals(name, true) }) customNames.add(name)
                custom = ""
            }, enabled = custom.isNotBlank()) { Text("Добавить") }
        }
        if (customNames.isNotEmpty()) {
            Section("Свои продукты")
            customNames.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        FilterChip(selected.contains(item),
                            onClick = { if (selected.contains(item)) selected.remove(item) else selected.add(item) },
                            label = { Text(item, maxLines = 2) }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Section("У вас есть · ${selected.size}")
        if (selected.isEmpty()) Text("Выберите хотя бы один ингредиент из разделов выше.")
        selected.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item ->
                    InputChip(selected = true, onClick = { selected.remove(item) },
                        label = { Text("$item ×") })
                }
            }
        }
        Section("Что можно приготовить · ${ranked.size}")
        if (selected.isNotEmpty() && ranked.isEmpty()) {
            Text(if (recipes.isEmpty()) "Пока нет семейных рецептов. Добавьте первый рецепт."
                else "Совпадений пока нет. Выберите ещё продукты или проверьте написание ингредиентов в рецептах.")
        }
        ranked.forEach { match ->
            val summary = if (match.missing.isEmpty()) "Всё есть · ${match.matched} из ${match.total}"
            else "Есть ${match.matched} из ${match.total} · не хватает: ${match.missing.joinToString()}"
            ListTile(match.recipe.title, summary) { open(match.recipe.id) }
        }
        if (selected.isNotEmpty()) Text("Количество продуктов проверьте в самом рецепте.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}
