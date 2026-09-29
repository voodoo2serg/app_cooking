package ru.vkusdetstva.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import java.io.File

object BookPdf {
    fun write(context: Context, destination: Uri, title: String, recipes: List<Recipe>,
              people: List<Person>, versions: List<RecipeVersion>, moments: List<FamilyMoment>) {
        val pdf = PdfDocument()
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(46, 55, 44) }
        val width = 595
        val height = 842
        var pageNumber = 0
        var currentPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        fun newPage() {
            if (canvas != null) pdf.finishPage(currentPage!!)
            currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, ++pageNumber).create())
            canvas = currentPage!!.canvas
            canvas!!.drawColor(android.graphics.Color.WHITE)
            y = 54f
        }
        try {
            newPage()
            ink.textSize = 32f
            canvas!!.drawText(title.take(32), 42f, 160f, ink)
            ink.textSize = 16f
            canvas!!.drawText("Семейная книга рецептов", 42f, 198f, ink)
            y = 260f
            fun line(text: String, size: Float = 15f) {
                ink.textSize = size
                val words = text.replace('\n', ' ').split(Regex("\\s+"))
                var current = ""
                for (word in words) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (ink.measureText(candidate) > width - 84 && current.isNotEmpty()) {
                        if (y > height - 55) newPage()
                        canvas!!.drawText(current, 42f, y, ink); y += size * 1.55f
                        current = word
                    } else current = candidate
                }
                if (current.isNotEmpty()) {
                    if (y > height - 55) newPage()
                    canvas!!.drawText(current, 42f, y, ink); y += size * 1.55f
                }
            }
            for (recipe in recipes) {
                newPage()
                line(recipe.title, 24f)
                people.find { it.id == recipe.personId }?.let { line("От кого: ${it.name}", 13f) }
                val photo = recipe.photos.firstOrNull()?.let { File(it) }
                if (photo?.exists() == true) {
                    val bitmap = BitmapFactory.decodeFile(photo.absolutePath)
                    if (bitmap != null) {
                        val rect = android.graphics.RectF(42f, y + 8, 325f, y + 180)
                        canvas!!.drawBitmap(bitmap, null, rect, ink)
                        bitmap.recycle(); y += 198
                    }
                }
                if (recipe.story.isNotBlank()) { line("История", 18f); line(recipe.story) }
                if (recipe.ingredients.isNotBlank()) { y += 10; line("Ингредиенты", 18f); recipe.ingredients.lines().forEach { line(it) } }
                if (recipe.steps.isNotBlank()) { y += 10; line("Приготовление", 18f); recipe.steps.lines().forEach { line(it) } }
                if (recipe.notes.isNotBlank()) { y += 10; line("Семейная заметка", 18f); line(recipe.notes) }
                val history = versions.filter { it.recipeId == recipe.id }.sortedBy { it.createdAt }
                if (history.isNotEmpty()) { y += 10; line("Как менялся рецепт", 18f) }
                history.forEach { line("${it.personName}: ${it.change}. ${it.note}") }
            }
            for (person in people) {
                newPage()
                line(person.name, 24f)
                if (person.relation.isNotBlank()) line(person.relation, 14f)
                if (person.years.isNotBlank()) line(person.years, 13f)
                if (person.story.isNotBlank()) { y += 14; line(person.story) }
                val photo = person.photos.firstOrNull()?.let { File(it) }
                if (photo?.exists() == true) {
                    BitmapFactory.decodeFile(photo.absolutePath)?.let { bitmap ->
                        if (y > 600) newPage()
                        canvas!!.drawBitmap(bitmap, null, android.graphics.RectF(42f, y + 8, 325f, y + 190), ink)
                        bitmap.recycle(); y += 200
                    }
                }
            }
            for (moment in moments) {
                newPage()
                line("Как мы это едим", 23f); line(moment.title, 19f)
                line(moment.story)
                if (moment.people.isNotBlank()) line("На фото: ${moment.people}", 12f)
                for (path in moment.photos) {
                    if (y > 600) newPage()
                    BitmapFactory.decodeFile(path)?.let { bitmap ->
                        canvas!!.drawBitmap(bitmap, null, android.graphics.RectF(42f, y + 8, 325f, y + 190), ink)
                        bitmap.recycle(); y += 200
                    }
                }
            }
            pdf.finishPage(currentPage!!)
            currentPage = null
            context.contentResolver.openOutputStream(destination)?.use(pdf::writeTo)
                ?: error("Невозможно сохранить книгу")
        } finally {
            pdf.close()
        }
    }

}
