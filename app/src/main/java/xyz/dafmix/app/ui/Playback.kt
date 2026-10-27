package xyz.dafmix.app.ui

/** Oynatıcıya iletilen sıra (bölümler art arda oynar) */
data class PlayItem(
    val contentId: String,
    val titleId: Int,
    val episodeId: Int,   // film için 0
    val title: String,
    val subtitle: String,
    val introStart: Double? = null,
    val introEnd: Double? = null,
    val creditsStart: Double? = null,
)

object Playback {
    var queue: List<PlayItem> = emptyList()
    var index: Int = 0

    fun start(items: List<PlayItem>, at: Int) {
        queue = items
        index = at.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }
}
