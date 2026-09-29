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
        val packageName = when {
            "масл" in n || "творог" in n -> "пачка"
            "сахар" in n || "мук" in n -> "пакет 1 кг"
            "молок" in n || "сливк" in n || "сметан" in n || "сыр" in n -> "упаковка"
            "яйц" in n -> "десяток"
            "макарон" in n || "рис" in n || "греч" in n -> "пачка"
            else -> null
        }
        val requested = amounts.joinToString(" + ").ifBlank { "количество не указано" }
        // Never claim that one pack is sufficient when several recipes may need more.
        return if (packageName != null) "$packageName (по рецептам: $requested)" else requested
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
