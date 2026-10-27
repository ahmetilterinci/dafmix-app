package xyz.dafmix.app.data

import org.json.JSONArray
import org.json.JSONObject

data class Title(
    val id: Int,
    val type: String, // movie | series | anime
    val title: String,
    val year: String,
    val time: String,
    val imdb: String,
    val quality: String,
    val dubbed: Boolean,
    val desc: String,
    val cast: String,
    val thumb: String,
    val topThumb: String,
    val tags: List<String>,
    val lists: List<String>,
    val rank: Int,
    val playable: Boolean,
    val top10: Int = 0,
    val textLogo: String = "",
    val pid: String = "",
) {
    val isMovie get() = type == "movie"
    val poster get() = if (topThumb.isNotBlank()) topThumb else thumb
    val typeLabel get() = when (type) { "movie" -> "Film"; "series" -> "Dizi"; else -> "Anime" }
}

data class Episode(
    val id: Int,
    val number: Int,
    val title: String,
    val desc: String,
    val playable: Boolean,
    val introStart: Double?,
    val introEnd: Double?,
    val creditsStart: Double? = null,
)

data class Season(val number: Int, val episodes: List<Episode>)

data class TitleDetail(val title: Title, val seasons: List<Season>)

data class Source(val id: Int, val label: String, val url: String, val kind: String)

data class Landing(val headline: String, val subline: String, val slogan: String, val videoUrl: String)

data class Notif(val id: Int, val kind: String, val title: String, val body: String, val link: String, val image: String, val at: Long, val read: Boolean)

data class Me(val username: String, val email: String, val role: String, val approved: Boolean, val watchParty: Boolean = false)

data class Profile(val id: Int, val name: String, val avatar: String, val isKid: Boolean)

fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")
fun JSONObject.dbl(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it, "") }.filter { it.isNotBlank() }

fun parseTitle(o: JSONObject): Title = Title(
    id = o.optInt("id"),
    type = o.str("type"),
    title = o.str("title"),
    year = o.str("year"),
    time = o.str("time"),
    imdb = o.str("imdb"),
    quality = o.str("quality"),
    dubbed = o.optBoolean("dubbed", false),
    desc = o.str("desc"),
    cast = o.str("cast"),
    thumb = o.str("thumb"),
    topThumb = o.str("top_thumb"),
    tags = o.optJSONArray("tags")?.strings() ?: emptyList(),
    lists = o.optJSONArray("lists")?.strings() ?: emptyList(),
    rank = o.optInt("rank", 0),
    playable = o.optBoolean("playable", false),
    top10 = o.optInt("top10", 0),
    textLogo = o.str("text_logo"),
    pid = o.str("pid"),
)

fun parseDetail(o: JSONObject): TitleDetail {
    val seasons = ArrayList<Season>()
    val arr = o.optJSONArray("seasons")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val eps = ArrayList<Episode>()
            val ea = s.optJSONArray("episodes")
            if (ea != null) {
                for (j in 0 until ea.length()) {
                    val e = ea.optJSONObject(j) ?: continue
                    eps.add(
                        Episode(
                            id = e.optInt("id"),
                            number = e.optInt("ep_number"),
                            title = e.str("title"),
                            desc = e.str("epsode_desc"),
                            playable = e.optBoolean("playable", false),
                            introStart = e.dbl("intro_start"),
                            introEnd = e.dbl("intro_end"),
                            creditsStart = e.dbl("credits_start"),
                        )
                    )
                }
            }
            seasons.add(Season(s.optInt("season_number"), eps))
        }
    }
    return TitleDetail(parseTitle(o), seasons)
}
