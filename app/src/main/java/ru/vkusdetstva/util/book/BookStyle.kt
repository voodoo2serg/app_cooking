package ru.vkusdetstva.util.book

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Константы страницы, краски и базовые примитивы вёрстки. Размеры в пунктах PDF (1/72 дюйма). */
internal object BookStyle {
    const val PAGE_W = 420f
    const val PAGE_H = 595f
    const val MARGIN_OUTER = 40f
    const val MARGIN_INNER = 68f
    const val MARGIN_TOP = 46f
    const val MARGIN_BOTTOM = 52f
    const val COLUMN_W = PAGE_W - MARGIN_OUTER - MARGIN_INNER // 312
    const val CONTENT_TOP = 70f
    const val CONTENT_BOTTOM = PAGE_H - MARGIN_BOTTOM         // 543

    val INK = Color.rgb(0x31, 0x29, 0x21)
    val MUTED = Color.rgb(0x77, 0x6E, 0x64)
    val CREAM = Color.rgb(0xFB, 0xF5, 0xE9)
    val LINE = Color.rgb(0xD9, 0xCD, 0xBF)

    fun toneColor(tone: PageTone): Int = if (tone == PageTone.CREAM) CREAM else Color.WHITE

    /** Пара (левый край, правый край) текстовой колонки с учётом чётности страницы. */
    fun column(verso: Boolean): Pair<Float, Float> =
        if (verso) MARGIN_OUTER to PAGE_W - MARGIN_INNER
        else MARGIN_INNER to PAGE_W - MARGIN_OUTER

    fun headingPaint(opts: BookOptions, size: Float, bold: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            val base = if (opts.headingFont == FontChoice.SERIF) Typeface.SERIF else Typeface.SANS_SERIF
            typeface = if (bold) Typeface.create(base, Typeface.BOLD) else base
            textSize = size
            color = INK
        }

    fun bodyPaint(opts: BookOptions, size: Float, color: Int = INK,
                  italic: Boolean = false, bold: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            val base = if (opts.bodyFont == FontChoice.SERIF) Typeface.SERIF else Typeface.SANS_SERIF
            typeface = when {
                bold -> Typeface.create(base, Typeface.BOLD)
                italic -> Typeface.create(base, Typeface.ITALIC)
                else -> base
            }
            textSize = size
            this.color = color
        }

    fun kickerPaint(color: Int): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 7.5f
        this.color = color
        letterSpacing = 0.12f
    }

    fun numberPaint(color: Int, size: Float): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SERIF
        textSize = size
        this.color = color
    }

    fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .build()

    fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    fun upper(text: String): String = text.uppercase(Locale("ru"))

    /** Делит текст на «влезающее до maxHeight» и остаток по границе строки. */
    fun splitByHeight(text: String, paint: TextPaint, width: Float, maxHeight: Float): Pair<String, String> {
        if (text.isBlank()) return "" to ""
        val layout = layout(text, paint, width)
        if (layout.height <= maxHeight) return text to ""
        var line = layout.getLineForVertical(maxHeight.toInt())
        while (line > 0 && layout.getLineBottom(line) > maxHeight) line--
        val cut = layout.getLineEnd(line)
        if (cut <= 0) return "" to text
        return text.substring(0, cut).trimEnd() to text.substring(cut).trimStart()
    }

    fun starPath(cx: Float, cy: Float, outer: Float): Path {
        val path = Path()
        for (i in 0 until 10) {
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val r = if (i % 2 == 0) outer.toDouble() else outer * 0.45
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }

    fun drawStars(canvas: Canvas, x: Float, y: Float, value: Int, accent: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE }
        for (i in 0 until 5) {
            canvas.drawPath(starPath(x + i * 11.5f + 4.5f, y, 4.5f), if (i < value) fill else empty)
        }
    }

    /** Домик с трубой и сердцем-дымком — фирменный знак. */
    fun drawHouse(canvas: Canvas, cx: Float, cy: Float, scale: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val roof = Path().apply {
            moveTo(cx - 15f * scale, cy - 6f * scale)
            lineTo(cx + 15f * scale, cy - 6f * scale)
            lineTo(cx, cy - 18f * scale)
            close()
        }
        canvas.drawPath(roof, paint)
        canvas.drawRect(cx - 13f * scale, cy - 6f * scale, cx + 13f * scale, cy + 14f * scale, paint)
        canvas.drawRect(cx + 6f * scale, cy - 15f * scale, cx + 11f * scale, cy - 8f * scale, paint)
        val hy = cy - 24f * scale
        val r = 2.4f * scale
        canvas.drawCircle(cx - r * 0.95f, hy, r, paint)
        canvas.drawCircle(cx + r * 0.95f, hy, r, paint)
        val tri = Path().apply {
            moveTo(cx - r * 1.9f, hy + r * 0.2f)
            lineTo(cx + r * 1.9f, hy + r * 0.2f)
            lineTo(cx, hy + r * 2.3f)
            close()
        }
        canvas.drawPath(tri, paint)
    }

    fun drawOrnament(canvas: Canvas, fromX: Float, toX: Float, y: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        var x = fromX
        while (x <= toX) {
            val d = Path().apply {
                moveTo(x, y - 3f); lineTo(x + 3f, y); lineTo(x, y + 3f); lineTo(x - 3f, y); close()
            }
            canvas.drawPath(d, paint)
            x += 10f
        }
    }

    fun px(pt: Float): Int = (pt * 300f / 72f + 0.5f).toInt()

    /** Декодирует фото с даунсемплингом до 300 dpi и center-crop под целевой размер. */
    fun decodeCrop(path: String?, targetWpt: Float, targetHpt: Float): Bitmap? {
        if (path == null) return null
        val file = File(path)
        if (!file.isFile) return null
        val targetW = px(targetWpt)
        val targetH = px(targetHpt)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) sample *= 2
        val src = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt().coerceAtLeast(targetW)
        val scaledH = (src.height * scale).toInt().coerceAtLeast(targetH)
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        if (scaled != src) src.recycle()
        val x = ((scaledW - targetW) / 2).coerceIn(0, scaledW - targetW)
        val y = ((scaledH - targetH) / 2).coerceIn(0, scaledH - targetH)
        val cropped = Bitmap.createBitmap(scaled, x, y, targetW, targetH)
        if (cropped != scaled) scaled.recycle()
        return cropped
    }

    fun roundedPhoto(canvas: Canvas, bitmap: Bitmap, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        canvas.save()
        val path = Path().apply { addRoundRect(RectF(l, t, r, b), radius, radius, Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, RectF(l, t, r, b), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    }

    fun placeholder(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, accent: Int) {
        val frame = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = LINE }
        canvas.drawRect(l, t, r, b, frame)
        drawHouse(canvas, (l + r) / 2, (t + b) / 2 - 6f, minOf(r - l, b - t) / 64f, accent)
    }

    fun fill(canvas: Canvas, color: Int) {
        canvas.drawRect(0f, 0f, PAGE_W, PAGE_H, Paint().apply { this.color = color })
    }
}
