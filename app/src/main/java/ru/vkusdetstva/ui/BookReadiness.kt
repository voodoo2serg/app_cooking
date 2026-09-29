package ru.vkusdetstva.ui

import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe

data class BookReadinessResult(val percent: Int, val estimatedPages: Int, val hints: List<String>)

object BookReadiness {
    fun calculate(recipes: List<Recipe>, people: List<Person>, authorName: String): BookReadinessResult {
        if (recipes.isEmpty()) return BookReadinessResult(5, 8,
            listOf("Добавьте первый семейный рецепт — книга начнёт собираться сразу."))
        val photoReady = recipes.count { it.photos.isNotEmpty() }
        val storyReady = recipes.count { it.story.isNotBlank() }
        val cookingReady = recipes.count { it.ingredients.isNotBlank() && it.steps.isNotBlank() }
        val attributed = recipes.count { it.personId != null || authorName.isNotBlank() }
        var score = 20 + recipes.size.coerceAtMost(12) * 2
        score += photoReady * 18 / recipes.size
        score += storyReady * 14 / recipes.size
        score += cookingReady * 14 / recipes.size
        score += attributed * 6 / recipes.size
        if (people.isNotEmpty()) score += 4
        if (authorName.isNotBlank()) score += 4
        val hints = buildList {
            val noPhoto = recipes.size - photoReady
            val noStory = recipes.size - storyReady
            val noCooking = recipes.size - cookingReady
            if (noPhoto > 0) add("Добавьте фото ещё к $noPhoto рецептам — книга станет заметно живее.")
            if (noStory > 0) add("У $noStory рецептов пока нет семейной истории.")
            if (noCooking > 0) add("Заполните ингредиенты и шаги ещё у $noCooking рецептов.")
            if (authorName.isBlank()) add("Заполните профиль автора — неподписанные рецепты получат голос хранителя книги.")
            if (isEmpty()) add("Материал уже выглядит как полноценная семейная книга.")
        }
        return BookReadinessResult(score.coerceIn(5,100), 8 + recipes.size * 2 + people.size + photoReady / 2, hints)
    }
}
