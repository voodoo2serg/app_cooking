package ru.vkusdetstva.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class FamilyViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    val people = dao.people().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recipes = dao.recipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val versions = dao.versions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val moments = dao.moments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val author = dao.author().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
    fun saveAuthor(profile: AuthorProfile, done: () -> Unit = {}) = viewModelScope.launch {
        dao.upsertAuthor(profile.copy(id = 1L)); done()
    }
    fun delete(recipe: Recipe, done: () -> Unit = {}) = viewModelScope.launch {
        dao.deleteVersions(recipe.id)
        dao.deleteRecipe(recipe)
        recipe.photos.forEach { runCatching { File(it).delete() } }
        recipe.audioPath?.let { path -> runCatching { File(path).delete() } }
        done()
    }
    fun delete(person: Person, done: () -> Unit = {}) = viewModelScope.launch {
        dao.detachRecipes(person.id)
        dao.deletePerson(person)
        person.photos.forEach { runCatching { File(it).delete() } }
        done()
    }
    fun delete(moment: FamilyMoment, done: () -> Unit = {}) = viewModelScope.launch {
        dao.deleteMoment(moment)
        moment.photos.forEach { runCatching { File(it).delete() } }
        done()
    }
}
