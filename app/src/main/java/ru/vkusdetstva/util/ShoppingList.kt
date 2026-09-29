package ru.vkusdetstva.util

import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.ui.IngredientCatalog
import ru.vkusdetstva.ui.parseIngredientLines

data class ShoppingItem(val section: String, val name: String, val amount: String)

object ShoppingListBuilder {
    fun build(recipes: List<Recipe>): List<ShoppingItem> {
        val raw = recipes.flatMap { recipe ->
            parseIngredientLines(recipe.ingredients).map { line ->
                val catalog = IngredientCatalog.find(line.name)
                Triple(catalog?.section ?: "Другое", catalog?.name ?: line.name.trim(), line.amount.trim())
            }
        }.filter { it.second.isNotBlank() }

        return raw.groupBy { it.first to it.second.lowercase() }
            .map { (key, rows) ->
                val name = rows.first().second
                val amounts = rows.map { it.third }.filter { it.isNotBlank() }
                ShoppingItem(key.first, name, humanAmount(name, amounts))
            }
            .sortedWith(compareBy<ShoppingItem> { it.section }.thenBy { it.name })
    }

    private fun humanAmount(name: String, amounts: List<String>): String {
        val n = name.lowercase()
        val fallback = amounts.distinct().joinToString(" + ").ifBlank { "1 упаковка" }
        return when {
            "масл" in n -> "1 пачка"
            "сахар" in n -> "1 пакет, около 1 кг"
            "мук" in n -> "1 пакет, около 1 кг"
            "молок" in n -> "1 упаковка"
            "сливк" in n -> "1 упаковка"
            "сметан" in n -> "1 упаковка"
            "творог" in n -> "1 пачка"
            "яйц" in n -> "1 десяток"
            "сыр" in n -> "1 упаковка"
            "макарон" in n || "рис" in n || "греч" in n -> "1 пачка"
            "соль" in n || "специ" in n || "перец" in n -> "проверьте дома"
            else -> fallback
        }
    }

    fun asMessage(items: List<ShoppingItem>, title: String = "Список в магазин"): String {
        if (items.isEmpty()) return title + "\n\nСписок пока пуст."
        val body = items.groupBy { it.section }.entries.joinToString("\n\n") { (section, rows) ->
            buildString {
                append(section); append("\n")
                rows.forEach { row -> append("• "); append(row.name); append(" — "); append(row.amount); append("\n") }
            }.trimEnd()
        }
        return title + "\n\n" + body
    }
}
