package ru.vkusdetstva.ui

import ru.vkusdetstva.data.Recipe
import java.util.Locale

data class PantryIngredient(val name: String, val category: String, val aliases: List<String>)
data class RecipeMatch(val recipe: Recipe, val matched: Int, val total: Int, val missing: List<String>)

/** Small offline starter dictionary. Users can still add an uncommon ingredient in the picker. */
object IngredientCatalog {
    private fun entry(name: String, category: String, vararg aliases: String) =
        PantryIngredient(name, category, listOf(name) + aliases)

    val groups = linkedMapOf(
        "Мука и крупы" to listOf(
            entry("Мука", "Мука и крупы", "мук", "пшеничн"), entry("Овсяные хлопья", "Мука и крупы", "овсян", "геркулес"),
            entry("Рис", "Мука и крупы", "рис"), entry("Манка", "Мука и крупы", "манн"),
            entry("Гречка", "Мука и крупы", "греч"), entry("Кукурузная мука", "Мука и крупы", "кукурузн"),
            entry("Крахмал", "Мука и крупы", "крахмал"), entry("Макароны", "Мука и крупы", "макарон")),
        "Молочное и яйца" to listOf(
            entry("Яйца", "Молочное и яйца", "яйц", "яиц"), entry("Молоко", "Молочное и яйца", "молок"),
            entry("Сливки", "Молочное и яйца", "сливк"), entry("Сметана", "Молочное и яйца", "сметан"),
            entry("Творог", "Молочное и яйца", "творог"), entry("Кефир", "Молочное и яйца", "кефир"),
            entry("Сыр", "Молочное и яйца", "сыр"), entry("Сливочное масло", "Молочное и яйца", "сливочное масло", "масло сливочное"),
            entry("Йогурт", "Молочное и яйца", "йогурт")),
        "Овощи и зелень" to listOf(
            entry("Картофель", "Овощи и зелень", "картош", "картоф"), entry("Морковь", "Овощи и зелень", "морков"),
            entry("Лук", "Овощи и зелень", "лук", "луков"), entry("Чеснок", "Овощи и зелень", "чеснок"),
            entry("Помидоры", "Овощи и зелень", "помидор", "томат"), entry("Огурцы", "Овощи и зелень", "огурц"),
            entry("Капуста", "Овощи и зелень", "капуст"), entry("Свёкла", "Овощи и зелень", "свекл"),
            entry("Тыква", "Овощи и зелень", "тыкв"), entry("Кабачки", "Овощи и зелень", "кабач"),
            entry("Грибы", "Овощи и зелень", "гриб"), entry("Укроп", "Овощи и зелень", "укроп"),
            entry("Петрушка", "Овощи и зелень", "петруш")),
        "Фрукты и ягоды" to listOf(
            entry("Яблоки", "Фрукты и ягоды", "яблок", "яблоч"), entry("Груши", "Фрукты и ягоды", "груш"),
            entry("Бананы", "Фрукты и ягоды", "банан"), entry("Лимон", "Фрукты и ягоды", "лимон"),
            entry("Апельсин", "Фрукты и ягоды", "апельсин"), entry("Клубника", "Фрукты и ягоды", "клубник"),
            entry("Вишня", "Фрукты и ягоды", "вишн"), entry("Малина", "Фрукты и ягоды", "малин"),
            entry("Смородина", "Фрукты и ягоды", "смородин"), entry("Сухофрукты", "Фрукты и ягоды", "изюм", "кураг", "чернослив")),
        "Мясо и рыба" to listOf(
            entry("Курица", "Мясо и рыба", "куриц", "курин"), entry("Говядина", "Мясо и рыба", "говядин"),
            entry("Свинина", "Мясо и рыба", "свинин"), entry("Фарш", "Мясо и рыба", "фарш"),
            entry("Рыба", "Мясо и рыба", "рыб"), entry("Колбаса", "Мясо и рыба", "колбас")),
        "Выпечка и сладкое" to listOf(
            entry("Сахар", "Выпечка и сладкое", "сахар"), entry("Мёд", "Выпечка и сладкое", "мед"),
            entry("Какао", "Выпечка и сладкое", "какао"), entry("Шоколад", "Выпечка и сладкое", "шоколад"),
            entry("Дрожжи", "Выпечка и сладкое", "дрожж"), entry("Разрыхлитель", "Выпечка и сладкое", "разрыхл"),
            entry("Сода", "Выпечка и сладкое", "сод"), entry("Ваниль", "Выпечка и сладкое", "ванил"),
            entry("Орехи", "Выпечка и сладкое", "орех"), entry("Варенье", "Выпечка и сладкое", "варень", "джем")),
        "Основа и специи" to listOf(
            entry("Соль", "Основа и специи", "сол"), entry("Вода", "Основа и специи", "вод"),
            entry("Растительное масло", "Основа и специи", "растительное масло", "подсолнечное масло", "оливковое масло"),
            entry("Перец", "Основа и специи", "перец", "перц"), entry("Корица", "Основа и специи", "кориц"),
            entry("Уксус", "Основа и специи", "уксус"))
    )
    val all = groups.values.flatten()

    fun normalize(raw: String): String = raw.lowercase(Locale.ROOT).replace('ё', 'е')
        .replace(Regex("[^а-яa-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun find(raw: String): PantryIngredient? {
        val words = normalize(raw).split(' ')
        return all.flatMap { item -> item.aliases.map { item to normalize(it) } }
            .sortedByDescending { it.second.length }
            .firstOrNull { (_, alias) ->
                val parts = alias.split(' ')
                if (parts.size == 1) words.any { it.startsWith(alias) }
                else normalize(raw).contains(alias)
            }?.first
    }

    fun match(recipe: Recipe, selected: Set<String>): RecipeMatch {
        val needed = recipe.ingredients.split('\n', ';').map { it.substringBefore('—').substringBefore(" - ").trim() }
            .filter { it.isNotBlank() }.map { line -> find(line)?.name ?: line }
            .distinctBy(::normalize)
        val missing = needed.filter { normalize(it) !in selected.map(::normalize).toSet() }
        return RecipeMatch(recipe, needed.size - missing.size, needed.size, missing)
    }
}
