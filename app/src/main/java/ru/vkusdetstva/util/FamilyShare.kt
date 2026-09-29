package ru.vkusdetstva.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import java.io.File

object FamilyShare {
    fun shareRecipePdf(context: Context, recipe: Recipe, author: String) {
        sharePdf(context, "recipe-${recipe.id}.pdf", recipe.title) { pdf ->
            addRecipePage(pdf, recipe, author, 1)
        }
    }

    fun sharePersonChapterPdf(context: Context, person: Person, recipes: List<Recipe>) {
        sharePdf(context, "chapter-${person.id}.pdf", "Рецепты · ${person.name}") { pdf ->
            recipes.forEachIndexed { index, recipe -> addRecipePage(pdf, recipe, person.name, index + 1) }
        }
    }

    private fun sharePdf(context: Context, fileName: String, chooserTitle: String, build: (PdfDocument) -> Unit) {
        runCatching {
            val folder = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(folder, fileName)
            val pdf = PdfDocument()
            try {
                build(pdf)
                file.outputStream().use { out -> pdf.writeTo(out) }
            } finally {
                pdf.close()
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, chooserTitle))
        }
    }

    private fun addRecipePage(pdf: PdfDocument, recipe: Recipe, author: String, pageNumber: Int) {
        val info = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        val page = pdf.startPage(info)
        val canvas = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; isFakeBoldText = true }
        val section = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        var y = 64f
        y = drawWrapped(canvas, recipe.title, title, 48f, y, 500f, 30f)
        y = drawWrapped(canvas, "От кого: $author", small, 48f, y + 4f, 500f, 18f)
        if (recipe.story.isNotBlank()) y = drawWrapped(canvas, recipe.story, body, 48f, y + 16f, 500f, 19f)
        y = drawWrapped(canvas, "Ингредиенты", section, 48f, y + 18f, 500f, 22f)
        y = drawWrapped(canvas, recipe.ingredients.ifBlank { "—" }, body, 48f, y + 4f, 500f, 19f)
        y = drawWrapped(canvas, "Приготовление", section, 48f, y + 18f, 500f, 22f)
        drawWrapped(canvas, recipe.steps.ifBlank { "—" }, body, 48f, y + 4f, 500f, 19f)
        pdf.finishPage(page)
    }

    private fun drawWrapped(canvas: android.graphics.Canvas, text: String, paint: Paint, x: Float,
                            startY: Float, maxWidth: Float, lineHeight: Float): Float {
        var y = startY
        text.lines().forEach { paragraph ->
            var line = ""
            paragraph.split(Regex("\\s+")).forEach { word ->
                val candidate = if (line.isBlank()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotBlank()) {
                    if (y < 800f) canvas.drawText(line, x, y, paint)
                    y += lineHeight
                    line = word
                } else line = candidate
            }
            if (line.isNotBlank()) {
                if (y < 800f) canvas.drawText(line, x, y, paint)
                y += lineHeight
            }
            y += lineHeight * 0.3f
        }
        return y
    }
}
