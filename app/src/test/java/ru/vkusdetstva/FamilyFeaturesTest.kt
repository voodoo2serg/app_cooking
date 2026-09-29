package ru.vkusdetstva

import org.junit.Assert.*
import org.junit.Test
import ru.vkusdetstva.data.*
import ru.vkusdetstva.util.BookReadinessCalculator
import ru.vkusdetstva.util.ShoppingListBuilder

class FamilyFeaturesTest {
    @Test fun readinessGrowsWithContent() {
        val empty = BookReadinessCalculator.calculate(emptyList(), emptyList(), emptyList(), null)
        val ready = BookReadinessCalculator.calculate((1..5).map { i ->
            Recipe(id = i.toLong(), title = "Блюдо $i", story = "Семейный ужин", photos = listOf("photo"))
        }, emptyList(), emptyList(), AuthorProfile(name = "Автор"))
        assertEquals(0, empty.percent)
        assertEquals(100, ready.percent)
    }

    @Test fun shoppingListDoesNotUnderstateRepeatedQuantities() {
        val items = ShoppingListBuilder.build(listOf(
            Recipe(title = "Пирог", ingredients = "Сахар — 700 г\nЯйца — 6 шт"),
            Recipe(title = "Печенье", ingredients = "Сахар — 700 г\nЯйца — 8 шт")
        ))
        val sugar = items.single { it.name == "Сахар" }
        assertTrue(sugar.amount.contains("700 г + 700 г"))
        assertTrue(items.single { it.name == "Яйца" }.amount.contains("6 шт + 8 шт"))
    }
}
