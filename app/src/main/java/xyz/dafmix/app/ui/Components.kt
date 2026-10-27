@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.dafmix.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.Title
import xyz.dafmix.app.download.Dl
import xyz.dafmix.app.download.Downloads

/** Material "download" simgesi (icons-extended eklenmesin diye elle tanımlı) */
val DownloadIcon: ImageVector = ImageVector.Builder(
    name = "Download", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).addPath(
    pathData = addPathNodes("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"),
    fill = SolidColor(Color.Black),
).build()

fun fmtBytes(b: Long): String {
    if (b < 1024L * 1024) return "${b / 1024} KB"
    val mb = b / (1024.0 * 1024.0)
    if (mb < 1024) return "%.0f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

@Composable
fun PosterCard(t: Title, onClick: () -> Unit, width: Dp = 118.dp, progress: Float? = null, showTitle: Boolean = true) {
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = Svc.api.img(t.poster, 480),
                contentDescription = t.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Red,
                    trackColor = Color(0x66000000),
                )
            }
        }
        if (showTitle) {
            Text(
                t.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = Color(0xFFD0D0D0), modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

/** İndirme düğmesi: indir / ilerleme (dokununca iptal) / indirildi (dokununca sil) / hata (tekrar dene) */
@Composable
fun DlButton(contentId: String, onStart: () -> Unit) {
    val states by Dl.states.collectAsState()
    val st = states[contentId]
    var confirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable {
            if (st == null || st.failed) onStart() else confirm = true
        },
        contentAlignment = Alignment.Center,
    ) {
        when {
            st == null -> Icon(DownloadIcon, contentDescription = "İndir", tint = Color.White, modifier = Modifier.size(24.dp))
            st.completed -> Icon(Icons.Default.Check, contentDescription = "İndirildi", tint = Green, modifier = Modifier.size(24.dp))
            st.failed -> Icon(Icons.Default.Refresh, contentDescription = "Tekrar dene", tint = Red, modifier = Modifier.size(24.dp))
            else -> {
                if (st.percent >= 0f) {
                    CircularProgressIndicator(
                        progress = (st.percent / 100f).coerceIn(0.03f, 1f),
                        modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = Red, trackColor = Color(0x33FFFFFF),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = Red)
                }
                Icon(Icons.Default.Close, contentDescription = "İptal", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }

    if (confirm) {
        val done = st?.completed == true
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (done) "İndirme silinsin mi?" else "İndirme iptal edilsin mi?") },
            text = { Text(if (done) "Cihazdaki kopya silinir; istediğin zaman tekrar indirebilirsin." else "Yarım kalan indirme silinir.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; Downloads.remove(ctx, contentId) }) { Text(if (done) "Sil" else "İptal et", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } },
        )
    }
}
