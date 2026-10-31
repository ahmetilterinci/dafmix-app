package xyz.dafmix.app.ui

import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.Seri
import xyz.dafmix.app.data.Title
import kotlin.random.Random

/** kind: 0 afiş satırı, 1 izlemeye devam, 2 top10, 3 seriler (serileri keşfet) */
internal data class HomeRow(val name: String, val items: List<Title>, val kind: Int, val series: List<Seri> = emptyList())

private val TYPE_LABEL = mapOf("series" to "Dizi", "movie" to "Film", "anime" to "Anime")
private val VAGUE = setOf("görev", "genel", "diğer", "yeni", "popüler")

private fun yearOf(t: Title): Int? = Regex("(19|20)\\d{2}").find(t.year)?.value?.toIntOrNull()
private fun castOf(t: Title): List<String> = t.cast.split(',').map { it.trim() }.filter { it.length > 2 }
private fun minutesOf(t: Title): Int? {
    val s = t.time.lowercase()
    val h = Regex("(\\d+)\\s*(sa|saat|h)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val m = Regex("(\\d+)\\s*(dk|dak|min|m)").find(s)?.groupValues?.get(1)?.toIntOrNull()
    if (m == null && h == 0) return s.trim().toIntOrNull()
    return h * 60 + (m ?: 0)
}

/** İki içerik arasındaki benzerlik: etiket örtüşmesi + ortak oyuncu + tür + dönem */
private fun sim(a: Title, b: Title): Double {
    if (a.id == b.id) return 0.0
    val ta = a.tags.filter { it.lowercase() !in VAGUE }.toSet()
    val tb = b.tags.filter { it.lowercase() !in VAGUE }.toSet()
    if (ta.isEmpty() || tb.isEmpty()) return 0.0
    val shared = ta.intersect(tb).size
    if (shared == 0) return 0.0
    var s = shared.toDouble() / minOf(ta.size, tb.size) * 0.55 + shared.toDouble() / ta.union(tb).size * 0.25
    val ca = castOf(a).toSet()
    if (ca.isNotEmpty()) s += minOf(0.2, ca.intersect(castOf(b).toSet()).size * 0.1)
    if (a.type == b.type) s += 0.08
    val ya = yearOf(a); val yb = yearOf(b)
    if (ya != null && yb != null && kotlin.math.abs(ya - yb) <= 5) s += 0.04
    return s
}

private class Cand(val name: String, val items: List<Title>, val weight: Double = 1.0, val personal: Boolean = false)

internal fun buildRows(data: List<Title>, all: List<Title>, page: Int, series: List<Seri>, pageType: String): List<HomeRow> {
    val rnd = Random(System.nanoTime())
    val out = ArrayList<HomeRow>()
    val byId = data.associateBy { it.id }

    // İzleme geçmişi
    val seen = LinkedHashSet<Int>()      // devam edilebilenler (en yeni en solda)
    val watched = LinkedHashSet<Int>()   // bitenler dahil tüm izlenenler (en yeni önce)
    for (p in Svc.prefs.allProgress().sortedByDescending { it.ts }) {
        val id = if (p.contentId.startsWith("t")) p.contentId.drop(1).toIntOrNull() else p.contentId.drop(1).substringBefore('_').toIntOrNull()
        if (id != null) watched.add(id)
        if (p.fraction >= 0.95f || p.posMs < 10_000) continue
        if (id != null) seen.add(id)
    }
    val cont = seen.mapNotNull { byId[it] }
    if (cont.isNotEmpty()) out.add(HomeRow("İzlemeye Devam Et", cont, 1))

    // Serileri keşfet: her açılışta karışık sıra
    if (page == 0 && series.isNotEmpty()) out.add(HomeRow("Serileri Keşfet", emptyList(), 3, series.shuffled(rnd)))

    // ---- aday satırlar ----
    val cands = ArrayList<Cand>()
    val watchedTitles = watched.mapNotNull { byId[it] }

    // Zevk profili: izlenenlerin etiketleri (yeni izlenen daha ağır)
    val taste = HashMap<String, Double>()
    watchedTitles.take(25).forEachIndexed { i, t ->
        val w = 1.0 / (1 + i * 0.12)
        for (g in t.tags) if (g.lowercase() !in VAGUE) taste[g] = (taste[g] ?: 0.0) + w
    }
    if (watchedTitles.isNotEmpty() && taste.isNotEmpty()) {
        val top = taste.entries.sortedByDescending { it.value }.take(4).map { it.key }
        val picks = data.filter { it.id !in watched }.map { t ->
            val sc = t.tags.sumOf { taste[it] ?: 0.0 } / (1.0 + t.tags.size * 0.35)
            t to sc
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(24).map { it.first }
        if (picks.size >= 6) cands.add(Cand("Senin İçin Seçtiklerimiz", picks, 3.0, true))
        // Son izlenene benzer
        val last = watchedTitles.first()
        val like = data.map { it to sim(last, it) }.filter { it.second > 0.35 }.sortedByDescending { it.second }.take(24).map { it.first }
        if (like.size >= 5) cands.add(Cand("${last.title} İzlediğin İçin", like, 3.0, true))
        if (top.isNotEmpty()) {
            val g = top.first()
            val sel = data.filter { g in it.tags && it.id !in watched }.shuffled(rnd).take(24)
            if (sel.size >= 5) cands.add(Cand("$g Sevenler İçin", sel, 2.0, true))
        }
    }

    // Sunucudaki özel listeler
    for (l in data.flatMap { it.lists }.distinct()) {
        val items = data.filter { l in it.lists }
        if (items.size >= 3) cands.add(Cand(l, items.shuffled(rnd).take(24), 1.6))
    }

    // Tür çiftleri ve tekil türler
    val tagCounts = data.flatMap { it.tags }.filter { it.lowercase() !in VAGUE }.groupingBy { it }.eachCount()
    val pairCounts = HashMap<Pair<String, String>, Int>()
    for (t in data) {
        val tg = t.tags.filter { it.lowercase() !in VAGUE }.distinct().sorted()
        for (i in tg.indices) for (j in i + 1 until tg.size) pairCounts[tg[i] to tg[j]] = (pairCounts[tg[i] to tg[j]] ?: 0) + 1
    }
    for ((pr, n) in pairCounts) {
        if (n < 4) continue
        val items = data.filter { pr.first in it.tags && pr.second in it.tags }
        cands.add(Cand("${pr.first} ve ${pr.second} Bir Arada", items.shuffled(rnd).take(24), 1.2))
    }
    for ((g, n) in tagCounts) {
        if (n < 5) continue
        cands.add(Cand("Popüler $g Yapımları", data.filter { g in it.tags }.shuffled(rnd).take(24), 0.8))
    }

    // Oyuncular
    val castCounts = data.flatMap { castOf(it).distinct() }.groupingBy { it }.eachCount()
    for ((name, n) in castCounts) {
        if (n < 3) continue
        cands.add(Cand("$name Filmografisi", data.filter { name in castOf(it) }.shuffled(rnd).take(24), 1.1))
    }

    // Dönemler
    val decades = data.mapNotNull { t -> yearOf(t)?.let { t to it } }
    for (d in listOf(1980, 1990, 2000, 2010, 2020)) {
        val items = decades.filter { it.second / 10 * 10 == d }.map { it.first }
        if (items.size >= 4) cands.add(Cand(if (d < 2000) "${d}'lerden Klasikler" else "${d}'ların En İyileri", items.sortedByDescending { it.imdb.toDoubleOrNull() ?: 0.0 }.take(24), 1.0))
    }
    val recent = decades.filter { it.second >= 2023 }.map { it.first }
    if (recent.size >= 4) cands.add(Cand("Son Yılların Yapımları", recent.sortedByDescending { yearOf(it) ?: 0 }.take(24), 1.0))

    // Puan / süre / dublaj
    val high = data.filter { (it.imdb.toDoubleOrNull() ?: 0.0) >= 8.0 }
    if (high.size >= 4) cands.add(Cand("Puanı Yüksek, Seveni Çok", high.sortedByDescending { it.imdb.toDoubleOrNull() ?: 0.0 }.take(24), 1.3))
    val short = data.filter { it.isMovie && (minutesOf(it) ?: 999) <= 100 }
    if (short.size >= 4) cands.add(Cand("Bir Oturuşta Biten Filmler", short.shuffled(rnd).take(24), 1.0))
    val oneSeason = data.filter { !it.isMovie && it.seasonCount == 1 }
    if (oneSeason.size >= 4) cands.add(Cand("Kısa ve Öz: Tek Sezonluk Yapımlar", oneSeason.shuffled(rnd).take(24), 1.0))
    val dub = data.filter { it.dubbed }
    if (dub.size >= 4) cands.add(Cand("Türkçe Dublajlı Yapımlar", dub.shuffled(rnd).take(24), 1.1))

    // ---- seçim: örtüşmeyen, ağırlıklı rastgele ----
    val chosen = ArrayList<Cand>()
    val usedSets = ArrayList<Set<Int>>()
    val pool = cands.filter { it.items.size >= 4 }.toMutableList()
    val personal = pool.filter { it.personal }.sortedByDescending { it.weight }
    val order = ArrayList<Cand>(personal)
    order.addAll(pool.filter { !it.personal }.map { it to it.weight * (0.4 + rnd.nextDouble()) }.sortedByDescending { it.second }.map { it.first })
    val names = HashSet<String>()
    for (c in order) {
        if (chosen.size >= 14) break
        if (!names.add(c.name)) continue
        val ids = c.items.map { it.id }.toSet()
        val overlapping = usedSets.any { u -> ids.intersect(u).size > ids.size * 0.5 }
        if (overlapping && !c.personal) continue
        chosen.add(c); usedSets.add(ids)
    }
    // Sitedeki yeni öneri motoru (ruh hâli, seri grupları, bölge, gizli hazineler…). Yetersiz kalırsa eski satırlar tamamlar.
    val favNow = xyz.dafmix.app.data.Favs.ids.value
    val lead = out.size + (if (data.any { it.id in favNow }) 1 else 0)
    val smart = try {
        Recommend.homeRows(data, watched.toList().reversed(), favNow.toList(), maxOf(10, 18 - lead), setOf("Yeni Eklenenler"))
    } catch (e: Exception) { emptyList() }
    val body = ArrayList<HomeRow>(smart.map { HomeRow(it.title, it.items, 0) })
    if (body.size < 8) {
        val have = body.map { it.name }.toMutableSet()
        for (c in chosen) if (body.size < 12 && have.add(c.name)) body.add(HomeRow(c.name, c.items, 0))
    }

    // Yakında Gelecekler / Yeni Eklenenler: her açılışta ayrı ayrı %25, yeri şansa bağlı
    val soonItems = all.filter { it.soon && (page == 0 || it.type == pageType) }
    val newItems = data.filter { it.isNew }.sortedByDescending { it.added }
    for ((nm, items) in listOf("Yakında Gelecekler" to soonItems, "Yeni Eklenenler" to newItems)) {
        if (items.isEmpty() || rnd.nextDouble() >= 0.25) continue
        body.add(rnd.nextInt(body.size + 1), HomeRow(nm, items, 0))
    }

    // Top 10 satırları (3 satırda bir araya girer)
    val tops = ArrayList<HomeRow>()
    val types = if (page == 0) listOf("series", "movie", "anime") else listOf(pageType)
    for (ty in types) {
        val items = data.filter { it.type == ty && it.top10 > 0 }.sortedBy { it.top10 }
        if (items.isNotEmpty()) tops.add(HomeRow("Türkiye'de Bugün Top 10 ${TYPE_LABEL[ty]} Listesi", items, 2))
    }
    val merged = ArrayList<HomeRow>(body)
    var at = 1
    for (tp in tops) {
        merged.add(minOf(at, merged.size), tp)
        at = minOf(at, merged.size - 1) + 3
    }
    out.addAll(merged)
    return out
}
