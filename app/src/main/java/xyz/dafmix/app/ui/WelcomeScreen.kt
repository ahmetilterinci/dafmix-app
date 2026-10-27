package xyz.dafmix.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.Landing

private const val DEFAULT_HEADLINE = "Film, dizi, anime ve canlı TV. Hepsi tek yerde."
private const val DEFAULT_SUB = "İstediğiniz yerde izleyin. İstediğiniz zaman bırakın."

fun openInBrowser(ctx: android.content.Context, path: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Svc.prefs.baseUrl + path)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) { }
}

/** Giriş yapmamış kişileri karşılayan sayfa (sitenin açılış sayfası: arka planda video, başlık, giriş / kayıt) */
@Composable
fun WelcomeScreen(onLogin: () -> Unit) {
    val ctx = LocalContext.current
    var landing by remember { mutableStateOf<Landing?>(null) }
    var regOpen by remember { mutableStateOf(true) }
    var videoReady by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
        landing = Svc.api.landing()
        regOpen = Svc.api.registrationOpen()
    }

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { videoReady = true }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(landing?.videoUrl) {
        val u = landing?.videoUrl
        if (!u.isNullOrBlank()) {
            player.setMediaItem(MediaItem.fromUri(Svc.api.absolute(u)))
            player.prepare()
            player.playWhenReady = true
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) player.pause()
            if (e == Lifecycle.Event.ON_RESUME && player.mediaItemCount > 0) player.play()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val videoAlpha by animateFloatAsState(if (videoReady) 1f else 0f, tween(900), label = "vid")
    val L = landing

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Kırmızı ışıma (video yokken / yüklenirken)
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0x66E50914), Color.Transparent),
                    center = Offset(500f, 0f), radius = 1400f,
                )
            )
        )
        if (!L?.videoUrl.isNullOrBlank()) {
            val exo = player
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = exo
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                modifier = Modifier.fillMaxSize().alpha(videoAlpha),
            )
        }
        // Okunabilirlik katmanı
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xB3000000), 0.3f to Color(0x33000000), 0.55f to Color(0x66000000), 1f to Color(0xF2000000)
                )
            )
        )

        // Üst çubuk
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LogoIcon, null, tint = Color.Unspecified, modifier = Modifier.size(30.dp))
            Text("DAFMIX", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text(
                "Giriş Yap", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Red).clickable(onClick = onLogin).padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }

        // Başlık + düğmeler
        AnimatedVisibility(
            visible = started, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 6 },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(bottom = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    L?.headline?.takeIf { it.isNotBlank() } ?: DEFAULT_HEADLINE,
                    fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                )
                Text(
                    (L?.subline?.takeIf { it.isNotBlank() } ?: DEFAULT_SUB),
                    fontSize = 16.sp, color = Color(0xFFE0E0E0), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
                )
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in listOf("İndir", "İnternetsiz izle", "Kaldığın yerden devam")) {
                        Text(
                            t, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEDEDED),
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).background(Red).clickable(onClick = onLogin),
                    contentAlignment = Alignment.Center,
                ) { Text("Giriş Yap", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                if (regOpen) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(8.dp)).background(Color(0x33000000))
                            .clickable { openInBrowser(ctx, "/kayit") },
                        contentAlignment = Alignment.Center,
                    ) { Text("Kayıt Ol", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
                val slogan = L?.slogan.orEmpty()
                if (slogan.isNotBlank()) {
                    Text(slogan, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
                }
            }
        }
    }
}
