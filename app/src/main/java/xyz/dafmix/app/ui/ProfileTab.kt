@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.dafmix.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import xyz.dafmix.app.data.Me
import xyz.dafmix.app.download.Dl
import xyz.dafmix.app.download.Downloads

private fun titleIdOf(contentId: String): Int? =
    if (contentId.startsWith("t")) contentId.drop(1).toIntOrNull() else contentId.drop(1).substringBefore('_').toIntOrNull()

private fun fmtDuration(ms: Long): String {
    val min = ms / 60_000L
    val h = min / 60
    val m = min % 60
    return if (h > 0) "$h sa $m dk" else "$m dk"
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel), content = content)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
}

@Composable
private fun Row2(
    title: String, sub: String? = null, titleColor: Color = Color.White,
    onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 15.sp, color = titleColor)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

@Composable
private fun Divider2() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Color(0x14FFFFFF)))
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Panel).padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/* ------------------------------------------------------------------ Profil sekmesi */
@Composable
fun ProfileTab(changeProfile: () -> Unit, logout: () -> Unit, goDownloads: () -> Unit, openBirlikte: () -> Unit = {}) {
    val ctx = LocalContext.current
    var me by remember { mutableStateOf<Me?>(null) }
    var wifi by remember { mutableStateOf(Svc.prefs.wifiOnly) }
    var quality by remember { mutableIntStateOf(Svc.prefs.hlsQuality) }
    var autoNext by remember { mutableStateOf(Svc.prefs.autoNext) }
    var resize by remember { mutableIntStateOf(Svc.prefs.resizeMode) }
    var srcLabel by remember { mutableStateOf(Svc.prefs.sourceLabel) }
    var confirmAll by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { me = try { Svc.api.me() } catch (e: Exception) { null } }

    val metas by Svc.store.items.collectAsState()
    val states by Dl.states.collectAsState()
    val prog = remember { Svc.prefs.allProgress() }
    val watchedMs = prog.sumOf { it.posMs }
    val started = prog.mapNotNull { titleIdOf(it.contentId) }.toSet().size
    val doneCount = metas.count { states[it.contentId]?.completed == true }
    val used = Dl.usedBytes()

    LazyColumn(
        Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Üst kart
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(
                    Brush.verticalGradient(listOf(Color(0xFF2A1013), Panel))
                ).padding(vertical = 24.dp, horizontal = 16.dp)
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(104.dp).clip(CircleShape).border(3.dp, Red, CircleShape).background(Panel2)) {
                        AsyncImage(
                            model = Svc.api.img(Svc.prefs.profileAvatar, 320), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(104.dp),
                        )
                    }
                    Text(Svc.prefs.profileName.ifBlank { "Profil" }, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 14.dp))
                    val m = me
                    if (m != null) {
                        Text(m.email.ifBlank { m.username }, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        val (label, color) = when {
                            m.role == "admin" -> "Yönetici" to Color(0xFFE4C400)
                            m.approved -> "Üyelik aktif" to Green
                            else -> "Onay bekliyor" to Color(0xFFFFC15C)
                        }
                        Text(
                            label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Profil Değiştir", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x2EFFFFFF)).clickable(onClick = changeProfile).padding(horizontal = 18.dp, vertical = 11.dp),
                        )
                        if (me?.approved == false) {
                            Text(
                                "Planları Gör", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Red).clickable { openInBrowser(ctx, "/planlar") }.padding(horizontal = 18.dp, vertical = 11.dp),
                            )
                        }
                    }
                }
            }
        }

        // İstatistikler
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(fmtDuration(watchedMs), "İzlenen süre", Modifier.weight(1f))
                StatCard("$started", "Başlanan içerik", Modifier.weight(1f))
                StatCard("$doneCount", "İndirilen", Modifier.weight(1f))
            }
        }

        // Birlikte İzle
        item { SectionLabel("BİRLİKTE İZLE") }
        item {
            Card {
                Row2("Birlikte İzle", "Arkadaşlarınla aynı anda izle, sohbet et", onClick = openBirlikte) {
                    if (me?.watchParty != true) Text("Premium", color = Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // İndirmeler
        item { SectionLabel("İNDİRMELER") }
        item {
            Card {
                Row2("Yalnızca Wi‑Fi ile indir", "Kapalıysa mobil veri de kullanılır") {
                    Switch(
                        checked = wifi, onCheckedChange = { wifi = it; Svc.prefs.wifiOnly = it; Dl.applyRequirements() },
                        colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White),
                    )
                }
                Divider2()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("İndirme kalitesi", fontSize = 15.sp)
                    Text("Çok kaliteli (HLS) içeriklerde geçerlidir; MP4 olduğu gibi iner.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Yüksek", "720p", "480p").forEachIndexed { i, label ->
                            FilterChip(selected = quality == i, onClick = { quality = i; Svc.prefs.hlsQuality = i }, label = { Text(label) })
                        }
                    }
                }
                Divider2()
                Row2("İndirilenleri aç", "$doneCount içerik • ${fmtBytes(used)}", onClick = goDownloads) {
                    Text("›", color = Muted, fontSize = 22.sp)
                }
                Divider2()
                Row2("Tüm indirmeleri sil", "Cihazdaki bütün indirilenler silinir", titleColor = Color(0xFFFF8E94), onClick = { confirmAll = true })
            }
        }

        // Oynatıcı
        item { SectionLabel("OYNATICI") }
        item {
            Card {
                Row2("Sonraki bölümü otomatik oynat", "Jenerik başlayınca geri sayımla geçer") {
                    Switch(
                        checked = autoNext, onCheckedChange = { autoNext = it; Svc.prefs.autoNext = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White),
                    )
                }
                Divider2()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Görüntü modu", fontSize = 15.sp)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Sığdır", "Doldur", "Uzat").forEachIndexed { i, label ->
                            FilterChip(selected = resize == i, onClick = { resize = i; Svc.prefs.resizeMode = i }, label = { Text(label) })
                        }
                    }
                }
                Divider2()
                Row2(
                    "Tercih edilen kaynak",
                    if (srcLabel.isBlank()) "Otomatik (ilk kaynak)" else srcLabel,
                    onClick = if (srcLabel.isBlank()) null else ({ Svc.prefs.sourceLabel = ""; srcLabel = "" }),
                ) {
                    if (srcLabel.isNotBlank()) Text("Sıfırla", color = Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Hesap
        item { SectionLabel("HESAP") }
        item {
            Card {
                Row2("Siteyi aç", Svc.prefs.baseUrl.removePrefix("https://"), onClick = { openInBrowser(ctx, "/anasayfa") }) { Text("›", color = Muted, fontSize = 22.sp) }
                Divider2()
                Row2("Üyelik planları", "Plan başvurusu siteden yapılır", onClick = { openInBrowser(ctx, "/planlar") }) { Text("›", color = Muted, fontSize = 22.sp) }
                Divider2()
                Row2("Çıkış yap", titleColor = Red, onClick = { confirmLogout = true })
            }
        }
        item { Text("Dafmix 1.0", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Tüm indirmeler silinsin mi?") },
            text = { Text("Cihazdaki bütün indirilen içerikler silinir.") },
            confirmButton = { TextButton(onClick = { Downloads.removeAll(ctx); confirmAll = false }) { Text("Sil", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Vazgeç") } },
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Çıkış yapılsın mı?") },
            text = { Text("İndirdiklerin cihazda kalır; tekrar giriş yapınca izleyebilirsin.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; logout() }) { Text("Çıkış yap", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Vazgeç") } },
        )
    }
}
