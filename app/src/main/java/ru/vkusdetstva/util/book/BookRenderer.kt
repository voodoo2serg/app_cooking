package ru.vkusdetstva.util.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import kotlin.math.ceil
import kotlin.math.max

/** Содержимое одной части разворота рецепта (часть = пара страниц verso+recto). */
internal data class PartData(
    val partNo: Int,
    val story: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val stepStart: Int,
    val notes: String
)

/** Физическая страница книги после развёртки продолжений и паддинга. */
internal sealed interface Phys {
    data class CoverP(val page: BookPage.Cover) : Phys
    data object FlyleafP : Phys
    data class TocP(val entries: List<TocEntry>, val index: Int, val total: Int) : Phys
    data class OpenerP(val page: BookPage.ChapterOpener) : Phys
    data class VersoP(val spread: BookPage.RecipeSpread, val part: PartData, val totalParts: Int) : Phys
    data class RectoP(val spread: BookPage.RecipeSpread, val part: PartData, val totalParts: Int) : Phys
    data class PeopleP(val page: BookPage.PeoplePage) : Phys
    data class MomentP(val page: BookPage.MomentPage) : Phys
    data object LinedP : Phys
    data object BackP : Phys
}

object BookRenderer {

    /** Растровое превью обложки для мастера сборки. */
    fun coverPreview(
        options: BookOptions, people: List<Person>, recipes: List<Recipe>,
        moments: List<FamilyMoment>, width: Int, height: Int
    ): Bitmap {
        val model = BookComposer.compose(people, recipes, emptyList(), moments, options)
        val cover = model.pages.filterIsInstance<BookPage.Cover>().first()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / BookStyle.PAGE_W, height / BookStyle.PAGE_H)
        PageDrawers.cover(canvas, model, cover)
        return bitmap
    }

    /**
     * Рендер книги в PDF. booklet = false — обычные страницы A5 по порядку;
     * booklet = true — тетрадная раскладка на A4-альбомных листах для печати «пополам».
     */
    fun render(context: Context, model: BookModel, target: Uri, booklet: Boolean,
               onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        val phys = buildPhysicalPages(model)
        val doc = PdfDocument()
        try {
            if (booklet) {
                require(phys.size % 4 == 0) { "Для раскладки нужно кратное 4 число страниц" }
                val sheets = BookletImposer.bookletOrder(phys.size)
                var done = 0
                sheets.forEach { sheet ->
                    val front = doc.startPage(info(842, 595, done + 1))
                    drawInto(front.canvas, phys, sheet.frontLeft, model)
                    drawInto(front.canvas, phys, sheet.frontRight, model, 421f)
                    doc.finishPage(front)
                    done++
                    onProgress(done, sheets.size * 2)
                    val back = doc.startPage(info(842, 595, done + 1))
                    drawInto(back.canvas, phys, sheet.backLeft, model)
                    drawInto(back.canvas, phys, sheet.backRight, model, 421f)
                    doc.finishPage(back)
                    done++
                    onProgress(done, sheets.size * 2)
                }
            } else {
                phys.forEachIndexed { index, _ ->
                    val page = doc.startPage(info(420, 595, index + 1))
                    drawInto(page.canvas, phys, index, model)
                    doc.finishPage(page)
                    onProgress(index + 1, phys.size)
                }
            }
            context.contentResolver.openOutputStream(target)?.use(doc::writeTo)
                ?: error("Невозможно записать книгу")
        } finally {
            doc.close()
        }
    }

    // ---------- Внутренняя механика ----------

    private fun info(w: Int, h: Int, number: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(w, h, number).create()

    private fun drawInto(canvas: Canvas, phys: List<Phys>, index: Int, model: BookModel, offsetX: Float = 0f) {
        val page = phys.getOrNull(index) ?: return
        canvas.save()
        canvas.translate(offsetX, 0f)
        drawPage(canvas, phys, index, page, model)
        canvas.restore()
    }

    private fun drawPage(canvas: Canvas, phys: List<Phys>, index: Int, page: Phys, model: BookModel) {
        val opts = model.options
        val num = index + 1
        val verso = num % 2 == 0
        var chapter: String? = null
        for (i in 0 until index) {
            when (val prev = phys[i]) {
                is Phys.OpenerP -> chapter = prev.page.title
                is Phys.PeopleP -> chapter = "Люди нашей книги"
                is Phys.MomentP -> chapter = "Как мы это едим"
                else -> {}
            }
        }
        when (page) {
            is Phys.CoverP -> PageDrawers.cover(canvas, model, page.page)
            is Phys.FlyleafP -> PageDrawers.flyleaf(canvas, model)
            is Phys.TocP -> PageDrawers.toc(canvas, page.entries, page.index, page.total, opts, num)
            is Phys.OpenerP -> PageDrawers.opener(canvas, page.page, opts, num, verso)
            is Phys.VersoP -> PageDrawers.spreadVerso(canvas, page.spread, page.part, page.totalParts, opts, num, chapter, verso)
            is Phys.RectoP -> PageDrawers.spreadRecto(canvas, page.spread, page.part, page.totalParts, opts, num, chapter, verso)
            is Phys.PeopleP -> PageDrawers.people(canvas, page.page, opts, num, verso)
            is Phys.MomentP -> PageDrawers.moment(canvas, page.page, opts, num, verso)
            is Phys.LinedP -> PageDrawers.lined(canvas, opts, num, verso)
            is Phys.BackP -> PageDrawers.backCover(canvas, opts)
        }
    }

    /** Развёртка разворотов в физические страницы + паддинг + нумерация содержания. */
    private fun buildPhysicalPages(model: BookModel): List<Phys> {
        val opts = model.options
        val phys = mutableListOf<Phys>()
        model.pages.forEach { page ->
            when (page) {
                is BookPage.Cover -> phys += Phys.CoverP(page)
                is BookPage.Flyleaf -> phys += Phys.FlyleafP
                is BookPage.Toc -> {}
                is BookPage.ChapterOpener -> phys += Phys.OpenerP(page)
                is BookPage.RecipeSpread -> {
                    val parts = expandSpread(page, opts)
                    parts.forEach { part ->
                        phys += Phys.VersoP(page, part, parts.size)
                        phys += Phys.RectoP(page, part, parts.size)
                    }
                }
                is BookPage.PeoplePage -> phys += Phys.PeopleP(page)
                is BookPage.MomentPage -> phys += Phys.MomentP(page)
                is BookPage.Lined -> phys += Phys.LinedP
                is BookPage.BackCover -> phys += Phys.BackP
            }
        }
        val backIndex = phys.indexOfLast { it is Phys.BackP }
        val need = (4 - phys.size % 4) % 4
        repeat(need) { phys.add(backIndex, Phys.LinedP) }

        val tocSource = model.pages.filterIsInstance<BookPage.Toc>().first().entries
        val tocPageCount = max(1, ceil(tocSource.size / 32.0).toInt())
        val numbers = mutableMapOf<String, Int>()
        var firstPeople = true
        var firstMoment = true
        var firstLined = true
        phys.forEachIndexed { index, page ->
            val number = index + 1 + tocPageCount
            when (page) {
                is Phys.OpenerP -> numbers["ch:${page.page.number - 1}"] = number
                is Phys.VersoP -> if (page.part.partNo == 1) numbers["rec:${page.spread.recipe.id}"] = number
                is Phys.PeopleP -> if (firstPeople) { numbers["people"] = number; firstPeople = false }
                is Phys.MomentP -> if (firstMoment) { numbers["moments"] = number; firstMoment = false }
                is Phys.LinedP -> if (firstLined && opts.includeLined) { numbers["lined"] = number; firstLined = false }
                else -> {}
            }
        }
        val tocPages = tocSource.chunked(32).mapIndexed { i, chunk ->
            Phys.TocP(chunk.map { it.copy(page = numbers[it.refKey]) }, i, tocPageCount)
        }
        phys.addAll(2, tocPages)
        return phys
    }

    // ---------- Перетекание длинных рецептов ----------

    private class Remainder(recipe: Recipe) {
        var story: String = recipe.story.trim()
        val ingredients: MutableList<String> = recipe.ingredients.lines()
            .map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        val steps: MutableList<String> = recipe.steps.lines()
            .map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        var notes: String = recipe.notes.trim()

        fun done(): Boolean = story.isBlank() && ingredients.isEmpty() && steps.isEmpty() && notes.isBlank()
    }

    private fun takeLines(source: MutableList<String>, budget: Float, opts: BookOptions): Pair<List<String>, Float> {
        val taken = mutableListOf<String>()
        var height = 0f
        if (budget > 24f) {
            val paint = BookStyle.bodyPaint(opts, 10f)
            val iterator = source.iterator()
            while (iterator.hasNext()) {
                val line = iterator.next()
                val layout = BookStyle.layout(line, paint, BookStyle.COLUMN_W - 16f)
                if (height + layout.height > budget) break
                taken.add(line)
                height += layout.height + 2f
                iterator.remove()
            }
        }
        return taken to height
    }

    private fun versionLine(version: RecipeVersion): String =
        "${version.personName}: ${version.change}" + if (version.note.isNotBlank()) ". ${version.note}" else ""

    private fun fixedRectoHeight(spread: BookPage.RecipeSpread, opts: BookOptions): Float {
        var height = 16f // кикер «От кого»
        val author = spread.author
        if (author != null) {
            var block = 85f + 8f
            if (author.story.isNotBlank()) {
                val (head, _) = BookStyle.splitByHeight(author.story, BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED), BookStyle.COLUMN_W, 40f)
                if (head.isNotBlank()) {
                    height += 0f
                    block += BookStyle.layout(head, BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED), BookStyle.COLUMN_W).height
                }
            }
            height += block
        } else {
            height += 26f
        }
        if (opts.showRatings) {
            val rows = listOf(spread.recipe.taste, spread.recipe.ease, spread.recipe.memory).count { it > 0 }
            if (rows > 0) height += 14f + rows * 16f + 4f
        }
        if (spread.versions.isNotEmpty()) {
            height += 16f
            val paint = BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED)
            spread.versions.take(opts.maxVersions).forEach {
                height += BookStyle.layout(versionLine(it), paint, BookStyle.COLUMN_W).height + 3f
            }
            if (spread.versions.size > opts.maxVersions) height += 15f
            height += 4f
        }
        if (spread.recipe.audioPath != null) height += 16f
        return height
    }

    private fun expandSpread(spread: BookPage.RecipeSpread, opts: BookOptions): List<PartData> {
        val remainder = Remainder(spread.recipe)
        val parts = mutableListOf<PartData>()
        val capacity = BookStyle.CONTENT_BOTTOM - BookStyle.CONTENT_TOP
        val titleSize = if (spread.recipe.title.length <= 28) 16f else 14f
        var consumedSteps = 0
        var partNo = 0
        val maxParts = 8
        while (!remainder.done() && partNo < maxParts) {
            partNo++
            var storyChunk = ""
            var ingChunk: List<String> = emptyList()
            var stepsChunk: List<String> = emptyList()
            var notesChunk = ""
            if (partNo == 1) {
                var used = 18f
                val titleLayout = BookStyle.layout(spread.recipe.title, BookStyle.headingPaint(opts, titleSize, bold = true), BookStyle.COLUMN_W)
                used += titleLayout.height + 10f + 212f
                if (opts.showTimesCooked && spread.recipe.timesCooked > 0) used += 18f
                if (remainder.story.isNotBlank()) {
                    val (head, tail) = BookStyle.splitByHeight(remainder.story, BookStyle.bodyPaint(opts, 10.5f), BookStyle.COLUMN_W, capacity - used - 6f)
                    storyChunk = head
                    remainder.story = tail
                }
                var budget = capacity - fixedRectoHeight(spread, opts) - 10f
                val (ing, ingHeight) = takeLines(remainder.ingredients, budget - 16f, opts)
                ingChunk = ing
                budget -= 16f + ingHeight
                val (steps, stepsHeight) = takeLines(remainder.steps, budget - 16f, opts)
                stepsChunk = steps
                consumedSteps += steps.size
                budget -= 16f + stepsHeight
                if (remainder.notes.isNotBlank() && budget - 16f > 20f) {
                    val (head, tail) = BookStyle.splitByHeight(remainder.notes, BookStyle.bodyPaint(opts, 10f, italic = true), BookStyle.COLUMN_W, budget - 16f)
                    notesChunk = head
                    remainder.notes = tail
                }
            } else {
                var used = 18f + 24f
                if (remainder.story.isNotBlank()) {
                    val (head, tail) = BookStyle.splitByHeight(remainder.story, BookStyle.bodyPaint(opts, 10.5f), BookStyle.COLUMN_W, capacity - used)
                    storyChunk = head
                    remainder.story = tail
                    used += BookStyle.layout(storyChunk, BookStyle.bodyPaint(opts, 10.5f), BookStyle.COLUMN_W).height + 10f
                }
                val (ing, _) = takeLines(remainder.ingredients, capacity - used, opts)
                ingChunk = ing
                var rectoBudget = capacity
                val (steps, stepsHeight) = takeLines(remainder.steps, rectoBudget - 16f, opts)
                stepsChunk = steps
                consumedSteps += steps.size
                rectoBudget -= 16f + stepsHeight
                if (remainder.notes.isNotBlank() && rectoBudget - 16f > 20f) {
                    val (head, tail) = BookStyle.splitByHeight(remainder.notes, BookStyle.bodyPaint(opts, 10f, italic = true), BookStyle.COLUMN_W, rectoBudget - 16f)
                    notesChunk = head
                    remainder.notes = tail
                }
            }
            if (storyChunk.isBlank() && ingChunk.isEmpty() && stepsChunk.isEmpty() && notesChunk.isBlank() && !remainder.done()) {
                when {
                    remainder.ingredients.isNotEmpty() -> ingChunk = listOf(remainder.ingredients.removeAt(0))
                    remainder.steps.isNotEmpty() -> { stepsChunk = listOf(remainder.steps.removeAt(0)); consumedSteps++ }
                    remainder.story.isNotBlank() -> { storyChunk = remainder.story; remainder.story = "" }
                    else -> { notesChunk = remainder.notes; remainder.notes = "" }
                }
            }
            parts += PartData(partNo, storyChunk, ingChunk, stepsChunk, consumedSteps - stepsChunk.size + 1, notesChunk)
        }
        return parts
    }
}
