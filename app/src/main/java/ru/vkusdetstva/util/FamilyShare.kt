package ru.vkusdetstva.util

import android.content.Context
import android.content.Intent
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
}
