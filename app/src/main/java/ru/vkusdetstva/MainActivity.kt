package ru.vkusdetstva

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.vkusdetstva.data.*
import ru.vkusdetstva.ui.*
import ru.vkusdetstva.util.BookPdf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Forest, background = Paper, surface = Cream)) {
                val app = LocalContext.current.applicationContext as Application
                val vm: FamilyViewModel = viewModel(factory = viewModelFactory { initializer { FamilyViewModel(app) } })
                FamilyApp(vm)
            }
        }
    }
}

@Composable
private fun FamilyApp(vm: FamilyViewModel) {
    val people by vm.people.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    var route by remember { mutableStateOf("home") }
    var selectedRecipe by remember { mutableLongStateOf(0L) }
    var selectedPerson by remember { mutableLongStateOf(0L) }
    var selectedMoment by remember { mutableLongStateOf(0L) }
    val selected = recipes.find { it.id == selectedRecipe }
    val person = people.find { it.id == selectedPerson }
    val moment = moments.find { it.id == selectedMoment }
    val go: (String) -> Unit = { route = it }
    val back: () -> Unit = { route = "home" }
    BackHandler(route != "home") { route = when (route) {
        "recipe-edit", "version" -> if (selectedRecipe != 0L) "recipe" else "home"
        "person-edit" -> if (selectedPerson != 0L) "person" else "home"
        "moment-edit" -> "moments"
        else -> "home"
    } }

    when (route) {
        "home" -> HomeScreen(recipes, people, { selectedRecipe = 0L; go("recipe-edit") }, go)
        "recipes" -> RecipeListScreen(recipes, people, back, { selectedRecipe = it; go("recipe") },
            { selectedRecipe = 0L; go("recipe-edit") })
        "recipe" -> if (selected != null) RecipeScreen(selected, people.find { it.id == selected.personId },
            versions.filter { it.recipeId == selected.id }, back,
            { go("recipe-edit") }, { go("version") }, { vm.save(selected.copy(timesCooked = selected.timesCooked + 1)) },
            { updated -> vm.save(updated) }, { vm.delete(selected, back) }) else HomeScreen(recipes, people, { selectedRecipe = 0L; go("recipe-edit") }, go)
        "recipe-edit" -> RecipeEditScreen(selected, people, back, { vm.save(it) { route = "recipes" } })
        "version" -> if (selected != null) VersionEditScreen(selected, { route = "recipe" }) { vm.save(it) { route = "recipe" } }
        "people" -> PeopleScreen(people, back, { selectedPerson = it; go("person") },
            { selectedPerson = 0L; go("person-edit") })
        "person" -> if (person != null) PersonScreen(person, recipes.filter { it.personId == person.id }, back,
            { go("person-edit") }, { selectedRecipe = it; go("recipe") }, { vm.delete(person, back) }) else HomeScreen(recipes, people, { selectedRecipe = 0L; go("recipe-edit") }, go)
        "person-edit" -> PersonEditScreen(person, back) { vm.save(it) { route = "people" } }
        "moments" -> MomentsScreen(moments, back, { selectedMoment = it; go("moment-edit") },
            { selectedMoment = 0L; go("moment-edit") })
        "moment-edit" -> MomentEditScreen(moment, back, { vm.save(it) { route = "moments" } },
            { if (moment != null) vm.delete(moment) { route = "moments" } })
        "pantry" -> PantryScreen(recipes, back) { selectedRecipe = it; go("recipe") }
        "book" -> BookScreen(recipes, people, versions, moments, back)
        "settings" -> SettingsScreen(back, people, recipes, versions, moments)
    }
}
