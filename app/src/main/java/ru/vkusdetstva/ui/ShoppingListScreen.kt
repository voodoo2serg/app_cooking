package ru.vkusdetstva.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.Recipe
import java.util.Locale
import kotlin.math.ceil

private data class ShoppingRow(val name: String, val category: String, val amounts: List<String>)

private fun packageHint(name: String, amounts: List<String>): String {
    val n = IngredientCatalog.normalize(name)
    val joined = amounts.joinToString(" ")
    fun qty(unit: String) = Regex("""(\d+(?:[.,]\d+)?)\s*$unit""", RegexOption.IGNORE_CASE)
        .find(joined)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
    val grams = qty("г") ?: qty("гр")
    val kilos = qty("кг")
    val ml = qty("мл")
    val liters = qty("л")
    val count = Regex("""(\d+)\s*(?:шт|яйц)""", RegexOption.IGNORE_CASE)
        .find(joined)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return when {
        n.contains("сливочное масло") -> {
            val total = grams ?: kilos?.times(1000)
            if (total != null) "${ceil(total / 180.0).toInt().coerceAtLeast(1)} пач." else "1 пачка"
        }
        n == "сахар" || n == "мука" || n.contains("греч") || n == "рис" -> {
            val total = kilos ?: grams?.div(1000)
            "${if (total != null) ceil(total).toInt().coerceAtLeast(1) else 1} пакет по 1 кг"
        }
        n == "яйца" -> "${ceil((count ?: 10) / 10.0).toInt().coerceAtLeast(1)} уп. по 10 шт."
        n.contains("молоко") || n.contains("кефир") -> {
            val total = liters ?: ml?.div(1000)
            "${if (total != null) ceil(total).toInt().coerceAtLeast(1) else 1} упаковка по 1 л"
        }
        n.contains("сметан") || n.contains("творог") || n.contains("сыр") -> "1 упаковка"
        n.contains("растительное масло") -> "1 бутылка"
        else -> amounts.filter { it.isNotBlank() }.distinct().joinToString(" + ").ifBlank { "1 упаковка" }
    }
}

private fun shoppingRows(recipes: List<Recipe>): List<ShoppingRow> {
    val grouped = linkedMapOf<String, MutableList<IngredientLine>>()
    recipes.forEach { recipe -> parseIngredientLines(recipe.ingredients).forEach { line ->
        grouped.getOrPut(IngredientCatalog.normalize(line.name)) { mutableListOf() }.add(line)
    } }
    return grouped.values.map { values ->
        val raw = values.first().name
        val item = IngredientCatalog.find(raw)
        ShoppingRow(item?.name ?: raw, item?.category ?: "Другое", values.map { it.amount })
    }.sortedWith(compareBy<ShoppingRow> { it.category }.thenBy { it.name.lowercase(Locale.ROOT) })
}

@Composable
fun ShoppingListScreen(recipes: List<Recipe>, back: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(recipes.take(1).map { it.id }.toSet()) }
    val chosen = recipes.filter { it.id in selected }
    val rows = remember(chosen) { shoppingRows(chosen) }
    Page("Список покупок", back) {
        Text("Выберите, что готовим. Список сгруппируется по отделам и переведёт количества в понятные магазинные упаковки там, где это возможно.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Section("Что готовим")
        recipes.forEach { recipe ->
            Row(Modifier.fillMaxWidth()) {
                Checkbox(recipe.id in selected, { checked ->
                    selected = if (checked) selected + recipe.id else selected - recipe.id
                })
                Text(recipe.title, Modifier.padding(top = 12.dp))
            }
        }
        if (rows.isNotEmpty()) {
            Section("В магазин")
            rows.groupBy { it.category }.forEach { (category, items) ->
                Text(category, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                items.forEach { Text("• ${it.name} — ${packageHint(it.name, it.amounts)}") }
                Spacer(Modifier.height(8.dp))
            }
            val shareText = buildString {
                appendLine("Список покупок")
                appendLine(chosen.joinToString(prefix = "Готовим: ", separator = ", ") { it.title })
                appendLine()
                rows.groupBy { it.category }.forEach { (category, items) ->
                    appendLine(category)
                    items.forEach { appendLine("• ${it.name} — ${packageHint(it.name, it.amounts)}") }
                    appendLine()
                }
            }
            Action("Отправить список в мессенджер", {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText)
                }, "Отправить список покупок"))
            })
        } else Text(if (recipes.isEmpty()) "Сначала добавьте семейные рецепты." else "Выберите хотя бы одно блюдо.")
        Spacer(Modifier.height(24.dp))
    }
}
