package ru.vkusdetstva.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Recipe

/**
 * Событие — это тег, а не отдельная сущность: «Новый год», «Пасха», «дача».
 * Тег прикрепляется к блюду или фотоистории, и всё, что им отмечено,
 * собирается вместе. Раздел образуется сам — когда событий станет минимум два.
 */
@Composable
fun EventsScreen(recipes: List<Recipe>, moments: List<FamilyMoment>, back: () -> Unit,
                 openRecipe: (Long) -> Unit, openMoment: (Long) -> Unit,
                 toBasket: (String) -> Unit) {
    val tags = (recipes.map { it.eventTag } + moments.map { it.eventTag })
        .filter { it.isNotBlank() }.distinct().sorted()
    Page("События и семейные столы", back) {
        Text("Событие — это тег, а не отдельный раздел: отметьте блюдо или фотоисторию «Новый год» или «Пасха» — и они соберутся вместе. Одно и то же блюдо может быть и праздничным, и будничным.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        if (tags.size < 2) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Раздел соберётся автоматически", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Он появится здесь и в книге, когда у вас образуется минимум два события-тега. Пока отметьте тегом блюда и фотоистории в их формах — поле «Событие-тег».",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (tags.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Тегов пока нет. Откройте рецепт или фотоисторию и впишите событие — например, «Новый год».")
        }
        tags.forEach { tag ->
            val dishes = recipes.filter { it.eventTag == tag }
            val photos = moments.filter { it.eventTag == tag }
            Section("$tag · ${dishes.size} ${pluralDishes(dishes.size)}, ${photos.size} ${pluralMoments(photos.size)}")
            dishes.forEach { recipe ->
                ListTile(recipe.title, recipe.story.take(90)) { openRecipe(recipe.id) }
            }
            photos.forEach { moment ->
                ListTile(moment.title, moment.story.take(90)) { openMoment(moment.id) }
                PhotoCarousel(moment.photos, "Стол: $tag")
            }
            if (dishes.isNotEmpty()) {
                TextButton(onClick = { toBasket(tag) }) { Text("Всё в корзину · ингредиенты «$tag»") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Поле «Событие-тег» с подсказками уже использованных тегов. */
@Composable
fun EventTagField(tag: String, onChange: (String) -> Unit, knownTags: List<String>) {
    TextBox(tag, onChange, "Событие-тег: Новый год, Пасха, дача…")
    if (knownTags.isNotEmpty()) {
        Text("Уже использованные теги", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            knownTags.forEach { known ->
                FilterChip(selected = tag == known, onClick = { onChange(if (tag == known) "" else known) },
                    label = { Text(known) })
            }
        }
    }
}

private fun pluralDishes(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "блюдо"
    n % 10 in 2..4 && (n % 100 < 12 || n % 100 > 14) -> "блюда"
    else -> "блюд"
}

private fun pluralMoments(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "фотоистория"
    n % 10 in 2..4 && (n % 100 < 12 || n % 100 > 14) -> "фотоистории"
    else -> "фотоисторий"
}
