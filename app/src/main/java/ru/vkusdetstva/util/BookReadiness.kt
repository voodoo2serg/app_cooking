package ru.vkusdetstva.util

import ru.vkusdetstva.data.AuthorProfile
import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe

data class BookReadiness(
    val percent: Int,
    val completed: Int,
    val total: Int,
    val hints: List<String>
)

object BookReadinessCalculator {
    fun calculate(
        recipes: List<Recipe>,
        people: List<Person>,
        moments: List<FamilyMoment>,
        author: AuthorProfile?
    ): BookReadiness {
        if (recipes.isEmpty()) return BookReadiness(
            percent = 0, completed = 0, total = 5,
            hints = listOf("Добавьте первый семейный рецепт")
        )

        var completed = 0
        var total = 0
        val hints = mutableListOf<String>()

        fun check(ok: Boolean, hint: String) {
            total++
            if (ok) completed++ else hints += hint
        }

        check(recipes.size >= 5, "Добавьте ещё рецепты: хорошая первая книга начинается примерно с пяти")
        check(recipes.any { it.photos.isNotEmpty() }, "Добавьте хотя бы одну фотографию блюда")
        check(recipes.count { it.story.isNotBlank() } >= (recipes.size + 1) / 2,
            "Запишите истории хотя бы для половины рецептов")
        check(people.isNotEmpty() || !author?.name.isNullOrBlank(),
            "Подпишите автора книги или добавьте близкого человека")
        check(
            recipes.count { it.personId != null || !author?.name.isNullOrBlank() } >= (recipes.size * 3 + 3) / 4,
            "Укажите, от кого пришли основные рецепты"
        )
        if (moments.isNotEmpty()) {
            check(moments.any { it.photos.isNotEmpty() }, "Добавьте фото семейного застолья")
        }

        val percent = if (total == 0) 0 else ((completed * 100f) / total).toInt().coerceIn(0, 100)
        return BookReadiness(percent, completed, total, hints.take(3))
    }
}
