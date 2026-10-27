package xyz.dafmix.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.Title
import java.util.Locale

private val TR = Locale("tr", "TR")
private val TYPE_CHIPS = listOf("Tümü" to "", "Diziler" to "series", "Filmler" to "movie", "Animeler" to "anime")

private fun recent(): List<String> = Svc.prefs.getStr("recent_search").split("\n").filter { it.isNotBlank() }
private fun saveRecent(q: String) {
    val t = q.trim()
    if (t.length < 2) return
    val l = (listOf(t) + recent().filter { !it.equals(t, ignoreCase = true) }).take(8)
    Svc.prefs.putStr("recent_search", l.joinToString("\n"))
}

private fun score(t: Title, q: String): Int {
    val title = t.title.lowercase(TR)
    return when {
        title == q -> 100
        title.startsWith(q) -> 80
        title.split(' ', '-', ':').any { it.startsWith(q) } -> 60
        title.contains(q) -> 50
        t.tags.any { it.lowercase(TR).contains(q) } -> 30
        t.cast.lowercase(TR).contains(q) -> 20
        t.year == q -> 15
        else -> 0
    }
}

/* ------------------------------------------------------------------ Arama sekmesi */
@Composable
fun SearchTab(catalog: List<Title>, openTitle: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    var q by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var quality by rememberSaveable { mutableIntStateOf(0) } // 0 hepsi, 1 4K, 2 HD
    var tag by rememberSaveable { mutableStateOf("") }
    var recents by remember { mutableStateOf(recent()) }

    val playable = remember(catalog) { catalog.filter { it.playable } }
    val byType = remember(playable, type) { if (type == 0) playable else playable.filter { it.type == TYPE_CHIPS[type].second } }
    val tags = remember(byType) { byType.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key } }
    val filtered = remember(byType, quality, tag) {
        byType.filter { (quality == 0 || it.quality.equals(if (quality == 1) "4k" else "hd", true)) && (tag.isEmpty() || tag in it.tags) }
    }
    val query = q.trim().lowercase(TR)
    val results = remember(filtered, query) {
        if (query.isEmpty()) filtered.sortedBy { it.title.lowercase(TR) }
        else filtered.map { it to score(it, query) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
    val hasFilter = q.isNotBlank() || type != 0 || quality != 0 || tag.isNotEmpty()
    val popular = remember(playable) { playable.filter { it.top10 > 0 }.sortedBy { it.top10 }.ifEmpty { playable.sortedByDescending { it.id }.take(12) } }

    Column(Modifier.fillMaxSize()) {
        // Arama kutusu
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp).height(48.dp)
                .clip(RoundedCornerShape(12.dp)).background(Panel).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(22.dp))
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (q.isEmpty()) Text("İçerik, tür, oyuncu ara…", color = Muted, fontSize = 15.sp)
                BasicTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { saveRecent(q); recents = recent(); focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (q.isNotEmpty()) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).clickable { q = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, "Temizle", tint = Muted, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Filtreler
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(TYPE_CHIPS.size) { i -> FilterPill(TYPE_CHIPS[i].first, type == i) { type = i; tag = "" } }
            item { Box(Modifier.padding(horizontal = 2.dp).size(width = 1.dp, height = 34.dp).background(Color(0x33FFFFFF))) }
            item { FilterPill("4K", quality == 1) { quality = if (quality == 1) 0 else 1 } }
            item { FilterPill("HD", quality == 2) { quality = if (quality == 2) 0 else 2 } }
        }
        if (tags.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                items(tags) { g -> FilterPill(g, g == tag, small = true) { tag = if (tag == g) "" else g } }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!hasFilter) {
                if (recents.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Son aramalar", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Text(
                                    "Temizle", color = Muted, fontSize = 13.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { Svc.prefs.putStr("recent_search", ""); recents = emptyList() }.padding(6.dp),
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                                items(recents) { r -> FilterPill(r, false, small = true) { q = r } }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(3) }) { Text("Şu an popüler", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp)) }
                items(popular, key = { "p" + it.id }) { t -> ResultCell(t, openTitle) }
            } else {
                item(span = { GridItemSpan(3) }) {
                    Text("${results.size} sonuç", color = Muted, fontSize = 13.sp)
                }
                if (results.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(44.dp))
                            Text(
                                if (q.isNotBlank()) "“${q.trim()}” için sonuç bulunamadı" else "Bu filtrelere uyan içerik yok",
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
                            )
                            Text("Yazımı kontrol et ya da filtreleri gevşet.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                items(results, key = { it.id }) { t ->
                    ResultCell(t) { id -> if (q.isNotBlank()) { saveRecent(q); recents = recent() }; openTitle(id) }
                }
            }
        }
    }
}

@Composable
private fun ResultCell(t: Title, onOpen: (Int) -> Unit) {
    val cell = ((LocalConfiguration.current.screenWidthDp - 48) / 3).dp
    Column {
        PosterCard(t, onClick = { onOpen(t.id) }, width = cell, showTitle = false)
        Text(t.title, fontSize = 12.sp, maxLines = 2, color = Color(0xFFD0D0D0), lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun FilterPill(text: String, on: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text(
        text, fontSize = if (small) 12.sp else 14.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        color = if (on) Color.Black else Color.White,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (on) Color.White else if (small) Color(0xFF262628) else Color(0xFF2A2A2E))
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 6.dp else 8.dp),
    )
}
