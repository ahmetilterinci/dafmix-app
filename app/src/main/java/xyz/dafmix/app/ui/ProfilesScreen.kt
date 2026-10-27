package xyz.dafmix.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.ApiException
import xyz.dafmix.app.data.Profile

@Composable
fun ProfilesScreen(onPicked: () -> Unit, onLogout: () -> Unit) {
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val hasSaved = Svc.prefs.profileId > 0

    LaunchedEffect(Unit) {
        try {
            profiles = Svc.api.profiles()
        } catch (e: ApiException) {
            if (e.status == 401) onLogout() else error = e.message
        } catch (e: Exception) {
            error = "İnternet yok"
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(LogoIcon, null, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
            Text("DAFMIX", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Red, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.height(40.dp))
        Text("Kim izliyor?", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(28.dp))

        val list = profiles
        when {
            list != null -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(list) { p ->
                    val current = p.id == Svc.prefs.profileId
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                            Svc.prefs.profileId = p.id
                            Svc.prefs.profileName = p.name
                            Svc.prefs.profileAvatar = p.avatar
                            onPicked()
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Panel2)
                                .then(if (current) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                        ) {
                            AsyncImage(
                                model = Svc.api.img(p.avatar, 320), contentDescription = p.name,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            )
                            if (p.isKid) {
                                Text(
                                    "ÇOCUK", fontSize = 10.sp, fontWeight = FontWeight.Black,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            p.name, modifier = Modifier.padding(top = 10.dp), fontSize = 15.sp,
                            color = if (current) Color.White else Color(0xFFB3B3B3), fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            error != null -> {
                Text(error ?: "", color = Muted)
                Spacer(Modifier.height(16.dp))
                if (hasSaved) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Red).clickable(onClick = onPicked).padding(horizontal = 24.dp, vertical = 14.dp),
                    ) { Text("Çevrimdışı devam et", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.weight(1f))
            }
            else -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
        }
        Text(
            "Çıkış yap", color = Muted, fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 14.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onLogout).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
