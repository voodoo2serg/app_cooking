package ru.vkusdetstva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe

@Composable
fun HomeScreen(recipes: List<Recipe>, people: List<Person>, add: () -> Unit, go: (String) -> Unit) {
    Page("У каждого блюда есть своя история") {
        Text("Сохраните рецепты, людей и воспоминания, которые собирают вашу семью за одним столом.")
        Spacer(Modifier.height(14.dp))
        Action("+ Сохранить семейный рецепт", add)
        Section("Ваша семейная книга")
        ListTile("Рецепты · ${recipes.size}", "Истории, фото и семейные версии") { go("recipes") }
        ListTile("Люди · ${people.size}", "От кого рецепт и кто его готовил") { go("people") }
        ListTile("Подбор по ингредиентам", "Что приготовить из того, что есть дома") { go("pantry") }
        ListTile("Как мы это едим", "Фото и истории за столом, отдельно от рецептов") { go("moments") }
        ListTile("Собрать книгу", "Рецепты, люди и фотоистории") { go("book") }
        TextButton(onClick = { go("settings") }) { Text("Настройки") }
    }
}

@Composable
fun RecipeListScreen(recipes: List<Recipe>, people: List<Person>, back: () -> Unit,
                     open: (Long) -> Unit, add: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Page("Рецепты", back) {
        TextBox(query, { query = it }, "Найти блюдо, продукт или человека")
        val matches = recipes.filter { recipe ->
            query.isBlank() || listOf(recipe.title, recipe.ingredients,
                people.find { it.id == recipe.personId }?.name.orEmpty()).any { it.contains(query, true) }
        }
        matches.forEach { recipe ->
            ListTile(recipe.title, people.find { it.id == recipe.personId }?.name.orEmpty(), { open(recipe.id) })
        }
        if (recipes.isEmpty()) Text("Начните с одного семейного рецепта — добавить его можно вручную или с фотографиями.")
        Action("+ Добавить рецепт", add)
    }
}

@Composable
fun PantryScreen(recipes: List<Recipe>, back: () -> Unit, open: (Long) -> Unit) {
    var input by remember { mutableStateOf("") }
    val have = remember(input) { input.split(',', ';', '\n').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet() }
    val ranked = remember(recipes, have) { recipes.map { recipe ->
        val needed = recipe.ingredients.lines().map { it.substringBefore('—').substringBefore('-').trim().lowercase() }
            .filter { it.isNotBlank() }
        recipe to needed.filter { ingredient -> have.none { it == ingredient || ingredient.startsWith(it) || it.startsWith(ingredient) } }
    }.filter { (_, missing) -> missing.size <= 2 }.sortedBy { it.second.size } }
    Page("Из чего готовим сегодня?", back) {
        Text("Перечислите продукты через запятую. Подбор работает по вашей семейной книге и показывает недостающее.")
        TextBox(input, { input = it }, "Например: яблоки, мука, яйца", 2)
        Section("Подходящие рецепты")
        ranked.forEach { (recipe, missing) ->
            ListTile(recipe.title, if (missing.isEmpty()) "Всё есть" else "Не хватает: ${missing.joinToString()}") { open(recipe.id) }
        }
        if (ranked.isEmpty()) Text("Пока нет совпадений. Добавьте продукты или сохраните рецепт.")
        Text("Проверяйте количество ингредиентов в самом рецепте.", style = MaterialTheme.typography.bodySmall)
    }
}
