package ru.vkusdetstva.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ru.vkusdetstva.data.AuthorProfile
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import java.io.File

/**
 * «Отправить родне» — простой текст, который уходит письмом или в соцсети
 * через системное меню «Поделиться». Никакой генерации PDF: документ нужен
 * только для полноценной электронной книги в мастере сборки.
 */
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

    /**
     * Делится текстом через любое приложение: почта, мессенджер, соцсеть.
     * Если у рецепта есть фото — прикладываем его: мессенджеры покажут снимок
     * вместе с текстом, почта вложением.
     */
    fun shareText(context: Context, subject: String, text: String, photoPath: String? = null) {
        val photo = photoPath?.let(::File)?.takeIf { it.isFile }
        val intent = if (photo != null) {
            val uri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
            }.getOrNull()
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, text)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Отправить родне"))
    }
}
