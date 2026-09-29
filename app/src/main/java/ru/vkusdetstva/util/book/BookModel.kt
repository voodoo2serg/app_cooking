package ru.vkusdetstva.util.book

import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import java.util.Calendar

enum class Edition { BY_POPULARITY, BY_PEOPLE, CHRONOLOGY }
enum class CoverTemplate { CLASSIC, PHOTO, ARCHIVE }
enum class CoverBackground { SOLID, PHOTO, PATTERN }
enum class PageTone { WHITE, CREAM }
enum class FontChoice { SANS, SERIF }

/** Размер листа в пунктах PDF (1/72 дюйма). Пропорции у A-серии одинаковы, поэтому вёрстка масштабируется без переверстки. */
enum class PageFormat(val w: Float, val h: Float, val label: String) {
    A5(420f, 595f, "A5"), A4(595f, 842f, "A4"), A3(842f, 1191f, "A3")
}

/** Книжный — одна страница PDF на страницу книги; альбомный — целый разворот (лево+право) на листе. */
enum class Orientation { PORTRAIT, ALBUM }

data class BookOptions(
    val title: String = "Фамильные рецепты",
    val familyName: String = "Моя семья",
    val edition: Edition = Edition.BY_POPULARITY,
    val coverTemplate: CoverTemplate = CoverTemplate.PHOTO,
    val coverBackground: CoverBackground = CoverBackground.PHOTO,
    val coverColor: Int = 0xFFA74731.toInt(),
    val accentColor: Int = 0xFFA74731.toInt(),
    val pageTone: PageTone = PageTone.WHITE,
    val headingFont: FontChoice = FontChoice.SERIF,
    val bodyFont: FontChoice = FontChoice.SANS,
    val includePeople: Boolean = true,
    val includeMoments: Boolean = true,
    val includeLined: Boolean = true,
    val linedPageCount: Int = 4,
    val showRatings: Boolean = true,
    val showTimesCooked: Boolean = true,
    val maxVersions: Int = 3,
    val pageSize: PageFormat = PageFormat.A5,
    val orientation: Orientation = Orientation.PORTRAIT,
    /** Пускать ли в раздел «Люди нашей книги» тех, о ком ещё нет рецептов. */
    val includePeopleWithoutRecipes: Boolean = true,
    /** Эмоциональные цитаты-рамки для блюд без своей истории. */
    val showQuotes: Boolean = true
)

data class TocEntry(val label: String, val level: Int, val refKey: String, val page: Int? = null)

sealed interface BookPage {
    data class Cover(val template: CoverTemplate, val background: CoverBackground, val photoPath: String?) : BookPage
    data object Flyleaf : BookPage
    data class Toc(val entries: List<TocEntry>) : BookPage
    data class ChapterOpener(val number: Int, val title: String, val subtitle: String) : BookPage
    data class RecipeSpread(
        val recipe: Recipe, val author: Person?, val versions: List<RecipeVersion>,
        val chapterTitle: String
    ) : BookPage
    data class PeoplePage(val persons: List<Person>, val recipeCounts: Map<Long, Int>) : BookPage
    data class MomentPage(val moment: FamilyMoment) : BookPage
    data object Lined : BookPage
    data object BackCover : BookPage
}

data class BookMeta(
    val recipeCount: Int, val peopleCount: Int, val momentCount: Int,
    val chapterCount: Int, val year: Int
)

data class BookModel(
    val options: BookOptions,
    val pages: List<BookPage>,
    val meta: BookMeta
)
