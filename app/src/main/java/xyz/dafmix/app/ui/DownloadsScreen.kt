package xyz.dafmix.app.ui

import android.os.StatFs
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.DownloadMeta
import xyz.dafmix.app.download.Dl
import xyz.dafmix.app.download.DlState
import xyz.dafmix.app.download.Downloads
import java.io.File

private fun freeBytes(ctx: android.content.Context): Long = try {
    val s = StatFs(ctx.filesDir.path)
    s.availableBytes
} catch (_: Exception) { 0L }

/* ------------------------------------------------------------------ İndirilenler sekmesi */
@Composable
fun DownloadsTab(openPlayer: () -> Unit) {
    val ctx = LocalContext.current
    val metas by Svc.store.items.collectAsState()
    val states by Dl.states.collectAsState()
    val online by Svc.online.collectAsState()
    var pendingDelete by remember { mutableStateOf<List<String>?>(null) }
    var deleteLabel by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(try { Dl.manager.downloadsPaused } catch (_: Exception) { false }) }
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }

    fun ready(m: DownloadMeta) = states[m.contentId]?.completed == true
    fun playQueue(list: List<DownloadMeta>, at: Int) {
        if (at < 0) return
        Playback.start(
            list.map { m -> PlayItem(m.contentId, m.titleId, m.episodeId, m.titleName, if (m.isMovie) "" else m.label, m.introStart, m.introEnd) },
            at,
        )
        openPlayer()
    }

    val active = metas.filter { val s = states[it.contentId]; s == null || !s.completed }.sortedBy { it.addedAt }
    val movies = metas.filter { it.isMovie && ready(it) }.sortedByDescending { it.addedAt }
    val shows = metas.filter { !it.isMovie && ready(it) }.groupBy { it.titleId }.values.sortedByDescending { l -> l.maxOf { it.addedAt } }
    val used = Dl.usedBytes()
    val free = freeBytes(ctx)
    val frac = if (used + free > 0) (used.toFloat() / (used + free)).coerceIn(0f, 1f) else 0f

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("İndirilenler", fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                if (active.any { states[it.contentId]?.failed != true }) {
                    Text(
                        if (paused) "Sürdür" else "Duraklat", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2A2A2E)).clickable {
                            try { if (paused) Dl.manager.resumeDownloads() else Dl.manager.pauseDownloads(); paused = !paused } catch (_: Exception) { }
                        }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        // Depolama
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
                Row {
                    Text("${fmtBytes(used)} indirildi", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${fmtBytes(free)} boş", fontSize = 13.sp, color = Muted)
                }
                Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF2E2E33))) {
                    Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.01f)).height(6.dp).clip(CircleShape).background(Red))
                }
                if (!online) Text("Çevrimdışısın — indirilenleri izleyebilirsin", color = Color(0xFFFFC15C), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                else if (paused && active.isNotEmpty()) Text("İndirmeler duraklatıldı", color = Color(0xFFFFC15C), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }

        if (metas.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                        Icon(DownloadIcon, null, tint = Muted, modifier = Modifier.size(40.dp))
                    }
                    Text("Henüz indirilen içerik yok", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp))
                    Text(
                        "Bir filmin ya da dizi bölümünün yanındaki indirme düğmesine dokun. İndirdiklerini internet olmadan da izleyebilirsin.",
                        color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        // İndiriliyor
        if (active.isNotEmpty()) {
            item { Text("İndiriliyor (${active.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp)) }
            items(active, key = { "a" + it.contentId }) { m ->
                ActiveRow(m, states[m.contentId], paused) { pendingDelete = listOf(m.contentId); deleteLabel = "İndirme iptal edilsin mi?" }
            }
        }

        // Filmler
        if (movies.isNotEmpty()) {
            item { Text("Filmler", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(movies, key = { "m" + it.contentId }) { m ->
                ReadyRow(
                    m, states[m.contentId], title = m.titleName, sub = null,
                    onPlay = { playQueue(movies, movies.indexOf(m)) },
                    onDelete = { pendingDelete = listOf(m.contentId); deleteLabel = "İndirilen film silinsin mi?" },
                )
            }
        }

        // Diziler
        if (shows.isNotEmpty()) {
            item { Text("Diziler", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(shows, key = { "s" + it.first().titleId }) { eps ->
                val sorted = eps.sortedWith(compareBy({ it.season }, { it.episode }))
                val tid = sorted.first().titleId
                val open = expanded[tid] == true
                val size = sorted.sumOf { states[it.contentId]?.bytes ?: 0L }
                // Devam: son izlenen bitmemiş bölüm, yoksa ilk izlenmemiş bölüm
                val resumeIdx = sorted.indexOfFirst { val p = Svc.prefs.getProgress(it.contentId); p == null || p.fraction < 0.95f }.let { if (it < 0) 0 else it }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded[tid] = !open }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Poster(sorted.first(), Modifier.width(72.dp).aspectRatio(2f / 3f))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(sorted.first().titleName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${sorted.size} bölüm • ${fmtBytes(size)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                            Row(
                                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                                    .clickable { playQueue(sorted, resumeIdx) }.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Text(sorted[resumeIdx].let { "S${it.season} B${it.episode}" } + " oynat", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Muted, modifier = Modifier.rotate(if (open) 180f else 0f))
                    }
                    if (open) {
                        for (m in sorted) {
                            ReadyRow(
                                m, states[m.contentId], title = "S${m.season} B${m.episode}" + if (m.episodeTitle.isNotBlank()) " · ${m.episodeTitle}" else "", sub = null,
                                onPlay = { playQueue(sorted, sorted.indexOf(m)) },
                                onDelete = { pendingDelete = listOf(m.contentId); deleteLabel = "Bölüm silinsin mi?" },
                                compact = true,
                            )
                        }
                        Text(
                            "Diziyi tamamen sil", color = Color(0xFFFF8E94), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clickable {
                                pendingDelete = eps.map { it.contentId }; deleteLabel = "Dizinin tüm indirilenleri silinsin mi?"
                            }.padding(14.dp),
                        )
                    }
                }
            }
        }
    }

    val pd = pendingDelete
    if (pd != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(deleteLabel) },
            text = { Text("İndirilen dosya cihazdan silinir; istediğin zaman tekrar indirebilirsin.") },
            confirmButton = { TextButton(onClick = { pd.forEach { Downloads.remove(ctx, it) }; pendingDelete = null }) { Text("Sil", color = Red) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun Poster(m: DownloadMeta, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Panel2)) {
        if (m.posterPath.isNotBlank()) {
            AsyncImage(model = File(m.posterPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ActiveRow(m: DownloadMeta, st: DlState?, paused: Boolean, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Poster(m, Modifier.width(96.dp).aspectRatio(16f / 10f))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(m.label, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            val failed = st?.failed == true
            val pct = st?.percent?.takeIf { it >= 0f } ?: 0f
            Text(
                when {
                    failed -> "İndirme başarısız"
                    st == null -> "Sırada…"
                    paused -> "Duraklatıldı • %${pct.toInt()}"
                    st.percent >= 0f -> "%${pct.toInt()} • ${fmtBytes(st.bytes)}"
                    else -> "İndiriliyor…"
                },
                color = if (failed) Color(0xFFFF6B6B) else Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp),
            )
            if (!failed) {
                LinearProgressIndicator(
                    progress = (pct / 100f).coerceIn(0.02f, 1f), color = Red, trackColor = Color(0xFF2E2E33),
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(CircleShape),
                )
            }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "İptal", tint = Muted)
        }
    }
}

@Composable
private fun ReadyRow(
    m: DownloadMeta, st: DlState?, title: String, sub: String?, onPlay: () -> Unit, onDelete: () -> Unit, compact: Boolean = false,
) {
    val prog = Svc.prefs.getProgress(m.contentId)
    val outer = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
    Row(outer.clickable(onClick = onPlay).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(if (compact) 84.dp else 104.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(8.dp)).background(Panel2)) {
            if (m.posterPath.isNotBlank()) {
                AsyncImage(model = File(m.posterPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            if (prog != null && prog.fraction > 0.01f) {
                LinearProgressIndicator(
                    progress = prog.fraction, color = Red, trackColor = Color(0x66000000),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                listOfNotNull(st?.bytes?.let { fmtBytes(it) }, if (prog != null && prog.fraction >= 0.95f) "İzlendi" else if (prog != null && prog.fraction > 0.01f) "%${(prog.fraction * 100).toInt()} izlendi" else null, sub)
                    .joinToString(" • "),
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Delete, "Sil", tint = Muted)
        }
    }
}
