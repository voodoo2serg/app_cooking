package ru.vkusdetstva.util.book

import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import ru.vkusdetstva.util.MemoryQuotes
import java.util.Calendar

data class Chapter(val title: String, val subtitle: String, val recipes: List<Recipe>)

object BookComposer {

    fun compose(
        people: List<Person>, recipes: List<Recipe>, versions: List<RecipeVersion>,
        moments: List<FamilyMoment>, options: BookOptions, authorName: String? = null
    ): BookModel {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val chapters = buildChapters(people, recipes, options.edition, authorName)
        val counts = recipes.filter { it.personId != null }
            .groupingBy { it.personId!! }.eachCount()

        val pages = mutableListOf<BookPage>()
        val toc = mutableListOf<TocEntry>()

        pages += BookPage.Cover(options.coverTemplate, options.coverBackground, pickCoverPhoto(recipes, moments, people))
        pages += BookPage.Flyleaf

        chapters.forEachIndexed { index, chapter ->
            toc += TocEntry("%02d · %s".format(index + 1, chapter.title), 0, "ch:$index")
            chapter.recipes.forEach { recipe ->
                toc += TocEntry(recipe.title, 1, "rec:${recipe.id}")
            }
            pages += BookPage.ChapterOpener(index + 1, chapter.title, chapter.subtitle)
            chapter.recipes.forEach { recipe ->
                pages += BookPage.RecipeSpread(
                    withQuote(recipe, people.find { it.id == recipe.personId }, authorName, options),
                    people.find { it.id == recipe.personId },
                    versions.filter { it.recipeId == recipe.id }.sortedBy { it.createdAt },
                    chapter.title
                )
            }
        }

        if (options.includePeople && people.isNotEmpty()) {
            // Про кого ещё нет рецептов — пускаем в книгу только по выбору автора.
            val shown = if (options.includePeopleWithoutRecipes) people
                else people.filter { (counts[it.id] ?: 0) > 0 }
            if (shown.isNotEmpty()) {
                toc += TocEntry("Люди нашей книги", 0, "people")
                val sorted = shown.sortedWith(
                    compareByDescending<Person> { counts[it.id] ?: 0 }.thenBy { it.name }
                )
                sorted.chunked(2).forEach { pages += BookPage.PeoplePage(it, counts) }
            }
        }

        if (options.includeMoments && moments.isNotEmpty()) {
            toc += TocEntry("Как мы это едим", 0, "moments")
            moments.sortedByDescending { it.createdAt }.forEach { pages += BookPage.MomentPage(it) }
        }

        if (options.includeLined) {
            toc += TocEntry("Страницы для новых рецептов", 0, "lined")
            repeat(maxOf(4, options.linedPageCount.coerceIn(4, 16))) { pages += BookPage.Lined }
        }

        pages += BookPage.BackCover
        pages.add(2, BookPage.Toc(toc.toList()))

        val meta = BookMeta(recipes.size, people.size, moments.size, chapters.size, year)
        return BookModel(options, pages, meta)
    }

    fun buildChapters(people: List<Person>, recipes: List<Recipe>, edition: Edition,
                      authorName: String? = null): List<Chapter> {
        if (recipes.isEmpty()) return emptyList()
        val byPopularity = compareByDescending<Recipe> { it.timesCooked }
            .thenByDescending { it.taste }
            .thenBy { it.createdAt }
        return when (edition) {
            Edition.BY_POPULARITY -> listOfNotNull(
                chapterIf("Легенды стола", "готовили чаще всего", recipes.filter { it.timesCooked >= 5 }.sortedWith(byPopularity)),
                chapterIf("Частые гости", "на столе по праздникам и без повода", recipes.filter { it.timesCooked in 2..4 }.sortedWith(byPopularity)),
                chapterIf("Первые пробы", "совсем новые истории", recipes.filter { it.timesCooked <= 1 }.sortedWith(byPopularity))
            )
            Edition.BY_PEOPLE -> {
                val own = people.mapNotNull { person ->
                    val list = recipes.filter { it.personId == person.id }.sortedBy { it.createdAt }
                    if (list.isEmpty()) null else Chapter(
                        title = if (person.relation.isNotBlank()) "${person.relation} ${person.name}" else person.name,
                        subtitle = "${list.size} ${pluralRecipes(list.size)}",
                        recipes = list
                    )
                }.sortedWith(compareByDescending<Chapter> { it.recipes.size }.thenBy { it.title })
                val orphan = recipes.filter { recipe ->
                    recipe.personId == null || people.none { it.id == recipe.personId }
                }.sortedBy { it.createdAt }
                if (orphan.isNotEmpty()) own + Chapter(
                    title = if (!authorName.isNullOrBlank()) "Рецепты автора" else "Из общей тетради",
                    subtitle = if (!authorName.isNullOrBlank()) authorName else "рецепты без подписи",
                    recipes = orphan
                ) else own
            }
            Edition.CHRONOLOGY -> recipes.groupBy { yearOf(it.createdAt) }.toSortedMap()
                .map { (year, list) ->
                    Chapter("$year год", "${list.size} ${pluralRecipes(list.size)}", list.sortedBy { it.createdAt })
                }
        }
    }

    fun pickCoverPhoto(
        recipes: List<Recipe>, moments: List<FamilyMoment>, people: List<Person>
    ): String? = recipes.filter { it.photos.isNotEmpty() }.maxByOrNull { it.timesCooked }?.photos?.first()
        ?: recipes.firstOrNull { it.photos.isNotEmpty() }?.photos?.first()
        ?: moments.firstOrNull { it.photos.isNotEmpty() }?.photos?.first()
        ?: people.firstOrNull { it.photos.isNotEmpty() }?.photos?.first()

    /**
     * Если у блюда нет своей истории — вставляем эмоциональную цитату-рамку
     * с подписью того, от кого рецепт. Так на развороте всегда есть живой текст.
     */
    fun withQuote(recipe: Recipe, person: Person?, authorName: String?, options: BookOptions): Recipe {
        if (recipe.story.isNotBlank() || !options.showQuotes) return recipe
        val signature = person?.let {
            if (it.relation.isNotBlank()) "${it.relation} ${it.name}" else it.name
        } ?: authorName?.takeIf { name -> name.isNotBlank() } ?: "Ваша семья"
        return recipe.copy(story = MemoryQuotes.frame(recipe.title, signature,
            MemoryQuotes.seedFor(recipe.id, recipe.title)))
    }

    private fun chapterIf(title: String, subtitle: String, recipes: List<Recipe>): Chapter? =
        if (recipes.isEmpty()) null else Chapter(title, subtitle, recipes)

    fun pluralRecipes(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "рецепт"
            mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> "рецепта"
            else -> "рецептов"
        }
    }

    private fun yearOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
}
