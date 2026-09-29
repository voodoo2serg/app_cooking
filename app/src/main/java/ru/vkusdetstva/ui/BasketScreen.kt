package ru.vkusdetstva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.BasketItem
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.util.Basket
import ru.vkusdetstva.util.FamilyShare

/**
 * Корзина покупок: кладём один или несколько рецептов (и всё, что отмечено
 * тегом события), внутри — общий список с чек-боксами: молоко или яйца,
 * которые уже есть дома, просто отмечаются галочкой.
 */
@Composable
fun BasketScreen(items: List<BasketItem>, recipes: List<Recipe>, back: () -> Unit,
                 toggle: (BasketItem) -> Unit, remove: (BasketItem) -> Unit,
                 addRecipes: (List<Recipe>) -> Unit, addItem: (String) -> Unit,
                 clearChecked: () -> Unit, clearAll: () -> Unit) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<Long>() }
    var manual by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val pending = items.filterNot { it.checked }
    val done = items.filter { it.checked }
    Page("Корзина", back) {
        Text("Отметьте блюда — их ингредиенты соберутся в один список. Лишнее (молоко, яйца…) отмечайте галочкой: оно у вас уже есть.",
            style = MaterialTheme.typography.bodySmall)

        Section("Положить блюда в корзину")
        recipes.forEach { recipe ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = recipe.id in selected, onCheckedChange = { checked ->
                    if (checked) selected.add(recipe.id) else selected.remove(recipe.id)
                })
                Text(recipe.title, Modifier.weight(1f))
            }
        }
        if (recipes.isEmpty()) Text("Сначала запишите рецепты — и корзина соберётся сама.")
        Action(if (selected.isEmpty()) "Выберите блюда" else "Положить в корзину · ${selected.size}", {
            addRecipes(recipes.filter { it.id in selected })
            selected.clear()
        }, selected.isNotEmpty())

        Section("Купить · ${pending.size}")
        if (items.isEmpty()) Text("Корзина пуста. Положите в неё блюда из списка выше.")
        pending.forEach { item ->
            BasketRow(item, toggle, remove)
        }

        if (done.isNotEmpty()) {
            Section("Уже есть дома · ${done.size}")
            done.forEach { item -> BasketRow(item, toggle, remove) }
        }

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(manual, { manual = it }, label = { Text("Другое, чего не хватает") },
                singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { addItem(manual); manual = "" }, enabled = manual.isNotBlank()) {
                Text("Добавить")
            }
        }

        if (items.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Action("Отправить список текстом", {
                FamilyShare.shareText(context, "Корзина покупок", Basket.asMessage(items))
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = clearChecked, enabled = done.isNotEmpty()) {
                    Text("Убрать отмеченные (${done.size})")
                }
                TextButton(onClick = { confirmClear = true }) { Text("Очистить корзину") }
            }
            if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
                title = { Text("Очистить корзину?") },
                text = { Text("Все позиции, включая отмеченные, исчезнут. Сами рецепты останутся.") },
                confirmButton = { TextButton(onClick = { confirmClear = false; clearAll() }) { Text("Очистить") } },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BasketRow(item: BasketItem, toggle: (BasketItem) -> Unit, remove: (BasketItem) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = { toggle(item) })
        Column(Modifier.weight(1f)) {
            Text(item.text + if (item.amount.isNotBlank()) " — ${item.amount}" else "",
                textDecoration = if (item.checked) TextDecoration.LineThrough else null)
            if (item.source.isNotBlank()) Text(item.source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { remove(item) }) { Text("✕") }
    }
}
