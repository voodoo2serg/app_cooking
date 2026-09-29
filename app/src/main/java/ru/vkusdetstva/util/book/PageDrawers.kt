package ru.vkusdetstva.util.book

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import ru.vkusdetstva.data.RecipeVersion

/** Отрисовщики физических страниц книги. Все координаты — в пунктах PDF. */
internal object PageDrawers {

    // ---------- Общие примитивы ----------

    private fun rect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        canvas.drawRect(l, t, r, b, Paint().apply { this.color = color })
    }

    private fun frame(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, width: Float) {
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            this.color = color
        }
        canvas.drawRect(l, t, r, b, paint)
    }

    private fun centeredText(canvas: Canvas, text: String, paint: TextPaint, cx: Float, baseline: Float) {
        canvas.drawText(text, cx - paint.measureText(text) / 2f, baseline, paint)
    }

    private fun kicker(canvas: Canvas, text: String, x: Float, baseline: Float, color: Int) {
        canvas.drawText(BookStyle.upper(text), x, baseline, BookStyle.kickerPaint(color))
    }

    private fun hairline(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int = BookStyle.LINE, width: Float = 0.5f) {
        canvas.drawLine(x1, y1, x2, y2, Paint().apply { this.color = color; strokeWidth = width })
    }

    private fun drawNoteGlyph(canvas: Canvas, x: Float, y: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(x + 3f, y, 2.8f, paint)
        val stem = Paint().apply { this.color = color; strokeWidth = 1.2f }
        canvas.drawLine(x + 5.8f, y, x + 5.8f, y - 12f, stem)
        val flag = Path().apply {
            moveTo(x + 5.8f, y - 12f)
            quadTo(x + 11f, y - 9f, x + 9f, y - 4f)
        }
        canvas.drawPath(flag, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.2f
        })
    }

    private fun pluralTimes(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "раз"
        n % 10 in 2..4 && (n % 100 < 12 || n % 100 > 14) -> "раза"
        else -> "раз"
    }

    private fun drawBullets(canvas: Canvas, lines: List<String>, x: Float, y: Float, w: Float, opts: BookOptions): Float {
        val paint = BookStyle.bodyPaint(opts, 10f)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = opts.accentColor }
        var yy = y
        lines.forEach { line ->
            val layout = BookStyle.layout(line, paint, w - 16f)
            canvas.drawCircle(x + 3.5f, yy + layout.height / 2f - 2f, 1.5f, dot)
            BookStyle.drawLayout(canvas, layout, x + 16f, yy)
            yy += layout.height + 2f
        }
        return yy
    }

    private fun drawSteps(canvas: Canvas, lines: List<String>, startNumber: Int, x: Float, y: Float, w: Float, opts: BookOptions): Float {
        val paint = BookStyle.bodyPaint(opts, 10f)
        val numbers = BookStyle.numberPaint(opts.accentColor, 10f)
        var yy = y
        lines.forEachIndexed { i, line ->
            val layout = BookStyle.layout(line, paint, w - 16f)
            canvas.drawText("${startNumber + i}.", x, yy + 9f, numbers)
            BookStyle.drawLayout(canvas, layout, x + 16f, yy)
            yy += layout.height + 2f
        }
        return yy
    }

    private fun monogram(canvas: Canvas, name: String, x: Float, y: Float, opts: BookOptions) {
        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = opts.accentColor }
        canvas.drawCircle(x + 42.5f, y + 42.5f, 42.5f, circle)
        val mono = BookStyle.headingPaint(opts, 24f, bold = true).apply { color = BookStyle.CREAM }
        val initial = name.take(1).uppercase()
        centeredText(canvas, initial, mono, x + 42.5f, y + 42.5f + 8.5f)
    }

    private fun titleSize(title: String) = if (title.length <= 28) 16f else 14f

    // ---------- Колонтитулы и фон ----------

    fun chrome(canvas: Canvas, opts: BookOptions, num: Int?, chapter: String?, verso: Boolean) {
        BookStyle.fill(canvas, BookStyle.toneColor(opts.pageTone))
        if (num == null) return
        val (left, right) = BookStyle.column(verso)
        if (chapter != null) {
            val paint = BookStyle.kickerPaint(BookStyle.MUTED)
            val text = BookStyle.upper(if (verso) chapter else opts.title)
            val width = paint.measureText(text)
            if (verso) canvas.drawText(text, left, BookStyle.MARGIN_TOP, paint)
            else canvas.drawText(text, right - width, BookStyle.MARGIN_TOP, paint)
            hairline(canvas, left, BookStyle.MARGIN_TOP + 10f, right, BookStyle.MARGIN_TOP + 10f)
        }
        val footer = BookStyle.numberPaint(BookStyle.MUTED, 9f)
        val label = num.toString()
        centeredText(canvas, label, footer, BookStyle.PAGE_W / 2f, BookStyle.PAGE_H - 24f)
    }

    // ---------- Обложка ----------

    fun cover(canvas: Canvas, model: BookModel, cover: BookPage.Cover) {
        val opts = model.options
        val meta = model.meta
        val photo = if (cover.background == CoverBackground.PHOTO) {
            BookStyle.decodeCrop(cover.photoPath, BookStyle.PAGE_W, 400f)
        } else null

        if (cover.template == CoverTemplate.PHOTO && photo != null) {
            canvas.drawBitmap(photo, null, RectF(0f, 0f, BookStyle.PAGE_W, 400f), Paint(Paint.FILTER_BITMAP_FLAG))
            photo.recycle()
            rect(canvas, 0f, 400f, BookStyle.PAGE_W, BookStyle.PAGE_H, opts.coverColor)
            val size = if (opts.title.length <= 16) 26f else 20f
            centeredText(canvas, BookStyle.upper(opts.title), BookStyle.headingPaint(opts, size, bold = true).apply { color = BookStyle.CREAM }, BookStyle.PAGE_W / 2f, 456f)
            hairline(canvas, BookStyle.PAGE_W / 2f - 30f, 470f, BookStyle.PAGE_W / 2f + 30f, 470f, BookStyle.CREAM, 0.8f)
            centeredText(canvas, opts.familyName, BookStyle.bodyPaint(opts, 11f, BookStyle.CREAM), BookStyle.PAGE_W / 2f, 492f)
            centeredText(canvas, "${meta.year}", BookStyle.bodyPaint(opts, 10f, BookStyle.CREAM), BookStyle.PAGE_W / 2f, 514f)
            return
        }

        when (cover.template) {
            CoverTemplate.ARCHIVE -> {
                BookStyle.fill(canvas, BookStyle.CREAM)
                BookStyle.drawOrnament(canvas, 60f, BookStyle.PAGE_W - 60f, 130f, opts.accentColor)
                BookStyle.drawOrnament(canvas, 60f, BookStyle.PAGE_W - 60f, 452f, opts.accentColor)
                val size = if (opts.title.length <= 16) 30f else 24f
                centeredText(canvas, BookStyle.upper(opts.title), BookStyle.headingPaint(opts, size, bold = true), BookStyle.PAGE_W / 2f, 268f)
                centeredText(canvas, opts.familyName, BookStyle.bodyPaint(opts, 12f, BookStyle.MUTED), BookStyle.PAGE_W / 2f, 300f)
                val stats = "${meta.recipeCount} ${BookComposer.pluralRecipes(meta.recipeCount)} · ${meta.peopleCount} человек · ${meta.year}"
                centeredText(canvas, stats, BookStyle.bodyPaint(opts, 10f, BookStyle.MUTED), BookStyle.PAGE_W / 2f, 486f)
                BookStyle.drawHouse(canvas, BookStyle.PAGE_W / 2f, 512f, 1.0f, opts.accentColor)
            }
            else -> { // Классика; также шаблон «Фото» без доступного фото
                BookStyle.fill(canvas, opts.coverColor)
                if (cover.background == CoverBackground.PATTERN) {
                    BookStyle.drawOrnament(canvas, 40f, BookStyle.PAGE_W - 40f, 60f, BookStyle.CREAM)
                    BookStyle.drawOrnament(canvas, 40f, BookStyle.PAGE_W - 40f, BookStyle.PAGE_H - 60f, BookStyle.CREAM)
                }
                frame(canvas, 28f, 28f, BookStyle.PAGE_W - 28f, BookStyle.PAGE_H - 28f, BookStyle.CREAM, 1f)
                frame(canvas, 34f, 34f, BookStyle.PAGE_W - 34f, BookStyle.PAGE_H - 34f, BookStyle.CREAM, 0.5f)
                val size = if (opts.title.length <= 16) 30f else 24f
                centeredText(canvas, BookStyle.upper(opts.title), BookStyle.headingPaint(opts, size, bold = true).apply { color = BookStyle.CREAM }, BookStyle.PAGE_W / 2f, 250f)
                hairline(canvas, BookStyle.PAGE_W / 2f - 30f, 268f, BookStyle.PAGE_W / 2f + 30f, 268f, BookStyle.CREAM, 0.8f)
                centeredText(canvas, opts.familyName, BookStyle.bodyPaint(opts, 12f, BookStyle.CREAM), BookStyle.PAGE_W / 2f, 294f)
                centeredText(canvas, "${meta.year}", BookStyle.bodyPaint(opts, 10f, BookStyle.CREAM), BookStyle.PAGE_W / 2f, 316f)
                BookStyle.drawHouse(canvas, BookStyle.PAGE_W / 2f, 480f, 1.6f, BookStyle.CREAM)
            }
        }
    }

    // ---------- Форзац ----------

    fun flyleaf(canvas: Canvas, model: BookModel) {
        val opts = model.options
        val meta = model.meta
        BookStyle.fill(canvas, BookStyle.CREAM)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = 14f
            color = BookStyle.INK
        }
        centeredText(canvas, "Эта книга собрана семьёй ${opts.familyName}", paint, BookStyle.PAGE_W / 2f, 244f)
        centeredText(canvas, "в ${meta.year} году", paint, BookStyle.PAGE_W / 2f, 270f)
        centeredText(canvas, "${meta.recipeCount} ${BookComposer.pluralRecipes(meta.recipeCount)},", paint, BookStyle.PAGE_W / 2f, 296f)
        centeredText(canvas, "${meta.peopleCount} человек и ${meta.momentCount} историй за столом", paint, BookStyle.PAGE_W / 2f, 322f)
        BookStyle.drawHouse(canvas, BookStyle.PAGE_W / 2f, 420f, 1.4f, opts.accentColor)
    }

    // ---------- Содержание ----------

    fun toc(canvas: Canvas, entries: List<TocEntry>, index: Int, total: Int, opts: BookOptions, num: Int) {
        chrome(canvas, opts, num, null, verso = false)
        val (left, right) = BookStyle.column(false)
        var y = BookStyle.CONTENT_TOP + 14f
        canvas.drawText("Содержание", left, y + 12f, BookStyle.headingPaint(opts, 18f, bold = true))
        y += 36f
        entries.forEach { entry ->
            val paint = if (entry.level == 0) BookStyle.bodyPaint(opts, 11f, BookStyle.INK, bold = true)
            else BookStyle.bodyPaint(opts, 10f, BookStyle.INK)
            val indent = if (entry.level == 0) 0f else 14f
            var label = entry.label
            val maxLabel = right - left - 46f - indent
            while (paint.measureText(label) > maxLabel && label.length > 4) label = label.dropLast(2)
            val pageStr = entry.page?.toString() ?: ""
            val pageWidth = paint.measureText(pageStr)
            canvas.drawText(label, left + indent, y + 9f, paint)
            if (pageStr.isNotEmpty()) {
                canvas.drawText(pageStr, right - pageWidth, y + 9f, paint)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BookStyle.LINE }
                var x = left + indent + paint.measureText(label) + 10f
                val end = right - pageWidth - 10f
                while (x < end) {
                    canvas.drawCircle(x, y + 6f, 0.6f, dot)
                    x += 4f
                }
            }
            y += if (entry.level == 0) 17f else 13.5f
        }
        if (total > 1) centeredText(canvas, "— ${index + 1} —", BookStyle.bodyPaint(opts, 8f, BookStyle.MUTED), BookStyle.PAGE_W / 2f, BookStyle.PAGE_H - 40f)
    }

    // ---------- Шмуцтитул главы ----------

    fun opener(canvas: Canvas, page: BookPage.ChapterOpener, opts: BookOptions, num: Int, verso: Boolean) {
        chrome(canvas, opts, num, null, verso)
        val (left, _) = BookStyle.column(verso)
        var y = BookStyle.CONTENT_TOP + 140f
        canvas.drawText("%02d".format(page.number), left, y + 48f, BookStyle.numberPaint(opts.accentColor, 48f))
        y += 72f
        hairline(canvas, left, y, left + 48f, y, opts.accentColor, 1f)
        y += 26f
        val title = BookStyle.layout(page.title, BookStyle.headingPaint(opts, 22f), BookStyle.COLUMN_W)
        BookStyle.drawLayout(canvas, title, left, y)
        y += title.height + 10f
        canvas.drawText(page.subtitle, left, y + 10f, BookStyle.bodyPaint(opts, 10f, BookStyle.MUTED))
    }

    // ---------- Разворот рецепта ----------

    fun spreadVerso(canvas: Canvas, spread: BookPage.RecipeSpread, part: PartData, totalParts: Int,
                    opts: BookOptions, num: Int, chapter: String?, verso: Boolean) {
        chrome(canvas, opts, num, chapter, verso)
        val (left, right) = BookStyle.column(verso)
        val w = right - left
        var y = BookStyle.CONTENT_TOP
        kicker(canvas, spread.chapterTitle, left, y + 8f, opts.accentColor)
        y += 18f
        if (part.partNo == 1) {
            val titleLayout = BookStyle.layout(spread.recipe.title, BookStyle.headingPaint(opts, titleSize(spread.recipe.title), bold = true), w)
            BookStyle.drawLayout(canvas, titleLayout, left, y)
            y += titleLayout.height + 10f
            val photo = BookStyle.decodeCrop(spread.recipe.photos.firstOrNull(), 312f, 200f)
            if (photo != null) {
                BookStyle.roundedPhoto(canvas, photo, left, y, left + w, y + 200f, 4f)
                photo.recycle()
            } else {
                BookStyle.placeholder(canvas, left, y, left + w, y + 200f, opts.accentColor)
            }
            y += 212f
            if (opts.showTimesCooked && spread.recipe.timesCooked > 0) {
                canvas.drawText("Готовили ${spread.recipe.timesCooked} ${pluralTimes(spread.recipe.timesCooked)}", left, y + 9f, BookStyle.bodyPaint(opts, 9f, opts.accentColor))
                y += 18f
            }
            if (part.story.isNotBlank()) {
                BookStyle.drawLayout(canvas, BookStyle.layout(part.story, BookStyle.bodyPaint(opts, 10.5f), w), left, y)
            }
        } else {
            val continuation = BookStyle.layout("${spread.recipe.title} · продолжение", BookStyle.headingPaint(opts, 12f, bold = true), w)
            BookStyle.drawLayout(canvas, continuation, left, y)
            y += continuation.height + 10f
            if (part.story.isNotBlank()) {
                val story = BookStyle.layout(part.story, BookStyle.bodyPaint(opts, 10.5f), w)
                BookStyle.drawLayout(canvas, story, left, y)
                y += story.height + 10f
            }
            if (part.ingredients.isNotEmpty()) {
                kicker(canvas, "Ингредиенты", left, y + 8f, opts.accentColor)
                y += 16f
                y = drawBullets(canvas, part.ingredients, left, y, w, opts)
            }
        }
    }

    fun spreadRecto(canvas: Canvas, spread: BookPage.RecipeSpread, part: PartData, totalParts: Int,
                    opts: BookOptions, num: Int, chapter: String?, verso: Boolean) {
        chrome(canvas, opts, num, chapter, verso)
        val (left, right) = BookStyle.column(verso)
        val w = right - left
        var y = BookStyle.CONTENT_TOP
        if (part.partNo == 1) {
            kicker(canvas, "От кого", left, y + 8f, opts.accentColor)
            y += 16f
            val author = spread.author
            if (author != null) {
                hairline(canvas, left - 8f, y, left - 8f, y + 100f, opts.accentColor, 2f)
                val photo = BookStyle.decodeCrop(author.photos.firstOrNull(), 85f, 85f)
                if (photo != null) {
                    BookStyle.roundedPhoto(canvas, photo, left, y, left + 85f, y + 85f, 4f)
                    photo.recycle()
                } else {
                    monogram(canvas, author.name, left, y, opts)
                }
                BookStyle.drawLayout(canvas, BookStyle.layout(author.name, BookStyle.headingPaint(opts, 12f), w - 95f), left + 95f, y + 2f)
                val meta = listOf(author.relation, author.years).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) canvas.drawText(meta, left + 95f, y + 26f, BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED))
                if (author.story.isNotBlank()) {
                    val (head, _) = BookStyle.splitByHeight(author.story, BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED), w, 40f)
                    if (head.isNotBlank()) {
                        BookStyle.drawLayout(canvas, BookStyle.layout(head, BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED), w), left, y + 92f)
                    }
                }
                y += 118f
            } else {
                canvas.drawText("Семейный рецепт", left, y + 10f, BookStyle.bodyPaint(opts, 10f, BookStyle.MUTED))
                y += 26f
            }
            if (opts.showRatings) {
                val rows = listOf("Вкус" to spread.recipe.taste, "Простота" to spread.recipe.ease, "Память" to spread.recipe.memory)
                    .filter { it.second > 0 }
                if (rows.isNotEmpty()) {
                    kicker(canvas, "Оценки семьи", left, y + 8f, opts.accentColor)
                    y += 14f
                    rows.forEach { (label, value) ->
                        canvas.drawText(label, left, y + 9f, BookStyle.bodyPaint(opts, 9f))
                        BookStyle.drawStars(canvas, left + 70f, y + 5f, value, opts.accentColor)
                        y += 16f
                    }
                    y += 4f
                }
            }
            if (spread.versions.isNotEmpty()) {
                kicker(canvas, "Семейные версии (${spread.versions.size})", left, y + 8f, opts.accentColor)
                y += 16f
                val paint = BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED)
                spread.versions.take(opts.maxVersions).forEach { version ->
                    val layout = BookStyle.layout(versionLine(version), paint, w)
                    BookStyle.drawLayout(canvas, layout, left, y)
                    y += layout.height + 3f
                }
                if (spread.versions.size > opts.maxVersions) {
                    canvas.drawText("и ещё ${spread.versions.size - opts.maxVersions} версий…", left, y + 9f, paint)
                    y += 15f
                }
                y += 4f
            }
            if (spread.recipe.audioPath != null) {
                drawNoteGlyph(canvas, left, y + 4f, opts.accentColor)
                canvas.drawText("В приложении к этому рецепту есть голосовая заметка", left + 14f, y + 8f, BookStyle.bodyPaint(opts, 8f, BookStyle.MUTED))
                y += 16f
            }
            if (part.ingredients.isNotEmpty()) {
                kicker(canvas, "Ингредиенты", left, y + 8f, opts.accentColor)
                y += 16f
                y = drawBullets(canvas, part.ingredients, left, y, w, opts)
                y += 8f
            }
            if (part.steps.isNotEmpty()) {
                kicker(canvas, "Приготовление", left, y + 8f, opts.accentColor)
                y += 16f
                y = drawSteps(canvas, part.steps, part.stepStart, left, y, w, opts)
                y += 8f
            }
            if (part.notes.isNotBlank()) {
                kicker(canvas, "Семейная заметка", left, y + 8f, opts.accentColor)
                y += 16f
                BookStyle.drawLayout(canvas, BookStyle.layout(part.notes, BookStyle.bodyPaint(opts, 10f, italic = true), w), left, y)
            }
        } else {
            if (part.steps.isNotEmpty()) {
                kicker(canvas, "Приготовление · продолжение", left, y + 8f, opts.accentColor)
                y += 16f
                y = drawSteps(canvas, part.steps, part.stepStart, left, y, w, opts)
                y += 8f
            }
            if (part.notes.isNotBlank()) {
                kicker(canvas, "Семейная заметка", left, y + 8f, opts.accentColor)
                y += 16f
                BookStyle.drawLayout(canvas, BookStyle.layout(part.notes, BookStyle.bodyPaint(opts, 10f, italic = true), w), left, y)
            }
        }
    }

    private fun versionLine(version: RecipeVersion): String =
        "${version.personName}: ${version.change}" + if (version.note.isNotBlank()) ". ${version.note}" else ""

    // ---------- Люди нашей книги ----------

    fun people(canvas: Canvas, page: BookPage.PeoplePage, opts: BookOptions, num: Int, verso: Boolean) {
        chrome(canvas, opts, num, "Люди нашей книги", verso)
        val (left, right) = BookStyle.column(verso)
        val w = right - left
        var y = BookStyle.CONTENT_TOP
        kicker(canvas, "Люди нашей книги", left, y + 8f, opts.accentColor)
        y += 20f
        page.persons.forEachIndexed { index, person ->
            if (index > 0) hairline(canvas, left, y - 12f, right, y - 12f)
            val photo = BookStyle.decodeCrop(person.photos.firstOrNull(), 85f, 85f)
            if (photo != null) {
                BookStyle.roundedPhoto(canvas, photo, left, y, left + 85f, y + 85f, 4f)
                photo.recycle()
            } else {
                monogram(canvas, person.name, left, y, opts)
            }
            BookStyle.drawLayout(canvas, BookStyle.layout(person.name, BookStyle.headingPaint(opts, 12f), w - 95f), left + 95f, y)
            val meta = listOf(person.relation, person.years).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) canvas.drawText(meta, left + 95f, y + 26f, BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED))
            canvas.drawText("Рецептов в книге: ${page.recipeCounts[person.id] ?: 0}", left + 95f, y + 42f, BookStyle.bodyPaint(opts, 8f, opts.accentColor))
            if (person.story.isNotBlank()) {
                val (head, _) = BookStyle.splitByHeight(person.story, BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED), w, 58f)
                if (head.isNotBlank()) {
                    BookStyle.drawLayout(canvas, BookStyle.layout(head, BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED), w), left, y + 96f)
                }
            }
            y += 218f
        }
    }

    // ---------- Как мы это едим ----------

    fun moment(canvas: Canvas, page: BookPage.MomentPage, opts: BookOptions, num: Int, verso: Boolean) {
        chrome(canvas, opts, num, "Как мы это едим", verso)
        val (left, right) = BookStyle.column(verso)
        val w = right - left
        var y = BookStyle.CONTENT_TOP
        kicker(canvas, "Как мы это едим", left, y + 8f, opts.accentColor)
        y += 18f
        val title = BookStyle.layout(page.moment.title, BookStyle.headingPaint(opts, 14f), w)
        BookStyle.drawLayout(canvas, title, left, y)
        y += title.height + 8f
        val two = page.moment.photos.size >= 2
        val photoHeight = if (two) 150f else 188f
        page.moment.photos.take(2).forEach { path ->
            val photo = BookStyle.decodeCrop(path, 312f, photoHeight)
            if (photo != null) {
                BookStyle.roundedPhoto(canvas, photo, left, y, left + w, y + photoHeight, 4f)
                photo.recycle()
            } else {
                BookStyle.placeholder(canvas, left, y, left + w, y + photoHeight, opts.accentColor)
            }
            y += photoHeight + 10f
        }
        if (page.moment.story.isNotBlank()) {
            val story = BookStyle.layout(page.moment.story, BookStyle.bodyPaint(opts, 10.5f), w)
            BookStyle.drawLayout(canvas, story, left, y)
            y += story.height + 6f
        }
        if (page.moment.people.isNotBlank()) {
            canvas.drawText("На фото: ${page.moment.people}", left, y + 10f, BookStyle.bodyPaint(opts, 9f, BookStyle.MUTED))
        }
    }

    // ---------- Страницы для записей ----------

    fun lined(canvas: Canvas, opts: BookOptions, num: Int, verso: Boolean) {
        chrome(canvas, opts, num, null, verso)
        val (left, right) = BookStyle.column(verso)
        val paint = Paint().apply { color = BookStyle.LINE; strokeWidth = 0.3f }
        var y = BookStyle.CONTENT_TOP + 20f
        while (y < BookStyle.CONTENT_BOTTOM) {
            canvas.drawLine(left, y, right, y, paint)
            y += 26f
        }
        BookStyle.drawHouse(canvas, right - 14f, BookStyle.CONTENT_BOTTOM + 12f, 0.5f, opts.accentColor)
    }

    // ---------- Задняя обложка ----------

    fun backCover(canvas: Canvas, opts: BookOptions) {
        BookStyle.fill(canvas, opts.coverColor)
        BookStyle.drawHouse(canvas, BookStyle.PAGE_W / 2f, 270f, 2.2f, BookStyle.CREAM)
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 10f
            color = BookStyle.CREAM
            letterSpacing = 0.2f
        }
        centeredText(canvas, "ВКУС ДЕТСТВА", brand, BookStyle.PAGE_W / 2f, 548f)
    }
}
