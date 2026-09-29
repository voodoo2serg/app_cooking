package ru.vkusdetstva.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FamilyViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    val people = dao.people().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recipes = dao.recipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val versions = dao.versions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val moments = dao.moments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    fun delete(recipe: Recipe, done: () -> Unit = {}) = viewModelScope.launch {
        dao.deleteVersions(recipe.id); dao.deleteRecipe(recipe); done()
    }
    fun delete(person: Person, done: () -> Unit = {}) = viewModelScope.launch {
        dao.deletePerson(person); done()
    }
    fun delete(moment: FamilyMoment, done: () -> Unit = {}) = viewModelScope.launch {
        dao.deleteMoment(moment); done()
    }
}
