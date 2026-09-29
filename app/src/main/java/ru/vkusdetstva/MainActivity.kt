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
import androidx.compose.runtime.saveable.Saver
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
import org.json.JSONObject

/** Черновик импорта переживает пересоздание Activity (поворот, звонок) через Bundle. */
val RecipeDraftSaver = Saver<Recipe?, String>(
    save = { it?.toJson()?.toString().orEmpty() },
    restore = { if (it.isEmpty()) null else recipeFromJson(JSONObject(it)) })

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("settings", MODE_PRIVATE) }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", systemDark)) }
            // Брендовый сплеш — только при настоящем запуске; при пересоздании Activity
            // (поворот, звонок) сохраняемое состояние сразу даёт false без паузы.
            var splash by rememberSaveable { mutableStateOf(true) }
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
    val context = LocalContext.current
    val people by vm.people.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val author by vm.author.collectAsStateWithLifecycle()
    val basket by vm.basket.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("home") }
    var selectedRecipe by rememberSaveable { mutableLongStateOf(0L) }
    var selectedPerson by rememberSaveable { mutableLongStateOf(0L) }
    var selectedMoment by rememberSaveable { mutableLongStateOf(0L) }
    var pantryIngredient by rememberSaveable { mutableStateOf<String?>(null) }
    var importDraft by rememberSaveable(stateSaver = RecipeDraftSaver) { mutableStateOf<Recipe?>(null) }
    val selected = recipes.find { it.id == selectedRecipe }
    val person = people.find { it.id == selectedPerson }
    val moment = moments.find { it.id == selectedMoment }
    val go: (String) -> Unit = { route = it }
    val back: () -> Unit = { route = "home" }
    val knownTags = (recipes.map { it.eventTag } + moments.map { it.eventTag }).filter { it.isNotBlank() }.distinct().sorted()
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
        topBar = { if (route == "home") BrandHeader(author) { go("author") } },
        bottomBar = { FamilyBottomBar(route) { destination ->
            if (destination == "recipe-edit") { selectedRecipe = 0L; importDraft = null }
            route = destination
        } }) { innerPadding ->
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(innerPadding)) {
    when (route) {
        "home" -> HomeScreen(recipes, people, { selectedRecipe = 0L; importDraft = null; go("recipe-edit") }, go,
            { selectedRecipe = it; go("recipe") }, author?.name.orEmpty())
        "recipes" -> RecipeListScreen(recipes, people, back, { selectedRecipe = it; go("recipe") },
            { selectedRecipe = 0L; importDraft = null; go("recipe-edit") }, author?.name.orEmpty())
        "search" -> SearchScreen(recipes, people, back,
            { selectedRecipe = it; go("recipe") }, { selectedPerson = it; go("person") },
            { pantryIngredient = it; go("pantry") }, author?.name.orEmpty())
        "recipe" -> if (selected != null) RecipeScreen(selected, people.find { it.id == selected.personId }, author,
            versions.filter { it.recipeId == selected.id }, back,
            { go("recipe-edit") }, { go("version") }, { vm.save(selected.copy(timesCooked = selected.timesCooked + 1)) },
            { updated -> vm.save(updated) }, { vm.delete(selected, back) },
            { vm.addToBasket(listOf(selected)) }) else HomeScreen(recipes, people,
                { selectedRecipe = 0L; importDraft = null; go("recipe-edit") }, go, { selectedRecipe = it; go("recipe") })
        "recipe-edit" -> RecipeEditScreen(selected ?: importDraft, people, back,
            { vm.save(it) { route = "recipes" }; importDraft = null }, knownTags)
        "version" -> if (selected != null) VersionEditScreen(selected, { route = "recipe" }) { vm.save(it) { route = "recipe" } }
        "people" -> PeopleScreen(people, back, { selectedPerson = it; go("person") },
            { selectedPerson = 0L; go("person-edit") })
        "person" -> if (person != null) PersonScreen(person, recipes.filter { it.personId == person.id }, back,
            { go("person-edit") }, { selectedRecipe = it; go("recipe") }, { vm.delete(person, back) },
            versions, author) else HomeScreen(recipes, people,
                { selectedRecipe = 0L; importDraft = null; go("recipe-edit") }, go, { selectedRecipe = it; go("recipe") })
        "person-edit" -> PersonEditScreen(person, back) { vm.save(it) { route = "people" } }
        "moments" -> MomentsScreen(moments, back, { selectedMoment = it; go("moment-edit") },
            { selectedMoment = 0L; go("moment-edit") })
        "moment-edit" -> MomentEditScreen(moment, back, { vm.save(it) { route = "moments" } },
            { if (moment != null) vm.delete(moment) { route = "moments" } }, knownTags)
        "pantry" -> PantryScreen(recipes, back, { selectedRecipe = it; go("recipe") }, pantryIngredient)

        "basket" -> BasketScreen(basket, recipes, back,
            { vm.toggleBasketItem(it) }, { vm.removeBasketItem(it) },
            { vm.addToBasket(it) }, { vm.addBasketItem(it) },
            { vm.clearCheckedBasket() }, { vm.clearBasket() })
        "events" -> EventsScreen(recipes, moments, back,
            { selectedRecipe = it; go("recipe") }, { selectedMoment = it; go("moment-edit") },
            { vm.addTagToBasket(it) })
        "feed" -> FamilyFeedScreen(recipes, moments, people, author, back,
            { selectedRecipe = it; go("recipe") }, { id, delta -> vm.likeRecipe(id, delta) },
            { id, delta -> vm.likeMoment(id, delta) })
        "book" -> BookWizard(people, recipes, versions, moments, author?.name.orEmpty(), back)
        "import" -> ImportScreen({ go("home") }, { go("settings") }) { draft, photoPaths ->
            importDraft = Recipe(title = draft.title, story = draft.story,
                ingredients = draft.ingredients.joinToString("\n"),
                steps = draft.steps.joinToString("\n"),
                notes = draft.notes, photos = photoPaths)
            selectedRecipe = 0L
            // Новое распознавание всегда важнее старого черновика формы.
            Drafts.clear(context, "recipe-import")
            go("recipe-edit")
        }
        "author" -> {
            val profile = author
            if (profile != null) AuthorScreen(profile, recipes.filter { it.personId == null },
                { go("settings") }, { go("author-edit") })
            else AuthorEditScreen(null, { go("settings") }) { vm.saveAuthor(it) { route = "author" } }
        }
        "author-edit" -> AuthorEditScreen(author, { route = if (author != null) "author" else "settings" }) {
            vm.saveAuthor(it) { route = "author" }
        }
        "settings" -> SettingsScreen(back, people, recipes, versions, moments, events, author, darkTheme, setDarkTheme,
            { go("author") }, { go("import") })
    }
    }
    }
}
