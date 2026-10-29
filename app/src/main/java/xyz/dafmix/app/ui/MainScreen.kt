@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.dafmix.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.ApiException
import xyz.dafmix.app.data.Favs
import xyz.dafmix.app.data.Title
import kotlinx.coroutines.launch
import xyz.dafmix.app.download.Dl
import xyz.dafmix.app.download.Downloads
import java.io.File
import java.util.Locale

@Composable
fun MainScreen(openTitle: (Int) -> Unit, openPlayer: () -> Unit, changeProfile: () -> Unit, logout: () -> Unit, openBirlikte: () -> Unit = {}, openSeries: (Int) -> Unit = {}) {
    val online by Svc.online.collectAsState()
    var tab by rememberSaveable { mutableStateOf(if (Svc.online.value) 0 else 2) }
    var catalog by remember { mutableStateOf(Svc.api.cachedCatalog()) }

    LaunchedEffect(Unit) { Favs.reload() }
    LaunchedEffect(online) {
        if (online) {
            launch { Favs.sync() }
            try {
                catalog = Svc.api.catalog()
            } catch (e: ApiException) {
                if (e.status == 401) logout()
            } catch (e: Exception) {
                // ağ hatası: önbellekteki katalog gösterilmeye devam eder
            }
        }
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Bg) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White, selectedTextColor = Color.White,
                    unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Color(0xFF2A2A2E),
                )
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Ana Sayfa") }, colors = colors)
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Ara") }, colors = colors)
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(DownloadIcon, null) }, label = { Text("İndirilenler") }, colors = colors)
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profil") }, colors = colors)
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeTab(catalog, online, openTitle, goDownloads = { tab = 2 }, openSearch = { tab = 1 }, openProfile = { tab = 3 }, openSeries = openSeries)
                1 -> SearchTab(catalog, openTitle)
                2 -> DownloadsTab(openPlayer)
                else -> ProfileTab(changeProfile, logout, goDownloads = { tab = 2 }, openBirlikte = openBirlikte)
            }
        }
    }
}

@Composable
fun OfflineBanner(onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF3A2A08)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Çevrimdışısın. İndirdiklerini izleyebilirsin.", color = Color(0xFFFFC15C), fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (onClick != null) TextButton(onClick = onClick) { Text("İndirilenler", color = Color.White) }
    }
}

/** Android 13+ için bildirim izni (indirme ilerlemesi bildirimi). İlk indirmede istenir. */
@Composable
fun rememberNotifPermission(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
