package ru.vkusdetstva.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.util.book.BookComposer
import ru.vkusdetstva.util.book.BookOptions
import ru.vkusdetstva.util.book.BookPage
import ru.vkusdetstva.util.book.Edition

class BookComposerTest {

    private val options = BookOptions()

    private fun recipe(
        id: Long, title: String, personId: Long? = null, timesCooked: Int = 0,
        taste: Int = 0, createdAt: Long = 0L, photos: List<String> = emptyList()
    ) = Recipe(id = id, title = title, personId = personId, timesCooked = timesCooked,
        taste = taste, createdAt = createdAt, photos = photos)

    @Test
    fun `popularity edition groups by timesCooked and sorts inside`() {
        val recipes = listOf(
            recipe(1, "А", timesCooked = 7), recipe(2, "Б", timesCooked = 3),
            recipe(3, "В", timesCooked = 3, taste = 4), recipe(4, "Г", timesCooked = 1),
            recipe(5, "Д", timesCooked = 0), recipe(6, "Е", timesCooked = 0)
        )
        val model = BookComposer.compose(emptyList(), recipes, emptyList(), emptyList(), options)
        val openers = model.pages.filterIsInstance<BookPage.ChapterOpener>()
        assertEquals(3, openers.size)
        assertEquals("Легенды стола", openers[0].title)
        assertEquals("Частые гости", openers[1].title)
        assertEquals("Первые пробы", openers[2].title)
        val order = model.pages.filterIsInstance<BookPage.RecipeSpread>().map { it.recipe.id }
        assertEquals(listOf(1L, 3L, 2L, 4L, 5L, 6L), order)
    }

    @Test
    fun `people edition puts bigger chapters first and orphans last`() {
        val people = listOf(Person(id = 1, name = "Анна"), Person(id = 2, name = "Борис", relation = "дедушка"))
        val recipes = listOf(
            recipe(1, "Пирог", personId = 1), recipe(2, "Суп", personId = 1),
            recipe(3, "Каша", personId = 2), recipe(4, "Компот", personId = 99)
        )
        val model = BookComposer.compose(people, recipes, emptyList(), emptyList(),
            options.copy(edition = Edition.BY_PEOPLE))
        val titles = model.pages.filterIsInstance<BookPage.ChapterOpener>().map { it.title }
        assertEquals(listOf("Анна", "дедушка Борис", "Из общей тетради"), titles)
    }

    @Test
    fun `people edition names author chapter when authorName is given`() {
        val recipes = listOf(recipe(1, "Каша", personId = null))
        val model = BookComposer.compose(emptyList(), recipes, emptyList(), emptyList(),
            options.copy(edition = Edition.BY_PEOPLE), authorName = "Сергей")
        val openers = model.pages.filterIsInstance<BookPage.ChapterOpener>()
        assertEquals(1, openers.size)
        assertEquals("Рецепты автора", openers[0].title)
        assertEquals("Сергей", openers[0].subtitle)
    }

    @Test
    fun `chronology edition makes year chapters ascending`() {
        val recipes = listOf(
            recipe(1, "А", createdAt = 1_700_000_000_000),
            recipe(2, "Б", createdAt = 1_600_000_000_000)
        )
        val model = BookComposer.compose(emptyList(), recipes, emptyList(), emptyList(),
            options.copy(edition = Edition.CHRONOLOGY))
        val titles = model.pages.filterIsInstance<BookPage.ChapterOpener>().map { it.title }
        assertEquals(2, titles.size)
        assertTrue(titles[0].substringBefore(" год").toInt() < titles[1].substringBefore(" год").toInt())
    }

    @Test
    fun `toc references chapters and spreads`() {
        val recipes = listOf(recipe(1, "Борщ", timesCooked = 6))
        val model = BookComposer.compose(emptyList(), recipes, emptyList(), emptyList(), options)
        val toc = model.pages.filterIsInstance<BookPage.Toc>().single()
        assertTrue(toc.entries.any { it.refKey == "ch:0" && it.level == 0 })
        assertTrue(toc.entries.any { it.refKey == "rec:1" && it.level == 1 })
        assertTrue(toc.entries.any { it.refKey == "lined" })
    }

    @Test
    fun `cover photo prefers most cooked recipe`() {
        val recipes = listOf(
            recipe(1, "А", photos = listOf("/a.jpg"), timesCooked = 1),
            recipe(2, "Б", photos = listOf("/b.jpg"), timesCooked = 9)
        )
        val model = BookComposer.compose(emptyList(), recipes, emptyList(), emptyList(), options)
        val cover = model.pages.first() as BookPage.Cover
        assertEquals("/b.jpg", cover.photoPath)
    }

    @Test
    fun `empty sections are skipped by options`() {
        val recipes = listOf(recipe(1, "А"))
        val model = BookComposer.compose(
            emptyList(), recipes, emptyList(), emptyList(),
            options.copy(includePeople = true, includeMoments = false, includeLined = false)
        )
        val keys = model.pages.filterIsInstance<BookPage.Toc>().single().entries.map { it.refKey }
        assertTrue("people" !in keys)
        assertTrue("moments" !in keys)
        assertTrue("lined" !in keys)
        val pages = model.pages.map { it::class }
        assertTrue(BookPage.Lined::class !in pages)
    }

    @Test
    fun `people without recipes are excluded when option is off`() {
        val people = listOf(Person(id = 1, name = "Анна"), Person(id = 2, name = "Пётр"))
        val recipes = listOf(recipe(1, "Пирог", personId = 1))
        val model = BookComposer.compose(people, recipes, emptyList(), emptyList(),
            options.copy(includePeople = true, includePeopleWithoutRecipes = false))
        val peoplePages = model.pages.filterIsInstance<BookPage.PeoplePage>()
        val names = peoplePages.flatMap { it.persons }.map { it.name }
        assertTrue("Анна" in names)
        assertTrue("Пётр" !in names)
    }

    @Test
    fun `quote frame fills blank story with signature`() {
        val person = Person(id = 1, name = "Настя", relation = "внучка")
        val spread = BookComposer.compose(
            listOf(person), listOf(recipe(1, "Пирожки", personId = 1)), emptyList(), emptyList(), options
        ).pages.filterIsInstance<BookPage.RecipeSpread>().single()
        assertTrue(spread.recipe.story.contains("«Пирожки»"))
        assertTrue(spread.recipe.story.contains("внучка Настя"))
    }

    @Test
    fun `own story wins over quote frame`() {
        val recipes = listOf(recipe(1, "Пирожки").copy(story = "Своя история"))
        val spread = BookComposer.compose(emptyList(), recipes, emptyList(), emptyList(), options)
            .pages.filterIsInstance<BookPage.RecipeSpread>().single()
        assertEquals("Своя история", spread.recipe.story)
    }

    @Test
    fun `quotes can be disabled`() {
        val spread = BookComposer.compose(emptyList(), listOf(recipe(1, "Каша")), emptyList(), emptyList(),
            options.copy(showQuotes = false)).pages.filterIsInstance<BookPage.RecipeSpread>().single()
        assertEquals("", spread.recipe.story)
    }
}
