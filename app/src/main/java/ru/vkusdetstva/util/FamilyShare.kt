package ru.vkusdetstva.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ru.vkusdetstva.data.RecipeVersion
import ru.vkusdetstva.util.book.BookComposer
import ru.vkusdetstva.util.book.BookOptions
import ru.vkusdetstva.util.book.BookRenderer
import java.io.File
import ru.vkusdetstva.data.AuthorProfile
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe

object FamilyShare {
    fun recipeText(recipe: Recipe, person: Person?, author: AuthorProfile?): String = buildString {
        append(recipe.title); append("\n")
        val by = person?.name ?: author?.name
        if (!by.isNullOrBlank()) { append("Рецепт от "); append(by); append("\n") }
        if (recipe.story.isNotBlank()) { append("\n"); append(recipe.story.trim()); append("\n") }
        if (recipe.ingredients.isNotBlank()) { append("\nИнгредиенты\n"); append(recipe.ingredients.trim()); append("\n") }
        if (recipe.steps.isNotBlank()) { append("\nПриготовление\n"); append(recipe.steps.trim()); append("\n") }
        if (recipe.notes.isNotBlank()) { append("\nЗаметка\n"); append(recipe.notes.trim()); append("\n") }
        append("\nИз семейного архива «Вкус детства»")
    }

    fun shareText(context: Context, subject: String, text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Отправить родне"))
    }

    /** Export only the selected recipes; leave the full book export separate. */
    fun createPdf(context: Context, title: String, recipes: List<Recipe>, people: List<Person>,
                  versions: List<RecipeVersion>, author: AuthorProfile?): android.net.Uri {
        require(recipes.isNotEmpty())
        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File.createTempFile("family-share-", ".pdf", folder)
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val options = BookOptions(title = title, includePeople = false, includeMoments = false,
                includeLined = false)
            val model = BookComposer.compose(people, recipes, versions, emptyList(), options, author?.name)
            BookRenderer.render(context, model, uri, booklet = false)
            uri
        } catch (e: Exception) { file.delete(); throw e }
    }

    fun sharePdf(context: Context, title: String, uri: android.net.Uri) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Отправить родне"))
    }
}
