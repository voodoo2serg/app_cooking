package ru.vkusdetstva

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.vkusdetstva.data.*
import ru.vkusdetstva.ui.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("settings", MODE_PRIVATE) }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", systemDark)) }
            var splash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) { delay(900); splash = false }
            VkusTheme(darkTheme = darkTheme) {
                val scheme = MaterialTheme.colorScheme
                SideEffect {
                    window.decorView.setBackgroundColor(scheme.background.toArgb())
                    window.statusBarColor = scheme.background.toArgb()
                    window.navigationBarColor = scheme.background.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !darkTheme
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightNavigationBars = !darkTheme
                }
                val app = LocalContext.current.applicationContext as Application
                val vm: FamilyViewModel = viewModel(factory = viewModelFactory { initializer { FamilyViewModel(app) } })
                if (splash) BrandSplash() else FamilyApp(vm, darkTheme) {
                    darkTheme = it
                    prefs.edit().putBoolean("dark_theme", it).apply()
                }
            }
        }
    }
}

@Composable
private fun FamilyApp(vm: FamilyViewModel, darkTheme: Boolean, setDarkTheme: (Boolean) -> Unit) {
    val people by vm.people.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    val author by vm.author.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("home") }
    var selectedRecipe by rememberSaveable { mutableLongStateOf(0L) }
    var selectedPerson by rememberSaveable { mutableLongStateOf(0L) }
    var selectedMoment by rememberSaveable { mutableLongStateOf(0L) }
    var pantryIngredient by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = recipes.find { it.id == selectedRecipe }
    val person = people.find { it.id == selectedPerson }
    val moment = moments.find { it.id == selectedMoment }
    val go: (String) -> Unit = { route = it }
    val back: () -> Unit = { route = "home" }
    BackHandler(route != "home") { route = when (route) {
        "recipe-edit", "version" -> if (selectedRecipe != 0L) "recipe" else "home"
        "person-edit" -> if (selectedPerson != 0L) "person" else "home"
        "moment-edit" -> "moments"
        "author-edit" -> if (author != null) "author" else "settings"
        "author" -> "settings"
        else -> "home"
    } }

    Scaffold(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { if (route == "home") BrandHeader() },
        bottomBar = { FamilyBottomBar(route) { destination ->
            if (destination == "recipe-edit") selectedRecipe = 0L
            route = destination
        } }) { innerPadding ->
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(innerPadding)) {
    when (route) {
        "home" -> HomeScreen(recipes, people, { selectedRecipe = 0L; go("recipe-edit") }, go,
            { selectedRecipe = it; go("recipe") }, author?.name.orEmpty())
        "recipes" -> RecipeListScreen(recipes, people, back, { selectedRecipe = it; go("recipe") },
            { selectedRecipe = 0L; go("recipe-edit") }, author?.name.orEmpty())
        "search" -> SearchScreen(recipes, people, back,
            { selectedRecipe = it; go("recipe") }, { selectedPerson = it; go("person") },
            { pantryIngredient = it; go("pantry") }, author?.name.orEmpty())
        "recipe" -> if (selected != null) RecipeScreen(selected, people.find { it.id == selected.personId }, author,
            versions.filter { it.recipeId == selected.id }, back,
            { go("recipe-edit") }, { go("version") }, { vm.save(selected.copy(timesCooked = selected.timesCooked + 1)) },
            { updated -> vm.save(updated) }, { vm.delete(selected, back) }) else HomeScreen(recipes, people,
                { selectedRecipe = 0L; go("recipe-edit") }, go, { selectedRecipe = it; go("recipe") })
        "recipe-edit" -> RecipeEditScreen(selected, people, back, { vm.save(it) { route = "recipes" } })
        "version" -> if (selected != null) VersionEditScreen(selected, { route = "recipe" }) { vm.save(it) { route = "recipe" } }
        "people" -> PeopleScreen(people, back, { selectedPerson = it; go("person") },
            { selectedPerson = 0L; go("person-edit") })
        "person" -> if (person != null) PersonScreen(person, recipes.filter { it.personId == person.id }, back,
            { go("person-edit") }, { selectedRecipe = it; go("recipe") }, { vm.delete(person, back) }) else HomeScreen(recipes, people,
                { selectedRecipe = 0L; go("recipe-edit") }, go, { selectedRecipe = it; go("recipe") })
        "person-edit" -> PersonEditScreen(person, back) { vm.save(it) { route = "people" } }
        "moments" -> MomentsScreen(moments, back, { selectedMoment = it; go("moment-edit") },
            { selectedMoment = 0L; go("moment-edit") })
        "moment-edit" -> MomentEditScreen(moment, back, { vm.save(it) { route = "moments" } },
            { if (moment != null) vm.delete(moment) { route = "moments" } })
        "pantry" -> PantryScreen(recipes, back, { selectedRecipe = it; go("recipe") }, pantryIngredient)
        "book" -> BookWizard(people, recipes, versions, moments, author?.name.orEmpty(), back)
        "author" -> {
            val profile = author
            if (profile != null) AuthorScreen(profile, recipes.filter { it.personId == null },
                { go("settings") }, { go("author-edit") })
            else AuthorEditScreen(null, { go("settings") }) { vm.saveAuthor(it) { route = "author" } }
        }
        "author-edit" -> AuthorEditScreen(author, { route = if (author != null) "author" else "settings" }) {
            vm.saveAuthor(it) { route = "author" }
        }
        "settings" -> SettingsScreen(back, people, recipes, versions, moments, author, darkTheme, setDarkTheme,
            { go("author") })
    }
    }
    }
}
