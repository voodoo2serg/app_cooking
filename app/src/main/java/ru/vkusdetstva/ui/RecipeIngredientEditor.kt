package ru.vkusdetstva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class IngredientLine(val name: String, val amount: String = "")

fun parseIngredientLines(text: String): List<IngredientLine> = text.lines().filter { it.isNotBlank() }.map { line ->
    val parts = line.split('—', limit = 2)
    IngredientLine(parts[0].trim(), parts.getOrNull(1)?.trim().orEmpty())
}

fun formatIngredientLines(lines: List<IngredientLine>): String = lines.joinToString("\n") { line ->
    if (line.amount.isBlank()) line.name.trim() else "${line.name.trim()} — ${line.amount.trim()}"
}

@Composable
fun RecipeIngredientEditor(lines: SnapshotStateList<IngredientLine>) {
    var category by remember { mutableStateOf(IngredientCatalog.groups.keys.first()) }
    var filter by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }

    Text("Выберите продукты по разделам. Количество укажите рядом с каждым продуктом.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextBox(filter, { filter = it }, "Найти ингредиент")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(IngredientCatalog.groups.keys.toList()) { name ->
            FilterChip(category == name && filter.isBlank(), onClick = { category = name; filter = "" },
                label = { Text(name) })
        }
    }
    val visible = if (filter.isBlank()) IngredientCatalog.groups[category].orEmpty() else
        IngredientCatalog.all.filter { it.name.contains(filter, true) }
    visible.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { item ->
                val added = lines.any { it.name.equals(item.name, true) }
                FilterChip(added, onClick = {
                    if (!added) lines.add(IngredientLine(item.name))
                }, label = { Text(if (added) "✓ ${item.name}" else "+ ${item.name}", maxLines = 2) },
                    modifier = Modifier.weight(1f))
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
            if (name.isNotBlank() && lines.none { it.name.equals(name, true) }) lines.add(IngredientLine(name))
            custom = ""
        }, enabled = custom.isNotBlank()) { Text("Добавить") }
    }
    Section("В рецепте · ${lines.size}")
    if (lines.isEmpty()) Text("Выберите ингредиенты из разделов выше.")
    lines.toList().forEachIndexed { index, item ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(item.amount, { amount -> lines[index] = item.copy(amount = amount) },
                        label = { Text("Количество, например 200 г") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { lines.removeAt(index) }) { Text("✕") }
                }
            }
        }
    }
}
