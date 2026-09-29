package ru.vkusdetstva.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.vkusdetstva.R

@Composable
fun BrandHeader() {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_brand_mark), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Вкус детства", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Семейная книга рецептов", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun BrandSplash() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)) {
            Box(Modifier.size(124.dp).clip(RoundedCornerShape(28.dp))
                .background(BrandColors.Terracotta), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                    tint = BrandColors.Cream, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(24.dp))
            Text("Вкус детства", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("Семейные рецепты и истории", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun FamilyBottomBar(route: String, navigate: (String) -> Unit) {
    val items = listOf(
        Triple("home", "Главная", R.drawable.ic_brand_mark),
        Triple("search", "Поиск", R.drawable.ic_nav_search),
        Triple("recipe-edit", "Добавить", R.drawable.ic_nav_add),
        Triple("book", "Книга", R.drawable.ic_nav_book),
        Triple("settings", "Настройки", R.drawable.ic_nav_settings)
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp, modifier = Modifier.height(72.dp)) {
        items.forEach { (destination, label, icon) ->
            NavigationBarItem(selected = route == destination,
                onClick = { navigate(destination) },
                icon = { Icon(painterResource(icon), contentDescription = label,
                    modifier = Modifier.size(24.dp)) },
                label = { Text(label, maxLines = 1) },
                alwaysShowLabel = true)
        }
    }
}
