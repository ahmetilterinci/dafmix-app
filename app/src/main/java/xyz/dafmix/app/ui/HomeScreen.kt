@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.dafmix.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.dafmix.app.data.Favs
import xyz.dafmix.app.data.Notif
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.Title
import kotlin.random.Random

private val PAGES = listOf("Ana Sayfa" to "", "Diziler" to "series", "Filmler" to "movie", "Animeler" to "anime")
private val TYPE_LABEL = mapOf("series" to "Dizi", "movie" to "Film", "anime" to "Anime")

/** Sitedeki DAFMIX logosu (kırmızı dış çizgili form) */
val LogoIcon: ImageVector = ImageVector.Builder(
    name = "DafmixLogo", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 44f, viewportHeight = 44f
).addPath(
    pathData = addPathNodes(
        "M29.76 18.72 c0 7.28-3.92 13.6-9.84 16.96 c-2.88 1.68-6.24 2.64-9.84 2.64 c-3.6 0-6.88-0.96-9.76-2.64 " +
            "c0-7.28 3.92-13.52 9.84-16.96 c2.88-1.68 6.24-2.64 9.76-2.64 S26.88 17.04 29.76 18.72 " +
            "c5.84 3.36 9.76 9.68 9.84 16.96 c-2.88 1.68-6.24 2.64-9.76 2.64 c-3.6 0-6.88-0.96-9.84-2.64 " +
            "c-5.84-3.36-9.76-9.68-9.76-16.96 c0-7.28 3.92-13.6 9.76-16.96 C25.84 5.12 29.76 11.44 29.76 18.72z"
    ),
    fill = null,
    stroke = SolidColor(Red),
    strokeLineWidth = 4f,
    strokeLineCap = StrokeCap.Round,
).build()

private data class HomeRow(val name: String, val items: List<Title>, val kind: Int) // 0 afiş, 1 devam, 2 top10

private fun latestFraction(titleId: Int): Float? {
    val p = Svc.prefs.allProgress().filter { it.contentId == "t$titleId" || it.contentId.startsWith("e${titleId}_") }
        .maxByOrNull { it.ts }
    return p?.fraction
}

private fun buildRows(data: List<Title>, page: Int): List<HomeRow> {
    val rnd = Random(System.nanoTime())
    val out = ArrayList<HomeRow>()

    // 1) İzlemeye devam et (en yeni en solda)
    val seen = LinkedHashSet<Int>()
    for (p in Svc.prefs.allProgress().sortedByDescending { it.ts }) {
        if (p.fraction >= 0.95f || p.posMs < 10_000) continue
        val id = if (p.contentId.startsWith("t")) p.contentId.drop(1).toIntOrNull()
        else p.contentId.drop(1).substringBefore('_').toIntOrNull()
        if (id != null) seen.add(id)
    }
    val cont = seen.mapNotNull { id -> data.firstOrNull { it.id == id } }
    if (cont.isNotEmpty()) out.add(HomeRow("İzlemeye Devam Et", cont, 1))

    // 2) Diğer satırlar (listeler + etiketler), karışık
    val cand = ArrayList<HomeRow>()
    cand.add(HomeRow("Yeni Eklenenler", data.sortedByDescending { it.id }.take(24), 0))
    for (l in data.flatMap { it.lists }.distinct()) {
        val items = data.filter { l in it.lists }
        if (items.size >= 3) cand.add(HomeRow(l, items.shuffled(rnd).take(24), 0))
    }
    val tagCounts = data.flatMap { it.tags }.groupingBy { it }.eachCount()
    for ((g, n) in tagCounts) {
        if (n < 4) continue
        cand.add(HomeRow("Popüler $g Yapımları", data.filter { g in it.tags }.shuffled(rnd).take(24), 0))
    }
    val first = cand.first()
    val rest = ArrayList<HomeRow>()
    rest.add(first)
    rest.addAll(cand.drop(1).shuffled(rnd))
    val body = rest.distinctBy { it.name }.take(14)

    // 3) Top 10 satırları (siteyle aynı: 3 satırda bir araya girer)
    val tops = ArrayList<HomeRow>()
    val types = if (page == 0) listOf("series", "movie", "anime") else listOf(PAGES[page].second)
    for (ty in types) {
        val items = data.filter { it.type == ty && it.top10 > 0 }.sortedBy { it.top10 }
        if (items.isNotEmpty()) tops.add(HomeRow("Türkiye'de Bugün Top 10 ${TYPE_LABEL[ty]} Listesi", items, 2))
    }
    val merged = ArrayList<HomeRow>(body)
    var at = 1
    for (tp in tops) {
        merged.add(minOf(at, merged.size), tp)
        at = minOf(at, merged.size - 1) + 3
    }
    out.addAll(merged)
    return out
}

/* ------------------------------------------------------------------ Ana sayfa */
@Composable
fun HomeTab(
    catalog: List<Title>, online: Boolean, openTitle: (Int) -> Unit,
    goDownloads: () -> Unit, openSearch: () -> Unit, openProfile: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var tag by rememberSaveable { mutableStateOf("") }

    val playable = remember(catalog) { catalog.filter { it.playable } }
    val data = remember(playable, page) { if (page == 0) playable else playable.filter { it.type == PAGES[page].second } }
    val tags = remember(data) {
        data.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key }
    }
    val heroes = remember(data) {
        val withPoster = data.filter { it.topThumb.isNotBlank() }
        (if (withPoster.size >= 3) withPoster else data).shuffled().take(6)
    }
    val rows = remember(data, page) { buildRows(data, page) }
    val favs by Favs.ids.collectAsState()
    val favItems = remember(data, favs) { data.filter { it.id in favs } }
    val shownRows = remember(rows, favItems) {
        val l = rows.toMutableList()
        if (favItems.isNotEmpty()) l.add(if (l.firstOrNull()?.kind == 1) 1 else 0, HomeRow("Listem", favItems, 0))
        l
    }

    // Bildirimler
    val scope = rememberCoroutineScope()
    var notifs by remember { mutableStateOf<List<Notif>>(emptyList()) }
    var unread by remember { mutableIntStateOf(0) }
    var showNotifs by remember { mutableStateOf(false) }
    LaunchedEffect(online) {
        if (online) {
            try {
                val r = Svc.api.notifications()
                unread = r.first; notifs = r.second
            } catch (e: Exception) { }
        }
    }
    fun openLink(n: Notif) {
        val seg = n.link.split("/").filter { it.isNotBlank() }
        val pid = seg.getOrNull(1)
        val id = catalog.firstOrNull { it.pid == pid }?.id
        scope.launch { try { Svc.api.markRead(n.id) } catch (e: Exception) { } }
        notifs = notifs.map { if (it.id == n.id) it.copy(read = true) else it }
        unread = notifs.count { !it.read }
        showNotifs = false
        if (id != null) openTitle(id)
    }

    var heroIdx by remember(heroes) { mutableIntStateOf(0) }
    LaunchedEffect(heroes) {
        while (heroes.size > 1) {
            delay(8000)
            heroIdx = (heroIdx + 1) % heroes.size
        }
    }

    val listState = rememberLazyListState()
    val showHero = tag.isEmpty() && heroes.isNotEmpty()
    val solid by remember(showHero) {
        derivedStateOf {
            if (!showHero || listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 420f).coerceIn(0f, 1f)
        }
    }

    // Aşağı kaydırınca üst çubuk gizlenir, yukarı kaydırınca geri gelir
    var barVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var lastIdx = 0
        var lastOff = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { cur ->
            val i = cur.first
            val o = cur.second
            val down = i > lastIdx || (i == lastIdx && o > lastOff + 12)
            val up = i < lastIdx || (i == lastIdx && o < lastOff - 12)
            if (i == 0 && o < 60) barVisible = true
            else if (down) barVisible = false
            else if (up) barVisible = true
            if (down || up) { lastIdx = i; lastOff = o }
        }
    }

    val cell = ((LocalConfiguration.current.screenWidthDp - 32 - 16) / 3).dp

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            if (showHero) {
                item(key = "hero") { HeroPager(heroes, heroIdx, openTitle) }
            } else {
                item(key = "hero_space") { Spacer(Modifier.height(if (online) 112.dp else 150.dp)) }
            }

            if (catalog.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (online) "Yükleniyor…" else "Katalog yüklenemedi. İnternete bağlanınca burada görünür.",
                        color = Muted, modifier = Modifier.padding(24.dp),
                    )
                }
            }

            if (tags.isNotEmpty()) {
                item(key = "chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    ) {
                        items(tags) { g ->
                            val on = g == tag
                            Text(
                                g, fontSize = 13.sp, color = Color.White, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (on) Red else Color(0xCC282828))
                                    .border(1.dp, if (on) Red else Color(0x33FFFFFF), RoundedCornerShape(50))
                                    .clickable { tag = if (on) "" else g }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            if (tag.isNotEmpty()) {
                val filtered = data.filter { tag in it.tags }
                item(key = "tag_title") { RowTitle("$tag (${filtered.size})") }
                items(filtered.chunked(3)) { chunk ->
                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in chunk) PosterCard(t, onClick = { openTitle(t.id) }, width = cell, showTitle = false)
                    }
                }
            } else {
                for (row in shownRows) {
                    item(key = "row_" + row.name) {
                        Column(Modifier.padding(bottom = 22.dp)) {
                            RowTitle(row.name)
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(if (row.kind == 2) 6.dp else 8.dp)) {
                                items(row.items, key = { it.id }) { t ->
                                    when (row.kind) {
                                        1 -> ContinueCard(t, latestFraction(t.id) ?: 0f) { openTitle(t.id) }
                                        2 -> Top10Card(t) { openTitle(t.id) }
                                        else -> PosterCard(t, onClick = { openTitle(t.id) }, width = 118.dp, showTitle = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = barVisible,
            modifier = Modifier.align(Alignment.TopStart),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            TopBar(page, { page = it; tag = "" }, solid, online, unread, { showNotifs = true }, goDownloads, openSearch, openProfile)
        }

        if (showNotifs) {
            ModalBottomSheet(onDismissRequest = { showNotifs = false }, containerColor = Panel) {
                NotificationList(
                    notifs, onOpen = { openLink(it) },
                    onReadAll = {
                        scope.launch { try { Svc.api.markAllRead() } catch (e: Exception) { } }
                        notifs = notifs.map { it.copy(read = true) }; unread = 0
                    },
                )
            }
        }
    }
}

private fun ago(at: Long): String {
    val d = System.currentTimeMillis() / 1000 - at
    return when {
        d < 60 -> "az önce"
        d < 3600 -> "${d / 60} dk önce"
        d < 86400 -> "${d / 3600} sa önce"
        else -> "${d / 86400} gün önce"
    }
}

@Composable
private fun NotificationList(items: List<Notif>, onOpen: (Notif) -> Unit, onReadAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bildirimler", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (items.any { !it.read }) {
                Text(
                    "Tümünü okundu say", color = Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReadAll).padding(8.dp),
                )
            }
        }
        if (items.isEmpty()) {
            Text("Henüz bildirim yok", color = Muted, modifier = Modifier.padding(20.dp))
        }
        for (n in items) {
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(n) }.background(if (n.read) Color.Transparent else Color(0x14FFFFFF)).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Panel2)) {
                    if (n.image.isNotBlank()) {
                        AsyncImage(Svc.api.img(n.image, 320), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(n.title, fontSize = 14.sp, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (n.body.isNotBlank()) Text(n.body, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    Text(ago(n.at), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
                if (!n.read) Box(Modifier.padding(start = 8.dp).size(9.dp).clip(CircleShape).background(Red))
            }
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/* ------------------------------------------------------------------ Üst çubuk */
@Composable
private fun TopBar(
    page: Int, onPage: (Int) -> Unit, solid: Float, online: Boolean, unread: Int, onBell: () -> Unit,
    goDownloads: () -> Unit, openSearch: () -> Unit, openProfile: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Bg.copy(alpha = solid))
            .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color(0x99000000), Color.Transparent)))
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(LogoIcon, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
            Text("DAFMIX", fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = openSearch) { Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color.White, modifier = Modifier.size(26.dp)) }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBell), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Notifications, contentDescription = "Bildirimler", tint = Color.White, modifier = Modifier.size(24.dp))
                if (unread > 0) {
                    Text(
                        if (unread > 9) "9+" else unread.toString(), fontSize = 9.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 1.dp).clip(CircleShape).background(Red).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Box(
                Modifier.padding(start = 2.dp, end = 4.dp).size(32.dp).clip(RoundedCornerShape(6.dp)).background(Red).clickable(onClick = openProfile),
                contentAlignment = Alignment.Center,
            ) {
                val av = Svc.prefs.profileAvatar
                if (av.isNotBlank()) {
                    AsyncImage(Svc.api.img(av, 160), contentDescription = "Profil", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(Svc.prefs.profileName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        ) {
            items(PAGES.size) { i ->
                val on = i == page
                Text(
                    PAGES[i].first, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Color.Black else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) Color.White else Color(0x24FFFFFF))
                        .clickable { onPage(i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (!online) OfflineBanner(goDownloads)
    }
}

/* ------------------------------------------------------------------ Hero */
@Composable
private fun HeroPager(items: List<Title>, idx: Int, onOpen: (Int) -> Unit) {
    val t = items[idx.coerceIn(0, items.size - 1)]
    Box(Modifier.fillMaxWidth().aspectRatio(0.76f)) {
        Crossfade(targetState = t, animationSpec = tween(900), label = "hero") { cur ->
            AsyncImage(
                model = Svc.api.img(cur.poster, 1080),
                contentDescription = cur.title, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().clickable { onOpen(cur.id) },
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x99000000), 0.22f to Color.Transparent, 0.5f to Color.Transparent,
                    0.78f to Color(0xC7141414), 1f to Bg,
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Crossfade(targetState = t, animationSpec = tween(600), label = "heroTitle") { cur ->
                Text(
                    cur.title, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, color = Color.White,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 3f), 18f)),
                )
            }
            Text(
                listOf(t.typeLabel, t.year, t.tags.firstOrNull() ?: "").filter { it.isNotBlank() }.joinToString("  •  "),
                color = Color(0xFFD8D8D8), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { onOpen(t.id) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                    Text("Oynat", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
                }
                Row(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xB36D6D6E)).clickable { onOpen(t.id) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("Daha Fazla Bilgi", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (items.size > 1) {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items.forEachIndexed { i, _ ->
                        Box(
                            Modifier.size(if (i == idx) 7.dp else 5.dp).clip(CircleShape)
                                .background(if (i == idx) Color.White else Color(0x66FFFFFF))
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Kartlar */
@Composable
private fun ContinueCard(t: Title, frac: Float, onClick: () -> Unit) {
    Column(Modifier.width(236.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Panel2)) {
            AsyncImage(
                model = Svc.api.img(t.thumb.ifBlank { t.poster }, 640),
                contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
            Icon(
                Icons.Default.PlayArrow, contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(42.dp).background(Color(0x99000000), CircleShape).padding(6.dp),
            )
            Text(
                t.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 9.dp),
            )
            LinearProgressIndicator(
                progress = frac.coerceIn(0f, 1f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = Red, trackColor = Color(0x66FFFFFF),
            )
        }
    }
}

@Composable
private fun Top10Card(t: Title, onClick: () -> Unit) {
    val n = t.top10
    val numW = if (n >= 10) 112.dp else 60.dp
    val posterW = 112.dp
    Box(Modifier.width(numW + posterW).height(168.dp).clickable(onClick = onClick)) {
        Text(
            n.toString(),
            modifier = Modifier.align(Alignment.BottomStart),
            style = TextStyle(
                fontSize = 130.sp, lineHeight = 130.sp, fontWeight = FontWeight.Black, letterSpacing = (-8).sp,
                color = if (n <= 3) Color(0xE6E50914) else Color(0xB3FFFFFF),
                drawStyle = Stroke(width = 5f, join = StrokeJoin.Round),
            ),
        )
        AsyncImage(
            model = Svc.api.img(t.poster, 480), contentDescription = t.title, contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.BottomEnd).width(posterW).height(168.dp)
                .clip(RoundedCornerShape(8.dp)).background(Panel2),
        )
    }
}
