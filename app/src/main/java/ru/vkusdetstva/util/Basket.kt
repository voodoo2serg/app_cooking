package ru.vkusdetstva.util

import ru.vkusdetstva.data.BasketItem
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.ui.IngredientCatalog
import ru.vkusdetstva.ui.parseIngredientLines

/**
 * Корзина покупок: собирает ингредиенты выбранных рецептов в один список,
 * объединяет повторы (молоко из двух рецептов — одна позиция) и не кладёт
 * то, что уже лежит в корзине. Чистые функции — покрываются unit-тестами.
 */
object Basket {

    /** Позиции корзины по ингредиентам выбранных рецептов. Дубликаты пропускаются. */
    fun itemsForRecipes(recipes: List<Recipe>, existing: List<BasketItem> = emptyList()): List<BasketItem> {
        val already = existing.map { it.text.trim().lowercase() }.toSet()
        val out = mutableListOf<BasketItem>()
        val seen = mutableSetOf<String>()
        recipes.forEach { recipe ->
            parseIngredientLines(recipe.ingredients).forEach { line ->
                val name = line.name.trim()
                if (name.isBlank()) return@forEach
                val key = name.lowercase()
                if (key in already || key in seen) return@forEach
                seen += key
                val catalog = IngredientCatalog.find(name)
                out += BasketItem(
                    text = catalog?.name ?: name,
                    amount = line.amount.trim(),
                    section = catalog?.category ?: "Другое",
                    source = recipe.title.trim()
                )
            }
        }
        return out
    }

    /** Текст для отправки родне: только ещё не отмеченные позиции, по разделам. */
    fun asMessage(items: List<BasketItem>, title: String = "Корзина покупок"): String {
        val pending = items.filterNot { it.checked }
        if (pending.isEmpty()) return "$title\n\nСписок пока пуст."
        val body = pending.groupBy { it.section }.entries.joinToString("\n\n") { (section, rows) ->
            buildString {
                append(section); append("\n")
                rows.forEach { row ->
                    append("• "); append(row.text)
                    if (row.amount.isNotBlank()) { append(" — "); append(row.amount) }
                    if (row.source.isNotBlank()) { append("  ("); append(row.source); append(")") }
                    append("\n")
                }
            }.trimEnd()
        }
        return "$title\n\n$body\n\nИз семейного архива «Вкус детства»"
    }
}
