package ru.vkusdetstva

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.vkusdetstva.util.importers.ClipboardRecipeParser
import ru.vkusdetstva.util.importers.RecipeImportParser

class RecipeImportTest {

    @Test fun `extracts plain json object`() {
        val json = RecipeImportParser.extractJson("prefix {\"title\":\"Борщ\"} suffix")
        assertEquals("{\"title\":\"Борщ\"}", json)
    }

    @Test fun `strips markdown fences around json`() {
        val answer = "Вот рецепт:\n```json\n{\"title\":\"Оладьи\",\"steps\":[\"смешать\"]}\n```\nПриятного!"
        val draft = RecipeImportParser.parseModelResponse(answer)
        assertEquals("Оладьи", draft.title)
        assertEquals(listOf("смешать"), draft.steps)
    }

    @Test fun `parses full draft with arrays`() {
        val answer = "{\"title\":\"Пирог с яблоками\",\"story\":\"Бабушкин рецепт\",\"ingredients\":" +
            "[\"мука — 3 стакана\",\"яблоки — 4 шт\"],\"steps\":[\"замесить тесто\",\"испечь\"],\"notes\":\"подавать тёплым\"}"
        val draft = RecipeImportParser.parseModelResponse(answer)
        assertEquals("Пирог с яблоками", draft.title)
        assertEquals("Бабушкин рецепт", draft.story)
        assertEquals(listOf("мука — 3 стакана", "яблоки — 4 шт"), draft.ingredients)
        assertEquals(listOf("замесить тесто", "испечь"), draft.steps)
        assertEquals("подавать тёплым", draft.notes)
    }

    @Test fun `steps given as one string are split by lines`() {
        val answer = "{\"title\":\"Кисель\",\"steps\":\"варить ягоды\\nдобавить сахар\\nостудить\"}"
        val draft = RecipeImportParser.parseModelResponse(answer)
        assertEquals(listOf("варить ягоды", "добавить сахар", "остудить"), draft.steps)
    }

    @Test fun `no recipe error raises readable message`() {
        try {
            RecipeImportParser.parseModelResponse("{\"error\":\"no_recipe\"}")
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("не видно рецепта"))
        }
    }

    @Test fun `missing title raises readable message`() {
        try {
            RecipeImportParser.parseModelResponse("{\"ingredients\":[\"соль\"]}")
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("название"))
        }
    }

    @Test fun `answer without json raises readable message`() {
        try {
            RecipeImportParser.parseModelResponse("Извините, я не нашёл рецепт на фото.")
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("нет данных"))
        }
    }

    @Test fun `clipboard splits title ingredients and steps`() {
        val text = "Оладьи бабушки\n\nИнгредиенты:\nкефир — стакан\nмука — два стакана\n\nСпособ приготовления:\nсмешать\nжарить на масле"
        val draft = ClipboardRecipeParser.parse(text)!!
        assertEquals("Оладьи бабушки", draft.title)
        assertEquals(listOf("кефир — стакан", "мука — два стакана"), draft.ingredients)
        assertEquals(listOf("смешать", "жарить на масле"), draft.steps)
    }

    @Test fun `clipboard without markers puts everything into steps`() {
        val draft = ClipboardRecipeParser.parse("Шарлотка\nяблоки нарезать\nзамесить тесто\nвыпекать 40 минут")!!
        assertEquals("Шарлотка", draft.title)
        assertTrue(draft.ingredients.isEmpty())
        assertEquals(listOf("яблоки нарезать", "замесить тесто", "выпекать 40 минут"), draft.steps)
    }

    @Test fun `clipboard blank text returns null`() {
        assertNull(ClipboardRecipeParser.parse("   \n  "))
    }
}
