@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.dafmix.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.ApiException
import xyz.dafmix.app.data.Me
import xyz.dafmix.app.data.Title
import xyz.dafmix.app.data.TitleDetail

/** Davet bağlantısından ya da düz koddan oda kodunu ayıklar */
private fun parseCode(input: String): String {
    val t = input.trim().substringBefore('?').trimEnd('/')
    return t.substringAfterLast('/').lowercase().filter { it.isLetterOrDigit() }
}

@Composable
fun BirlikteScreen(onBack: () -> Unit, openRoom: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<Me?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf<String?>(null) }
    var invite by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Title?>(null) }
    val catalog = remember { Svc.api.cachedCatalog().filter { it.playable } }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    LaunchedEffect(Unit) {
        try { me = Svc.api.me() } catch (_: Exception) { }
        try { mine = Svc.api.roomMine() } catch (_: Exception) { }
        loaded = true
    }

    fun join() {
        val code = parseCode(invite)
        if (code.length < 4) { toast("Geçerli bir davet bağlantısı ya da kod gir"); return }
        busy = true
        scope.launch {
            try {
                Svc.api.roomInfo(code)
                openRoom(code)
            } catch (e: ApiException) {
                toast(if (e.status == 404) "Oda bulunamadı ya da kapatılmış" else e.message ?: "Hata")
            } catch (e: Exception) {
                toast(e.message ?: "Bağlantı hatası")
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
            }
            Text("Birlikte İzle", fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 6.dp))
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Arkadaşlarınla aynı anda izle: oynatıcı herkeste senkron çalışır, sohbet edebilirsin.", color = Muted, fontSize = 13.sp)
            }

            // Odaya katıl
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    Text("Odaya katıl", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Arkadaşının gönderdiği davet bağlantısını ya da kodu yapıştır.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                    OutlinedTextField(
                        value = invite, onValueChange = { invite = it }, singleLine = true,
                        placeholder = { Text("https://dafmix.xyz/oda/…") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { join() }, enabled = !busy && invite.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) { Text("Katıl") }
                }
            }

            // Oda kur
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    Text("Oda kur", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    when {
                        !loaded -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red, modifier = Modifier.size(26.dp)) }
                        me?.watchParty != true -> {
                            Text("Oda kurmak Premium üyelere özel. Odalara katılmak ise tüm onaylı hesaplara açık.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                            Button(onClick = { openInBrowser(ctx, "/planlar") }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Planları incele") }
                        }
                        mine != null -> {
                            Text("Açık bir odan var.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                            Button(onClick = { openRoom(mine!!) }, colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth()) { Text("Odaya gir") }
                        }
                        else -> {
                            Text("İzlemek istediğin içeriği seç. Oda kurulduktan sonra içerik değişmez; dizilerde bölümler arası geçiş yapılabilir.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                            OutlinedTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                placeholder = { Text("Film, dizi ya da anime ara…") }, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (loaded && me?.watchParty == true && mine == null) {
                val q = query.trim().lowercase()
                val results = if (q.length < 2) emptyList() else catalog.filter { it.title.lowercase().contains(q) }.take(30)
                items(results) { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { picked = t }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = Svc.api.img(t.poster, 240), contentDescription = t.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(48.dp).height(70.dp).clip(RoundedCornerShape(6.dp)).background(Panel2),
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(t.typeLabel + if (t.year.isNotBlank()) " · ${t.year}" else "", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    val t = picked
    if (t != null) {
        CreateRoomDialog(t, onDismiss = { picked = null }, onCreated = { code -> picked = null; mine = code; openRoom(code) })
    }
}

/** İçerik (dizi ise bölüm) ve kontrol seçimi, sonra oda kurulur */
@Composable
private fun CreateRoomDialog(t: Title, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<TitleDetail?>(null) }
    var season by remember { mutableStateOf(0) }
    var episode by remember { mutableStateOf(0) }
    var control by remember { mutableStateOf("host") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(t.id) {
        try {
            val d = Svc.api.detail(t.id)
            detail = d
            val first = d.seasons.firstOrNull { s -> s.episodes.any { it.playable } }
            if (first != null) { season = first.number; episode = first.episodes.first { it.playable }.number }
        } catch (e: Exception) {
            Toast.makeText(ctx, e.message ?: "İçerik alınamadı", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val d = detail
            if (d == null) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(340.dp)) {
                    if (!t.isMovie) {
                        item { Text("Başlangıç bölümü", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                        for (s in d.seasons) {
                            val eps = s.episodes.filter { it.playable }
                            if (eps.isEmpty()) continue
                            item { Text("${s.number}. Sezon", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                            items(eps) { ep ->
                                val sel = season == s.number && episode == ep.number
                                Text(
                                    "${ep.number}. ${ep.title}", color = if (sel) Red else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.fillMaxWidth().clickable { season = s.number; episode = ep.number }.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                    item { Text("Oynatıcı kontrolü", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("Sadece ben", control == "host") { control = "host" }
                            ChoiceChip("Herkes", control == "all") { control = "all" }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = detail != null && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val code = Svc.api.roomCreate(t.id, season, episode, control)
                            if (code.isBlank()) throw IllegalStateException("Oda kurulamadı")
                            onCreated(code)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, e.message ?: "Oda kurulamadı", Toast.LENGTH_LONG).show()
                            busy = false
                        }
                    }
                },
            ) { Text("Odayı kur", color = Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Red else Panel2).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}
