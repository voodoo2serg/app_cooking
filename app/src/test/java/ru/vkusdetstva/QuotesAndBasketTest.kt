package ru.vkusdetstva

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vkusdetstva.data.BasketItem
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.util.Basket
import ru.vkusdetstva.util.MemoryQuotes

class QuotesAndBasketTest {

    // ---------- Цитаты-рамки ----------

    @Test
    fun `frame replaces dish placeholder and keeps signature`() {
        val text = MemoryQuotes.frame("Пирожки", "Любимая внучка Настя", seed = 3)
        assertTrue(text.contains("«Пирожки»"))
        assertTrue(text.contains("Любимая внучка Настя"))
        assertFalse(text.contains("{dish}"))
    }

    @Test
    fun `frame without signature stays a single quote`() {
        val text = MemoryQuotes.frame("Борщ", "", seed = 0)
        // Подпись всегда идёт отдельной строкой через «— »; без подписи её быть не должно.
        assertFalse(text.contains("\n— "))
        assertFalse(text.contains("{dish}"))
    }

    @Test
    fun `same seed gives same frame so the book does not flicker`() {
        val a = MemoryQuotes.frame("Каша", "Настя", seed = 11)
        val b = MemoryQuotes.frame("Каша", "Настя", seed = 11)
        assertEquals(a, b)
    }

    @Test
    fun `blank dish falls back to a neutral name`() {
        val text = MemoryQuotes.frame("  ", "Настя", seed = 0)
        assertTrue(text.contains("Это блюдо"))
    }

    @Test
    fun `suggestions are distinct and do not contain placeholders`() {
        val list = MemoryQuotes.suggestions("Пельмени", count = 4, seed = 2)
        assertEquals(4, list.size)
        assertEquals(4, list.toSet().size)
        list.forEach { assertFalse(it.contains("{dish}")) }
    }

    @Test
    fun `templates cover thirty frames`() {
        assertTrue(MemoryQuotes.templates.size >= 30)
    }

    // ---------- Корзина ----------

    private fun recipe(id: Long, title: String, ingredients: String) =
        Recipe(id = id, title = title, ingredients = ingredients)

    @Test
    fun `basket merges duplicate ingredients from different recipes`() {
        val items = Basket.itemsForRecipes(listOf(
            recipe(1, "Оладьи", "Молоко — 1 стакан\nЯйца — 2 шт"),
            recipe(2, "Блины", "молоко — 0,5 л\nМука — 300 г")
        ))
        val names = items.map { it.text.lowercase() }
        assertEquals(1, names.count { it.contains("молок") })
        assertEquals(3, items.size)
    }

    @Test
    fun `basket does not duplicate what is already inside`() {
        val existing = listOf(BasketItem(text = "Молоко", amount = "1 л"))
        val items = Basket.itemsForRecipes(
            listOf(recipe(1, "Оладьи", "Молоко — 1 стакан\nЯйца — 2 шт")), existing)
        assertEquals(1, items.size)
        assertEquals("Яйца", items.first().text)
    }

    @Test
    fun `basket keeps amounts and source recipe`() {
        val items = Basket.itemsForRecipes(listOf(recipe(5, "Оладьи", "Яйца — 2 шт")))
        val item = items.single()
        assertEquals("2 шт", item.amount)
        assertEquals("Оладьи", item.source)
    }

    @Test
    fun `basket message skips checked items and signs the archive`() {
        val message = Basket.asMessage(listOf(
            BasketItem(text = "Молоко", amount = "1 л", section = "Молочное и яйца", checked = true),
            BasketItem(text = "Яйца", amount = "десяток", section = "Молочное и яйца")
        ))
        assertFalse(message.contains("Молоко — 1 л"))
        assertTrue(message.contains("Яйца — десяток"))
        assertTrue(message.contains("«Вкус детства»"))
    }

    @Test
    fun `basket message for empty list is friendly`() {
        assertTrue(Basket.asMessage(emptyList()).contains("пуст"))
    }
}
