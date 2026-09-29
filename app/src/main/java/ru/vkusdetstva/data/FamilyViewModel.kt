package ru.vkusdetstva.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import androidx.room.withTransaction
import ru.vkusdetstva.util.Basket

class FamilyViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    val people = dao.people().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recipes = dao.recipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val versions = dao.versions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val moments = dao.moments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val events = dao.events().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val author = dao.author().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val basket = dao.basket().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        migrateEventsToTags()
    }

    /**
     * Раньше событие было отдельной сущностью со списком блюд. Теперь событие —
     * тег у блюда или фотоистории. Один раз переносим старые данные: у блюд
     * появляется тег, фотографии общего стола становятся фотоисторией с тегом.
     */
    private fun migrateEventsToTags() {
        val prefs = getApplication<Application>().getSharedPreferences("settings", 0)
        if (prefs.getBoolean("event_tags_migrated", false)) return
        viewModelScope.launch {
            runCatching {
                dao.eventsSnapshot().forEach { event ->
                    val tag = event.title.trim()
                    if (tag.isBlank()) return@forEach
                    dao.recipesSnapshot()
                        .filter { it.id in event.recipeIds && it.eventTag.isBlank() }
                        .forEach { dao.updateRecipe(it.copy(eventTag = tag)) }
                    if (event.photos.isNotEmpty() || event.story.isNotBlank()) {
                        dao.addMoment(FamilyMoment(title = tag, story = event.story,
                            photos = event.photos, eventTag = tag, createdAt = event.createdAt))
                    }
                }
            }
            prefs.edit().putBoolean("event_tags_migrated", true).apply()
        }
    }

    fun save(person: Person, done: () -> Unit = {}) = viewModelScope.launch {
        if (person.id == 0L) dao.addPerson(person) else dao.updatePerson(person)
        done()
    }
    fun save(recipe: Recipe, done: () -> Unit = {}) = viewModelScope.launch {
        if (recipe.id == 0L) dao.addRecipe(recipe) else dao.updateRecipe(recipe)
        done()
    }
    fun save(version: RecipeVersion, done: () -> Unit = {}) = viewModelScope.launch { dao.addVersion(version); done() }
    fun save(moment: FamilyMoment, done: () -> Unit = {}) = viewModelScope.launch {
        if (moment.id == 0L) dao.addMoment(moment) else dao.updateMoment(moment)
        done()
    }
    fun save(event: FamilyEvent, done: () -> Unit = {}) = viewModelScope.launch {
        if (event.id == 0L) dao.addEvent(event) else dao.updateEvent(event)
        done()
    }
    fun saveAuthor(profile: AuthorProfile, done: () -> Unit = {}) = viewModelScope.launch {
        dao.upsertAuthor(profile.copy(id = 1L)); done()
    }
    fun delete(recipe: Recipe, done: () -> Unit = {}) = viewModelScope.launch {
        AppDatabase.get(getApplication()).withTransaction {
            dao.eventsSnapshot().filter { recipe.id in it.recipeIds }.forEach { event ->
                dao.updateEvent(event.copy(recipeIds = event.recipeIds - recipe.id))
            }
            dao.deleteVersions(recipe.id)
            dao.deleteRecipe(recipe)
        }
        recipe.photos.forEach { runCatching { File(it).delete() } }
        recipe.audioPath?.let { path -> runCatching { File(path).delete() } }
        done()
    }
    fun delete(person: Person, done: () -> Unit = {}) = viewModelScope.launch {

        AppDatabase.get(getApplication()).withTransaction {
            dao.unlinkPerson(person.id)
            dao.deletePerson(person)
        }
        person.photos.forEach { runCatching { File(it).delete() } }
        done()
    }
    /** Лайк/снятие лайка: передаём дельту (+1 или −1), счётчик не уходит ниже нуля. */
    fun likeRecipe(id: Long, delta: Int) = viewModelScope.launch { dao.likeRecipe(id, delta) }
    fun likeMoment(id: Long, delta: Int) = viewModelScope.launch { dao.likeMoment(id, delta) }
    fun delete(moment: FamilyMoment, done: () -> Unit = {}) = viewModelScope.launch {
        dao.deleteMoment(moment)
        moment.photos.forEach { runCatching { File(it).delete() } }
        done()
    }
    fun delete(event: FamilyEvent, done: () -> Unit = {}) = viewModelScope.launch {
        dao.deleteEvent(event)
        event.photos.forEach { runCatching { File(it).delete() } }
        done()
    }

    // ---------- Корзина ----------

    /** Кладёт ингредиенты выбранных рецептов; то, что уже в корзине, не дублируется. */
    fun addToBasket(recipes: List<Recipe>) = viewModelScope.launch {
        val fresh = Basket.itemsForRecipes(recipes, basket.value)
        if (fresh.isNotEmpty()) dao.addBasketItems(fresh)
    }

    /** Кладёт всё, что отмечено тегом события: блюда тега и их ингредиенты. */
    fun addTagToBasket(tag: String) = viewModelScope.launch {
        val tagged = dao.recipesSnapshot().filter { it.eventTag == tag }
        val fresh = Basket.itemsForRecipes(tagged, basket.value)
        if (fresh.isNotEmpty()) dao.addBasketItems(fresh)
    }

    fun addBasketItem(text: String) = viewModelScope.launch {
        val name = text.trim()
        if (name.isBlank()) return@launch
        if (basket.value.any { it.text.trim().equals(name, ignoreCase = true) }) return@launch
        dao.addBasketItem(BasketItem(text = name))
    }

    fun toggleBasketItem(item: BasketItem) = viewModelScope.launch {
        dao.updateBasketItem(item.copy(checked = !item.checked))
    }
    fun removeBasketItem(item: BasketItem) = viewModelScope.launch { dao.deleteBasketItem(item.id) }
    fun clearCheckedBasket() = viewModelScope.launch { dao.clearCheckedBasket() }
    fun clearBasket() = viewModelScope.launch { dao.clearBasket() }
}
