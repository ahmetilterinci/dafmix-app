package xyz.dafmix.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.Seri
import xyz.dafmix.app.data.Title

/** Bir serinin (koleksiyonun) sayfası: banner, poster, açıklama ve sıralı içerikler */
@Composable
fun SeriesScreen(id: Int, back: () -> Unit, openTitle: (Int) -> Unit) {
    val catalog = remember { Svc.api.cachedCatalog() }
    var seri by remember { mutableStateOf<Seri?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        try { seri = Svc.api.collections().firstOrNull { it.id == id }; if (seri == null) failed = true } catch (e: Exception) { failed = true }
    }
    val titles: List<Title> = remember(seri, catalog) {
        val s = seri
        if (s == null) emptyList() else s.items.mapNotNull { i -> catalog.firstOrNull { it.id == i } }
    }
    val cell = ((LocalConfiguration.current.screenWidthDp - 32 - 16) / 3).dp

    Box(Modifier.fillMaxSize().background(Bg)) {
        val s = seri
        if (s == null) {
            if (failed) Text("Seri bulunamadı", color = Muted, modifier = Modifier.align(Alignment.Center))
            else CircularProgressIndicator(Modifier.align(Alignment.Center), color = Red)
        } else {
            val bannerSrc = s.banner.ifBlank { titles.firstOrNull()?.thumb.orEmpty() }
            val posterSrc = s.poster.ifBlank { titles.firstOrNull()?.poster.orEmpty() }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.9f)) {
                        if (bannerSrc.isNotBlank()) AsyncImage(Svc.api.img(bannerSrc, 1080), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    0f to Color(0x99000000), 0.25f to Color.Transparent, 0.45f to Color(0x33141414),
                                    0.72f to Color(0xCC141414), 0.9f to Color(0xF2141414), 1f to Bg,
                                )
                            )
                        )
                        Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                            if (posterSrc.isNotBlank()) {
                                AsyncImage(
                                    Svc.api.img(posterSrc, 480), null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(112.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Panel2),
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = if (posterSrc.isNotBlank()) 14.dp else 0.dp)) {
                                Text(s.name, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text("${titles.size} içerik", color = Color(0xFFD0D0D0), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                if (s.desc.isNotBlank()) {
                    item { Text(s.desc, color = Color(0xFFD8D8D8), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) }
                }
                item { Text("Serideki içerikler", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) }
                items(titles.chunked(3)) { chunk ->
                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in chunk) PosterCard(t, onClick = { openTitle(t.id) }, width = cell, showTitle = true)
                    }
                }
            }
        }
        Box(
            Modifier.padding(start = 8.dp, top = 10.dp).size(40.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClick = back).align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White) }
    }
}
