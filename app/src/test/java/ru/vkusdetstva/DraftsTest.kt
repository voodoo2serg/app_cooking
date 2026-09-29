package ru.vkusdetstva

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vkusdetstva.data.FamilyEvent
import ru.vkusdetstva.data.FamilyMoment
import ru.vkusdetstva.data.Person
import ru.vkusdetstva.data.Recipe
import ru.vkusdetstva.data.RecipeVersion
import ru.vkusdetstva.data.eventFromJson
import ru.vkusdetstva.data.momentFromJson
import ru.vkusdetstva.data.personFromJson
import ru.vkusdetstva.data.recipeFromJson
import ru.vkusdetstva.data.versionFromJson
import ru.vkusdetstva.data.toJson

/**
 * Черновики переживают перезапуск приложения только если сериализация
 * туда и обратно не теряет ни одного символа — проверяем это на JVM.
 */
class DraftsTest {

    @Test
    fun `recipe round trip keeps cyrillic dashes newlines photos and nulls`() {
        val recipe = Recipe(
            title = "Борщ по-бабушкиному",
            personId = 3L,
            story = "История — с тире\nи переносами строк",
            ingredients = "Свёкла — 2 шт\nКартофель — 3 шт",
            steps = "Сварить.\nНастоять.",
            notes = "Заметка «в кавычках» и ёлочках",
            photos = listOf("/data/photos/а.jpg", "/data/photos/b.jpg"),
            audioPath = "/data/audio/note.m4a"
        )
        val restored = recipeFromJson(JSONObject(recipe.toJson().toString()))
        assertEquals(recipe.title, restored.title)
        assertEquals(recipe.personId, restored.personId)
        assertEquals(recipe.story, restored.story)
        assertEquals(recipe.ingredients, restored.ingredients)
        assertEquals(recipe.steps, restored.steps)
        assertEquals(recipe.notes, restored.notes)
        assertEquals(recipe.photos, restored.photos)
        assertEquals(recipe.audioPath, restored.audioPath)
    }

    @Test
    fun `recipe without person audio and photos round trips to null and empty`() {
        val recipe = Recipe(title = "Оладьи")
        val restored = recipeFromJson(JSONObject(recipe.toJson().toString()))
        assertNull(restored.personId)
        assertNull(restored.audioPath)
        assertTrue(restored.photos.isEmpty())
        assertEquals("Оладьи", restored.title)
    }

    @Test
    fun `empty json gives blank recipe instead of crash`() {
        val restored = recipeFromJson(JSONObject("{}"))
        assertEquals("", restored.title)
        assertNull(restored.personId)
        assertTrue(restored.photos.isEmpty())
    }

    @Test
    fun `person round trip keeps story and photos`() {
        val person = Person(name = "Бабушка Валя", relation = "бабушка",
            years = "1938—2019", story = "Учила меня лепить\nпельмени",
            photos = listOf("/p/1.jpg"))
        val restored = personFromJson(JSONObject(person.toJson().toString()))
        assertEquals(person, restored) // id остаётся 0 у обоих — сериализация его не переносит
        assertEquals(person.name, restored.name)
        assertEquals(person.relation, restored.relation)
        assertEquals(person.years, restored.years)
        assertEquals(person.story, restored.story)
        assertEquals(person.photos, restored.photos)
    }

    @Test
    fun `moment round trip keeps people list`() {
        val moment = FamilyMoment(title = "Пасха 2024", story = "Собрались всей семьёй",
            people = "Бабушка, мама, я", photos = listOf("/m/1.jpg", "/m/2.jpg"))
        val restored = momentFromJson(JSONObject(moment.toJson().toString()))
        assertEquals(moment.title, restored.title)
        assertEquals(moment.story, restored.story)
        assertEquals(moment.people, restored.people)
        assertEquals(moment.photos, restored.photos)
    }

    @Test
    fun `event round trip keeps recipe ids`() {
        val event = FamilyEvent(title = "Новый год", story = "Оливье и мандарины",
            recipeIds = listOf(1L, 5L, 9L), photos = listOf("/e/1.jpg"))
        val restored = eventFromJson(JSONObject(event.toJson().toString()))
        assertEquals(event.title, restored.title)
        assertEquals(event.story, restored.story)
        assertEquals(event.recipeIds, restored.recipeIds)
        assertEquals(event.photos, restored.photos)
    }

    @Test
    fun `version round trip keeps change note`() {
        val version = RecipeVersion(recipeId = 7L, personName = "Папа",
            change = "Меньше соли", note = "Все попросили добавки",
            photos = listOf("/v/1.jpg"))
        val restored = versionFromJson(JSONObject(version.toJson().toString()))
        assertEquals(0L, restored.recipeId) // recipeId подставит экран из своего рецепта
        assertEquals(version.personName, restored.personName)
        assertEquals(version.change, restored.change)
        assertEquals(version.note, restored.note)
        assertEquals(version.photos, restored.photos)
    }
}
