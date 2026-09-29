package ru.vkusdetstva.util

import kotlin.math.ceil
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.ui.IngredientCatalog
import ru.vkusdetstva.ui.parseIngredientLines

data class ShoppingItem(val section: String, val name: String, val amount: String)

object ShoppingListBuilder {
    fun build(recipes: List<Recipe>): List<ShoppingItem> {
        val raw = recipes.flatMap { recipe ->
            parseIngredientLines(recipe.ingredients).map { line ->
                val catalog = IngredientCatalog.find(line.name)
                Triple(catalog?.category ?: "Другое", catalog?.name ?: line.name.trim(), line.amount.trim())
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
        val pack = when {
            "сахар" in n || "мук" in n -> Triple(1000.0, "г", "пакет 1 кг")
            "яйц" in n -> Triple(10.0, "шт", "десяток")
            "масл" in n && "сливоч" in n -> Triple(180.0, "г", "пачка 180 г")
            "молок" in n -> Triple(1000.0, "мл", "упаковка 1 л")
            else -> null
        }
        if (pack != null && amounts.isNotEmpty()) {
            val parsed = amounts.map { Regex("^(\\d+(?:[.,]\\d+)?)\\s*(кг|г|л|мл|шт)", RegexOption.IGNORE_CASE)
                .find(it.trim())?.let { match ->
                    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let null
                    val unit = match.groupValues[2].lowercase()
                    when (unit) {
                        pack.second -> value
                        "кг" -> if (pack.second == "г") value * 1000 else null
                        "л" -> if (pack.second == "мл") value * 1000 else null
                        else -> null
                    }
                }
            }
            if (parsed.all { it != null }) {
                val count = ceil(parsed.filterNotNull().sum() / pack.first).toInt().coerceAtLeast(1)
                return "$count × ${pack.third} (нужно: $requested)"
            }
        }
        // Unclear units stay visible; a guessed pack count could leave ingredients missing.
        return if (packageName != null) "упаковками по потребности (нужно: $requested)" else requested
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
