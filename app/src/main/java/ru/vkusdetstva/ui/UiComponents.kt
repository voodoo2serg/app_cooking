package ru.vkusdetstva.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.vkusdetstva.util.LocalMedia
import java.io.File

val Paper = androidx.compose.ui.graphics.Color(0xFFF9F5ED)
val Forest = androidx.compose.ui.graphics.Color(0xFF324E3F)
val Ink = androidx.compose.ui.graphics.Color(0xFF29251E)
val Cream = androidx.compose.ui.graphics.Color(0xFFFFFDF8)

@Composable
fun Page(title: String, back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) TextButton(onClick = back) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            Text("ВКУС ДЕТСТВА", style = MaterialTheme.typography.labelSmall, color = Forest)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif), color = Ink)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), content = content)
    }
}

@Composable
fun Action(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(text) }
}

@Composable
fun TextBox(value: String, onChange: (String) -> Unit, label: String, lines: Int = 1) {
    OutlinedTextField(value, onChange, label = { Text(label) }, minLines = lines,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
fun PhotoEditor(paths: List<String>, onChange: (List<String>) -> Unit, label: String) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        val copied = uris.mapNotNull { runCatching { LocalMedia.copy(context, it) }.getOrNull() }
        if (copied.isNotEmpty()) onChange(paths + copied)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) onChange(paths + LocalMedia.save(context, bitmap))
    }
    Text(label, style = MaterialTheme.typography.titleSmall)
    if (paths.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(136.dp)) {
            items(paths) { path ->
                Box {
                    AsyncImage(File(path), contentDescription = label, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(136.dp).background(Cream, RoundedCornerShape(12.dp)))
                    TextButton(onClick = { onChange(paths.filterNot { it == path }) },
                        modifier = Modifier.align(Alignment.TopEnd)) { Text("✕") }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("+ Фото") }
        OutlinedButton(onClick = { camera.launch(null) }) { Text("Снять") }
    }
}

@Composable
fun PhotoCarousel(paths: List<String>, label: String) {
    if (paths.isEmpty()) return
    var index by remember(paths) { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(paths.indices)
    AsyncImage(File(paths[safeIndex]), contentDescription = "$label ${safeIndex + 1}",
        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(205.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { index = (safeIndex - 1 + paths.size) % paths.size }) { Text("←") }
        Text("${safeIndex + 1} / ${paths.size}")
        TextButton(onClick = { index = (safeIndex + 1) % paths.size }) { Text("→") }
    }
}

@Composable
fun Section(title: String) {
    Spacer(Modifier.height(13.dp))
    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif), color = Forest)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun ListTile(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Cream)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Medium, color = Ink)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
