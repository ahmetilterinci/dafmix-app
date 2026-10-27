@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.dafmix.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.ApiException
import xyz.dafmix.app.data.Episode
import xyz.dafmix.app.data.OfflineException
import xyz.dafmix.app.data.Source
import xyz.dafmix.app.data.Title
import xyz.dafmix.app.data.TitleDetail
import xyz.dafmix.app.data.Favs
import xyz.dafmix.app.download.Dl
import xyz.dafmix.app.download.Downloads
import xyz.dafmix.app.download.episodeContentId
import xyz.dafmix.app.download.movieId

@Composable
fun DetailScreen(id: Int, back: () -> Unit, openPlayer: () -> Unit, openTitle: (Int) -> Unit) {
    val catalog = remember { Svc.api.cachedCatalog() }
    val cached = remember(id) { catalog.firstOrNull { it.id == id } }
    var detail by remember(id) { mutableStateOf<TitleDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var seasonIdx by remember(id) { mutableIntStateOf(0) }
    var expanded by remember(id) { mutableStateOf(false) }
    var srcList by remember(id) { mutableStateOf<List<Source>>(emptyList()) }
    var srcLabel by remember(id) { mutableStateOf(Svc.prefs.sourceLabel) }
    var showSheet by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val favs by Favs.ids.collectAsState()
    val scope = rememberCoroutineScope()
    val askNotif = rememberNotifPermission()

    LaunchedEffect(id) {
        try {
            detail = Svc.api.detail(id)
        } catch (e: OfflineException) {
            error = "İnternet bağlantısı yok"
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = e.message ?: "Yüklenemedi"
        }
    }

    val title: Title? = detail?.title ?: cached
    val seasons = detail?.seasons ?: emptyList()

    // Kaynak listesi (film: filmin kaynakları; dizi: ilk oynatılabilir bölümün kaynakları)
    LaunchedEffect(detail) {
        val d = detail ?: return@LaunchedEffect
        try {
            val epId: Int? = if (d.title.isMovie) null else d.seasons.flatMap { it.episodes }.firstOrNull { it.playable }?.id
            if (d.title.isMovie || epId != null) srcList = Svc.api.sources(d.title.id, epId)
        } catch (e: Exception) {
            // kaynak seçici gizli kalır; oynatma yine de ilk kaynakla çalışır
        }
    }
    val effLabel: String = srcList.firstOrNull { it.label == srcLabel }?.label ?: srcList.firstOrNull()?.label ?: ""

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    fun download(t: Title, season: Int, ep: Episode?) {
        askNotif()
        scope.launch {
            val err = Downloads.enqueue(ctx, t, season, ep)
            if (err != null) toast(err) else toast("İndirme başladı")
        }
    }

    fun downloadSeason(t: Title, season: Int, eps: List<Episode>) {
        askNotif()
        scope.launch {
            var n = 0
            for (e in eps) {
                if (!e.playable) continue
                if (Dl.states.value[episodeContentId(t.id, e.id)] != null) continue
                val err = Downloads.enqueue(ctx, t, season, e)
                if (err != null) { toast(err); return@launch }
                n++
            }
            toast(if (n > 0) "$n bölüm indirmeye eklendi" else "İndirilecek yeni bölüm yok")
        }
    }

    /** Tüm oynatılabilir bölümler sırayla (sezonlar arası geçişli) */
    fun allEpisodes(t: Title): List<PlayItem> = seasons.flatMap { s ->
        s.episodes.filter { it.playable }.map { e ->
            PlayItem(episodeContentId(t.id, e.id), t.id, e.id, t.title, "S${s.number} B${e.number}" + if (e.title.isNotBlank()) " · ${e.title}" else "", e.introStart, e.introEnd, e.creditsStart)
        }
    }

    fun playMovie(t: Title) {
        Playback.start(listOf(PlayItem(movieId(t.id), t.id, 0, t.title, "")), 0)
        openPlayer()
    }

    fun playEpisode(t: Title, epId: Int) {
        val list = allEpisodes(t)
        val at = list.indexOfFirst { it.episodeId == epId }
        if (at >= 0) { Playback.start(list, at); openPlayer() }
    }

    fun lastEpisodeProgress(t: Title) =
        Svc.prefs.allProgress().filter { it.contentId.startsWith("e${t.id}_") }.maxByOrNull { it.ts }

    fun playSeries(t: Title) {
        val list = allEpisodes(t)
        if (list.isEmpty()) { toast("Oynatılabilir bölüm yok"); return }
        val last = lastEpisodeProgress(t)
        var at = 0
        if (last != null) {
            val i = list.indexOfFirst { it.contentId == last.contentId }
            if (i >= 0) at = if (last.fraction >= 0.95f && i + 1 < list.size) i + 1 else i
        }
        Playback.start(list, at); openPlayer()
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (title == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error != null) Text(error ?: "", color = Muted) else CircularProgressIndicator(color = Red)
            }
        } else {
            val cell = ((LocalConfiguration.current.screenWidthDp - 32 - 16) / 3).dp
            val similar = remember(title.id, catalog) {
                catalog.filter { it.id != title.id && it.playable && it.type == title.type }
                    .map { it to it.tags.count { g -> g in title.tags } }
                    .filter { it.second > 0 }.sortedByDescending { it.second }.take(9).map { it.first }
            }
            val resume: Float? = if (title.isMovie) Svc.prefs.getProgress(movieId(title.id))?.fraction else lastEpisodeProgress(title)?.fraction
            val hasProgress = resume != null && resume > 0.01f && resume < 0.95f

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                // Vitrin: geniş arka plan + solda afiş, sağda başlık
                item {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val bdH = maxWidth * 9f / 16f
                        val posterW = maxWidth * 0.31f
                        AsyncImage(
                            model = Svc.api.img(title.thumb.ifBlank { title.topThumb }, 1080),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(bdH),
                        )
                        Box(
                            Modifier.fillMaxWidth().height(bdH).background(
                                Brush.verticalGradient(0f to Color(0x99000000), 0.35f to Color.Transparent, 0.7f to Color(0x8C141414), 1f to Bg)
                            )
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = bdH - 64.dp, start = 16.dp, end = 16.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            AsyncImage(
                                model = Svc.api.img(title.poster, 480), contentDescription = title.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.width(posterW).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Panel2),
                            )
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(
                                    title.title, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(title.typeLabel, title.year).filter { it.isNotBlank() }.joinToString("  •  "),
                                    color = Color(0xFFBBBBBB), fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp),
                                )
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (title.imdb.isNotBlank()) Text("★ ${title.imdb}", color = Color(0xFFF5C518), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (title.time.isNotBlank()) Text(title.time, color = Color(0xFFD0D0D0), fontSize = 13.sp)
                                    if (title.quality.isNotBlank()) Chip(title.quality.uppercase())
                                    if (title.dubbed) Chip("Dublaj")
                                }
                            }
                        }
                    }
                }

                // Eylemler
                item {
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp)) {
                        val canPlay = title.playable && (title.isMovie || seasons.isNotEmpty())
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (canPlay) Color.White else Color(0xFF3A3A3E))
                                .clickable(enabled = canPlay) { if (title.isMovie) playMovie(title) else playSeries(title) },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = if (canPlay) Color.Black else Muted, modifier = Modifier.size(28.dp))
                            Text(
                                if (!canPlay) "Yakında" else if (hasProgress) "Kaldığın Yerden Devam Et" else "Oynat",
                                color = if (canPlay) Color.Black else Muted, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        if (hasProgress) {
                            LinearProgressIndicator(
                                progress = resume ?: 0f, color = Red, trackColor = Color(0xFF2A2A2E),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp),
                            )
                        }
                        run {
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (srcList.isNotEmpty()) {
                                    Row(
                                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2E))
                                            .clickable { showSheet = true }.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Kaynak", color = Muted, fontSize = 13.sp)
                                        Text(effLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White)
                                    }
                                } else Spacer(Modifier.weight(1f))
                                val inList = title.id in favs
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape).background(if (inList) Color(0xFF1E3A26) else Color(0xFF2A2A2E)).clickable { Favs.toggle(title.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(if (inList) Icons.Default.Check else Icons.Default.Add, if (inList) "Listemden çıkar" else "Listeme ekle", tint = if (inList) Green else Color.White)
                                }
                                if (title.isMovie && title.playable) {
                                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2A2A2E)), contentAlignment = Alignment.Center) {
                                        DlButton(movieId(title.id), onStart = { download(title, 0, null) })
                                    }
                                }
                            }
                        }
                        if (error != null && detail == null) {
                            Text(error ?: "", color = Color(0xFFFFC15C), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                        }
                        if (title.desc.isNotBlank()) {
                            Text(
                                title.desc, color = Color(0xFFD8D8D8), fontSize = 14.sp, lineHeight = 21.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 16.dp).clickable { expanded = !expanded },
                            )
                            if (title.desc.length > 160) {
                                Text(
                                    if (expanded) "Daha az" else "Devamını oku", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded },
                                )
                            }
                        }
                        if (title.cast.isNotBlank()) {
                            Text("Oyuncular: ${title.cast}", color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
                if (title.tags.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            items(title.tags) { g ->
                                Text(
                                    g, fontSize = 12.sp, color = Color(0xFFE0E0E0),
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF262628)).padding(horizontal = 13.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                // Bölümler
                if (!title.isMovie && seasons.isNotEmpty()) {
                    val season = seasons[seasonIdx.coerceIn(0, seasons.size - 1)]
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Bölümler", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).clickable { downloadSeason(title, season.number, season.episodes) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(DownloadIcon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Sezonu indir", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    if (seasons.size > 1) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(seasons.size) { i ->
                                    val on = seasonIdx == i
                                    Text(
                                        "Sezon ${seasons[i].number}", fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                        color = if (on) Color.Black else Color.White,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Color(0xFF2A2A2E))
                                            .clickable { seasonIdx = i }.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    } else item { Spacer(Modifier.height(8.dp)) }
                    items(season.episodes, key = { "ep" + it.id }) { ep ->
                        EpisodeCard(title, ep, onPlay = { playEpisode(title, ep.id) }, onDownload = { download(title, season.number, ep) })
                    }
                }

                // Benzer içerikler
                if (similar.isNotEmpty()) {
                    item {
                        Text("Benzer İçerikler", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 28.dp, bottom = 12.dp))
                    }
                    items(similar.chunked(3)) { chunk ->
                        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (t in chunk) PosterCard(t, onClick = { openTitle(t.id) }, width = cell, showTitle = false)
                        }
                    }
                }
            }
        }

        Box(
            Modifier.padding(12.dp).size(40.dp).clip(CircleShape).background(Color(0x99000000)).clickable { back() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White) }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = Panel) {
            Column(Modifier.padding(bottom = 28.dp)) {
                Text("Kaynak seç", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Text(
                    "Seçimin sonraki oynatma ve indirmelerde de kullanılır.",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                )
                for (s in srcList) {
                    val on = s.label == effLabel
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            Svc.prefs.sourceLabel = s.label
                            srcLabel = s.label
                            showSheet = false
                        }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.label.ifBlank { "Kaynak ${s.id}" }, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Default.Check, null, tint = Green)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0E0E0),
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EpisodeCard(t: Title, ep: Episode, onPlay: () -> Unit, onDownload: () -> Unit) {
    val cid = episodeContentId(t.id, ep.id)
    val prog = Svc.prefs.getProgress(cid)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp)).background(Panel)
            .clickable(enabled = ep.playable) { onPlay() }.padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Panel2), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = Svc.api.img(t.thumb.ifBlank { t.poster }, 320), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color(0x88000000)))
            if (ep.playable) Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Text(
                "B${ep.number}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xAA000000)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
            if (prog != null && prog.fraction > 0.01f) {
                LinearProgressIndicator(
                    progress = prog.fraction, color = Red, trackColor = Color(0x66FFFFFF),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp)) {
            Text(
                ep.title.ifBlank { "Bölüm ${ep.number}" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (ep.playable) Color.White else Muted,
            )
            if (ep.desc.isNotBlank()) {
                Text(ep.desc, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            if (!ep.playable) Text("Yakında", color = Color(0xFFFFC15C), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (ep.playable) DlButton(cid, onStart = onDownload)
    }
}
