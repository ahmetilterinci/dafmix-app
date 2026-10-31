package xyz.dafmix.app.ui

import xyz.dafmix.app.data.Title
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** Sitedeki öneri ve ana sayfa satır motorunun (public/js/recommend.js) Kotlin karşılığı.
 *  Her içerik; etiket, tür kavramı, açıklama teması, oyuncu, bölge, dönem ve puandan oluşan IDF ağırlıklı bir özellik vektörüne çevrilir.
 *  Satırlar: kişisel, ruh hâli, tür birleşimi, tür+tip, tema, oyuncu, dönem/puan, süre/format, bölge, seri, ham etiket.
 *  Açgözlü seçici örtüşmeyi, aynı kavramın tekrarını ve bağdaşmaz türleri (çocuk ↔ korku) eler. */
internal data class RecRowOut(val title: String, val items: List<Title>, val tag: String?, val family: String, val personal: Boolean)

internal object Recommend {
    private class Concept(val id: String, val re: Regex, val label: String)
    private class Theme(val id: String, val re: Regex, val label: String)
    private class Mood(
        val id: String, val titles: List<String>, val need: List<String>, val plus: Map<String, Double>,
        val ban: List<String>, val night: Boolean = false, val morning: Boolean = false,
    )

    private val CONCEPTS = listOf(
        Concept("komedi", Regex("komedi|comedy|mizah|gulmece"), "Komedi"),
        Concept("dram", Regex("\\bdram|drama|trajedi|melodram"), "Dram"),
        Concept("aksiyon", Regex("aksiyon|action|dovus"), "Aksiyon"),
        Concept("macera", Regex("macera|adventure|hayatta kalma|survival"), "Macera"),
        Concept("romantik", Regex("romantik|romance|\\bask\\b|aski"), "Romantik"),
        Concept("gerilim", Regex("gerilim|thriller"), "Gerilim"),
        Concept("gizem", Regex("gizem|mystery|dedektif|polisiye"), "Gizem"),
        Concept("korku", Regex("korku|horror|\\bfnaf\\b|dehset"), "Korku"),
        Concept("bilimkurgu", Regex("sci-?fi|bilim kurgu|cyberpunk|distopik|uzay|robot"), "Bilim Kurgu"),
        Concept("fantastik", Regex("fantez|fantasti|\\bbuyu\\b|epic|efsane"), "Fantastik"),
        Concept("suc", Regex("\\bsuc\\b|crime|mafya|uyusturucu|kara film"), "Suç"),
        Concept("aile", Regex("\\baile|cocuk|family|\\bkids?\\b"), "Aile"),
        Concept("animasyon", Regex("animasyon|animation|\\banime\\b|webtoon"), "Animasyon"),
        Concept("tarih", Regex("tarih|history|donem|\\bepik\\b"), "Tarih"),
        Concept("savas", Regex("savas|\\bwar\\b|asker"), "Savaş"),
        Concept("muzikal", Regex("muzikal|musical|k-pop|\\bmuzik\\b"), "Müzikal"),
        Concept("spor", Regex("\\bspor\\b|sport|futbol|basketbol"), "Spor"),
        Concept("belgesel", Regex("dokumen|belgesel|document"), "Belgesel"),
        Concept("psikolojik", Regex("piskolojik|psikolojik|psychological"), "Psikolojik"),
        Concept("genc", Regex("\\bgenc\\b|\\bokul\\b|\\blise\\b|teen"), "Gençlik"),
        Concept("superkahraman", Regex("super kahraman|super guc|superhero|marvel|\\bdc\\b"), "Süper Kahraman"),
        Concept("karanlik", Regex("karanlik|\\bdark\\b|noir|agresif"), "Karanlık"),
    )
    private val REGIONS = listOf(
        Concept("kore", Regex("\\bkore|k-drama|k-pop"), "Kore"), Concept("japon", Regex("japon|\\banime\\b|manga"), "Japon"),
        Concept("cin", Regex("\\bcin\\b|cince|c-drama"), "Çin"), Concept("turk", Regex("\\bturk|yerli|turkiye"), "Türk"),
        Concept("italyan", Regex("italyan"), "İtalyan"), Concept("hint", Regex("\\bhint|bollywood"), "Hint"),
        Concept("ispanyol", Regex("ispanyol|latin"), "İspanyol"), Concept("fransiz", Regex("fransiz"), "Fransız"),
        Concept("ingiliz", Regex("ingiliz|\\buk\\b"), "İngiliz"),
    )
    private val THEMES = listOf(
        Theme("okul", Regex("\\bokul|\\blise\\b|ogrenci|akademi|universite"), "Okul Hayatı"),
        Theme("uzay", Regex("\\buzay|galaksi|gezegen|astronot"), "Uzay Macerası"),
        Theme("zombi", Regex("zombi|kiyamet|salgin|virus"), "Kıyamet ve Hayatta Kalma"),
        Theme("intikam", Regex("intikam|\\boc\\b|\\boc alma"), "İntikam Hikâyeleri"),
        Theme("gercek", Regex("gercek bir hikaye|gercek olay|gercek hayat|biyografi|hayatindan|gercekten yasan"), "Gerçek Hikâyelerden"),
        Theme("polis", Regex("dedektif|polis|cinayet|sorusturma|katil|dedektiflik"), "Cinayet ve Soruşturma"),
        Theme("hastane", Regex("doktor|hastane|cerrah|hekim"), "Hastane Hikâyeleri"),
        Theme("mafya", Regex("mafya|gangster|kartel|\\bcete\\b"), "Mafya ve Yeraltı"),
        Theme("buyu", Regex("\\bbuyu|buyucu|sihir|cadi"), "Büyü Dünyası"),
        Theme("zaman", Regex("zaman yolculugu|zamanda yolculuk|gecmise|gelecege"), "Zaman Yolculuğu"),
        Theme("isdunyasi", Regex("sirket|is dunyasi|patron|ofis|girisim"), "İş Dünyası"),
        Theme("saray", Regex("saray|kralice|\\bkral\\b|imparator|prens\\b|prenses"), "Saray ve Taht"),
        Theme("dovus", Regex("samuray|ninja|dovus sanat|turnuva|savasci"), "Savaşçılar"),
        Theme("oyun", Regex("sanal|oyun dunyasi|mmorpg|seviye atla"), "Oyun Dünyası"),
        Theme("deniz", Regex("korsan|gemi|okyanus|\\bdeniz\\b"), "Denizler ve Korsanlar"),
        Theme("yemek", Regex("yemek|sef\\b|restoran|mutfak"), "Mutfaktan Hikâyeler"),
    )
    private val BAN = mapOf(
        "aile" to listOf("korku", "gerilim", "suc", "savas", "karanlik"),
        "korku" to listOf("aile", "muzikal"),
    )
    private val CONCEPT_LABEL = CONCEPTS.associate { it.id to it.label }
    private val THEME_LABEL = THEMES.associate { it.id to it.label }
    private val REGION_LABEL = REGIONS.associate { it.id to it.label }
    private val FAMILY_HARD = listOf("korku", "gerilim", "suc", "savas")

    private val MOODS = listOf(
        Mood("guldur", listOf("Gülmekten Kırılacaksın", "Moral Depolayan Komediler", "Kahkaha Garantili"), listOf("komedi"), mapOf("komedi" to 1.0, "romantik" to 0.15, "aile" to 0.1), listOf("korku", "savas", "gerilim", "karanlik")),
        Mood("gozyasi", listOf("Mendilleri Hazırla", "Duygu Yüklü Dramalar", "İçine Dokunan Hikâyeler"), listOf("dram"), mapOf("dram" to 1.0, "romantik" to 0.5, "psikolojik" to 0.2), listOf("komedi", "korku", "aksiyon", "superkahraman")),
        Mood("adrenalin", listOf("Adrenalin Zirvede", "Nefes Aldırmayan Aksiyon", "Tempo Hiç Düşmüyor"), listOf("aksiyon"), mapOf("aksiyon" to 1.0, "macera" to 0.4, "gerilim" to 0.3, "superkahraman" to 0.3), listOf("aile", "romantik", "muzikal")),
        Mood("gerilim", listOf("Gerilim Dozu Yüksek", "Tırnak Yedirenler", "Sonunu Tahmin Edemeyeceksin"), listOf("gerilim", "gizem", "psikolojik"), mapOf("gerilim" to 1.0, "gizem" to 0.7, "psikolojik" to 0.6, "suc" to 0.4), listOf("aile", "komedi", "muzikal")),
        Mood("karanlik", listOf("Karanlık ve Sürükleyici", "Gecenin Karanlığına Yakışanlar", "Rahatsız Eden Güzellik"), listOf("korku", "karanlik", "suc", "psikolojik"), mapOf("korku" to 1.0, "karanlik" to 0.8, "suc" to 0.6, "psikolojik" to 0.6, "gerilim" to 0.3), listOf("aile", "komedi", "muzikal", "romantik"), night = true),
        Mood("hayal", listOf("Hayal Gücünü Zorlayanlar", "Başka Dünyalara Yolculuk", "Fantastik Evrenler"), listOf("fantastik", "bilimkurgu"), mapOf("fantastik" to 1.0, "bilimkurgu" to 0.9, "macera" to 0.4), listOf("belgesel", "spor")),
        Mood("kalp", listOf("Kalbin Hızlanacak", "Aşk Kokan Hikâyeler", "Romantizm Dozu"), listOf("romantik"), mapOf("romantik" to 1.0, "dram" to 0.35, "komedi" to 0.3), listOf("korku", "savas", "gerilim", "suc")),
        Mood("icini", listOf("İçini Isıtan Yapımlar", "Hafif ve Keyifli Seçkiler", "Rahat Rahat İzlenenler"), listOf("aile", "komedi", "romantik"), mapOf("aile" to 0.8, "komedi" to 0.8, "romantik" to 0.4, "muzikal" to 0.3), listOf("korku", "gerilim", "suc", "savas", "karanlik"), morning = true),
        Mood("zihin", listOf("Zihin Açan Hikâyeler", "Kafa Karıştıran Bilimkurgu ve Psikolojik", "Düşündüren Yapımlar"), listOf("psikolojik", "bilimkurgu", "gizem"), mapOf("psikolojik" to 1.0, "bilimkurgu" to 0.8, "gizem" to 0.6, "gerilim" to 0.4), listOf("aile", "komedi", "muzikal")),
        Mood("epik", listOf("Destansı Yolculuklar", "Tarihin ve Efsanenin Gölgesinde", "Epik Hikâyeler"), listOf("tarih", "savas", "fantastik", "macera"), mapOf("tarih" to 1.0, "savas" to 0.8, "fantastik" to 0.6, "macera" to 0.5, "aksiyon" to 0.3), listOf("komedi", "muzikal")),
        Mood("genclik", listOf("Gençlik Başımda Duman", "Okul Koridorlarından", "Gençliğin Tadı"), listOf("genc"), mapOf("genc" to 1.0, "romantik" to 0.4, "komedi" to 0.3, "dram" to 0.3), listOf("korku", "savas")),
        Mood("kahraman", listOf("Kahramanlar Sahnede", "Süper Güçler Konuşuyor"), listOf("superkahraman"), mapOf("superkahraman" to 1.0, "aksiyon" to 0.6, "bilimkurgu" to 0.3), listOf("romantik", "muzikal")),
    )
    private val COMBO_TITLES = mapOf(
        "komedi+romantik" to listOf("Romantik Komediler", "Aşk ve Kahkaha"), "gizem+komedi" to listOf("Komedi ve Gizem Bir Arada", "Gülerek Çöz Gizemi"),
        "aksiyon+komedi" to listOf("Aksiyon-Komedi Karması"), "dram+romantik" to listOf("Aşk ve Özlem", "Acıtan Aşk Hikâyeleri"),
        "aksiyon+bilimkurgu" to listOf("Bilim Kurgu Aksiyonları"), "fantastik+macera" to listOf("Fantastik Maceralar"),
        "gerilim+gizem" to listOf("Gizem Dolu Gerilimler"), "gerilim+suc" to listOf("Suç ve Gerilim Hattı"), "dram+suc" to listOf("Sokakların Dramı"),
        "dram+tarih" to listOf("Tarihin İçinden Dramalar"), "gizem+korku" to listOf("Korku ve Gizem Gecesi"), "aile+komedi" to listOf("Aile Boyu Gülüş"),
        "aile+animasyon" to listOf("Çocuklarla İzlenecekler"), "animasyon+macera" to listOf("Animasyon Maceraları"),
    )

    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9+\\- ]+")
    private val SPACES = Regex("\\s+")
    fun norm(s: String?): String {
        val t = (s ?: "").lowercase(Locale("tr")).replace('ı', 'i')
        return NON_ALNUM.replace(DIACRITICS.replace(Normalizer.normalize(t, Normalizer.Form.NFD), ""), " ").let { SPACES.replace(it, " ").trim() }
    }

    private fun minutesOf(s: String): Int {
        val t = norm(s); var m = 0
        Regex("(\\d+)\\s*saat").find(t)?.let { m += it.groupValues[1].toInt() * 60 }
        Regex("(\\d+)\\s*(dakika|dk)").find(t)?.let { m += it.groupValues[1].toInt() }
        return m
    }

    private class R(val item: Title) {
        val id: Int = item.id
        val type: String = item.type
        val tagSet: Set<String> = item.tags.map { it.lowercase() }.toSet()
        val c = HashMap<String, Double>()
        val th = HashSet<String>()
        val rg = HashSet<String>()
        val cast: List<String> = item.cast.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val castN: List<String> = cast.map { norm(it) }
        val year: Int = Regex("(19|20)\\d{2}").find(item.year)?.value?.toIntOrNull() ?: 0
        val imdb: Double = item.imdb.replace(',', '.').toDoubleOrNull() ?: 0.0
        val mins: Int = if (item.type == "movie") minutesOf(item.time) else 0
        val seasons: Int = item.seasonCount
        val dubbed: Boolean = item.dubbed
        val q4k: Boolean = item.quality.contains("4k", ignoreCase = true)
        val top10: Int = item.top10
        val feats = HashMap<String, Double>()
        var nrmV = 1.0
        var tot = 1.0
        fun cv(k: String): Double = c[k] ?: 0.0
    }

    private class Index(val rows: List<R>, val byId: Map<Int, R>, val idf: (String) -> Double, val franchises: List<Pair<String, List<R>>>)

    private fun build(catalog: List<Title>): Index {
        val df = HashMap<String, Int>()
        val rows = catalog.map { item ->
            val r = R(item)
            val tagText = item.tags.map { norm(it) }
            for (tt in tagText) {
                for (cn in CONCEPTS) if (cn.re.containsMatchIn(tt)) r.c[cn.id] = 1.0
                for (rg in REGIONS) if (rg.re.containsMatchIn(tt)) r.rg.add(rg.id)
            }
            if (item.type == "anime") { r.c["animasyon"] = r.c["animasyon"] ?: 1.0; r.rg.add("japon") }
            val dt = norm("${item.title} ${item.desc}")
            for (th in THEMES) if (th.re.containsMatchIn(dt)) r.th.add(th.id)
            for (cn in CONCEPTS) {
                if (!r.c.containsKey(cn.id) && cn.id in WEAK && cn.re.containsMatchIn(dt)) r.c[cn.id] = 0.4
            }
            val keys = ArrayList<Pair<String, Double>>()
            r.tagSet.forEach { keys.add("t:$it" to 1.0) }
            r.c.forEach { (id, w) -> keys.add("c:$id" to 1.2 * w) }
            r.th.forEach { keys.add("h:$it" to 0.7) }
            r.castN.take(8).forEachIndexed { i, n -> keys.add("a:$n" to if (i < 3) 1.6 else 1.0) }
            r.rg.forEach { keys.add("r:$it" to 0.9) }
            keys.add("y:${item.type}" to 0.8)
            if (r.year > 0) keys.add("d:${r.year / 5 * 5}" to 0.35)
            for ((k, w) in keys) { r.feats[k] = w; df[k] = (df[k] ?: 0) + 1 }
            r
        }
        val n = max(1, rows.size)
        val idf = { k: String -> ln(1.0 + n.toDouble() / (df[k] ?: 1)) }
        for (r in rows) {
            var s = 0.0; var t = 0.0
            for (k in r.feats.keys.toList()) { val v = r.feats[k]!! * idf(k); r.feats[k] = v; s += v * v; t += v }
            r.nrmV = sqrt(s).takeIf { it > 0 } ?: 1.0
            r.tot = if (t > 0) t else 1.0
        }
        val groups = LinkedHashMap<String, MutableList<R>>()
        for (r in rows) {
            val b = baseOf(r.item.title)
            if (b.length >= 4) groups.getOrPut(b) { ArrayList() }.add(r)
        }
        val fr = groups.entries.filter { it.value.size >= 3 }.map { it.key to it.value.toList() }
        return Index(rows, rows.associateBy { it.id }, idf, fr)
    }

    private val WEAK = setOf("korku", "komedi", "romantik", "gerilim", "savas", "bilimkurgu", "suc", "tarih", "spor")
    private val RE_BASE_WORDS = Regex("\\b(bolum|part|sezon|season|film|movie|the)\\b")
    private val RE_BASE_CUT = Regex("[:\\-–(].*$")
    private val RE_BASE_NUM = Regex("\\b[ivx]+\\b|\\d+")
    private fun baseOf(t: String): String {
        var s = norm(t)
        s = RE_BASE_WORDS.replace(s, " ")
        s = RE_BASE_CUT.replace(s, "")
        s = RE_BASE_NUM.replace(s, " ")
        return SPACES.replace(s, " ").trim()
    }

    private var cache: Index? = null
    private var cacheKey: List<Int> = emptyList()
    private fun index(catalog: List<Title>): Index {
        val key = catalog.map { it.id }
        val c = cache
        if (c != null && key == cacheKey) return c
        return build(catalog).also { cache = it; cacheKey = key }
    }

    private fun banned(a: R, b: R): Boolean {
        for ((x, list) in BAN) if (a.cv(x) >= 1.0 && list.any { b.cv(it) >= 1.0 }) return true
        return false
    }

    private fun cosine(av: Map<String, Double>, an: Double, bv: Map<String, Double>, bn: Double): Double {
        var dot = 0.0
        val (s, l) = if (av.size < bv.size) av to bv else bv to av
        for ((k, w) in s) { val o = l[k]; if (o != null) dot += w * o }
        return dot / (an * bn)
    }

    private fun pairScore(a: R, b: R): Double {
        var sh = 0.0
        val (s, l) = if (a.feats.size < b.feats.size) a.feats to b.feats else b.feats to a.feats
        for ((k, w) in s) { val o = l[k]; if (o != null) sh += min(w, o) }
        if (sh == 0.0) return 0.0
        var sc = 0.7 * (sh / a.tot) + 0.3 * (sh / b.tot)
        if (a.year > 0 && b.year > 0) sc += max(0.0, 0.05 - abs(a.year - b.year) * 0.005)
        sc += if (b.imdb > 0) (b.imdb / 10) * 0.04 else 0.0
        if (banned(a, b)) sc *= 0.2
        return sc
    }

    private class Taste(
        val vec: Map<String, Double>, val nrm: Double, val seen: Set<Int>, val empty: Boolean,
        val fit: Map<R, Double>, val topActors: List<Pair<String, Double>>, val topConcepts: List<Pair<String, Double>>,
    )

    private fun tasteProfile(ix: Index, history: List<Int>, favIds: List<Int>): Taste {
        val vec = HashMap<String, Double>()
        fun add(id: Int, weight: Double) {
            val r = ix.byId[id] ?: return
            for ((k, w) in r.feats) vec[k] = (vec[k] ?: 0.0) + (w / r.nrmV) * weight
        }
        val h = history.takeLast(30)
        h.forEachIndexed { i, id -> add(id, 0.5 + (i.toDouble() / max(1, h.size)) * 1.0) }
        favIds.forEach { add(it, 2.0) }
        var s = 0.0
        for (v in vec.values) s += v * v
        val nrm = sqrt(s).takeIf { it > 0 } ?: 1.0
        val seen = (history + favIds).toSet()
        val fit = HashMap<R, Double>()
        if (vec.isNotEmpty()) {
            var mx = 0.0
            val raw = ix.rows.map { r -> val f = cosine(vec, nrm, r.feats, r.nrmV.pow(0.6)); mx = max(mx, f); r to f }
            for ((r, f) in raw) fit[r] = if (mx > 0) f / mx else 0.0
        }
        fun top(pre: String) = vec.entries.filter { it.key.startsWith(pre) }.sortedByDescending { it.value }
        return Taste(vec, nrm, seen, vec.isEmpty(), fit, top("a:").take(4).map { it.key.drop(2) to it.value }, top("c:").take(4).map { it.key.drop(2) to it.value })
    }

    private class Cand(
        val family: String, val title: String, val tag: String?, val concept: String?, val items: List<Title>,
        val ids: Set<Int>, val score: Double, val types: List<String>?, val personal: Boolean,
    )

    private fun pick(arr: List<String>, seed: Int): String = arr[abs(seed % arr.size)]
    private fun hash(s: String): Int { var h = 0; for (ch in s) h = h * 31 + ch.code; return h }
    private val TYPE_WORD = mapOf("movie" to listOf("Filmleri", "Film"), "series" to listOf("Dizileri", "Dizi"), "anime" to listOf("Animeleri", "Anime"))

    /** history: izleme geçmişi, en eski → en yeni; favIds: listedekiler. exclude: bu başlıklar kullanılmaz */
    fun homeRows(catalog: List<Title>, history: List<Int>, favIds: List<Int>, count: Int, exclude: Set<String> = emptySet()): List<RecRowOut> {
        if (catalog.size < 6) return emptyList()
        val ix = index(catalog)
        val taste = tasteProfile(ix, history, favIds)
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY); val dow = now.get(Calendar.DAY_OF_WEEK) - 1; val dom = now.get(Calendar.DAY_OF_MONTH)
        val thisYear = now.get(Calendar.YEAR)
        val isNight = hour >= 23 || hour < 5; val isMorning = hour in 6..10
        val isWeekend = dow == 0 || dow == 6 || (dow == 5 && hour >= 17)
        val rnd = Random(System.nanoTime())
        val seen = taste.seen
        val cands = ArrayList<Cand>()
        val rows = ix.rows
        fun fitOf(r: R) = if (taste.empty) 0.0 else taste.fit[r] ?: 0.0

        fun make(
            id: String, family: String, title: String, items: List<Pair<R, Double>>, base: Double,
            minN: Int = 6, maxN: Int = 24, jitter: Double = 0.12, order: Comparator<R>? = null, keepSeen: Boolean = false,
            tag: String? = null, concept: String? = null, types: List<String>? = null, personal: Boolean = false,
            night: Boolean = false, morning: Boolean = false, weekend: Boolean = false,
        ) {
            val list = items.filter { it.second > 0 }.sortedByDescending { it.second }
            if (list.size < minN) return
            val top = list.take(max(maxN, 24) * 2).map { (r, fit) ->
                var sc = fit + (if (r.imdb > 0) (r.imdb / 10) * 0.25 else 0.1) + fitOf(r) * 0.6 + rnd.nextDouble() * jitter
                if (r.id in seen) sc -= if (keepSeen) 0.0 else 0.35
                r to sc
            }
            val sorted = if (order != null) top.sortedWith { a, b -> order.compare(a.first, b.first) } else top.sortedByDescending { it.second }
            val picked = sorted.take(maxN).map { it.first }
            if (picked.size < minN) return
            val head = picked.take(12)
            val aff = if (taste.empty) 0.0 else head.sumOf { fitOf(it) } / head.size
            val qual = head.sumOf { if (it.imdb > 0) it.imdb else 6.0 } / head.size / 10
            var score = base + aff * 2.2 + qual * 0.35 + rnd.nextDouble() * 0.25
            if (night && isNight) score += 0.5
            if (morning && isMorning) score += 0.3
            if (weekend && isWeekend) score += 0.45
            cands.add(Cand(family, title, tag, concept, picked.map { it.item }, picked.map { it.id }.toSet(), score, types, personal))
        }

        /* 1) kişisel */
        if (!taste.empty) {
            val rec = history.reversed()
            val used = HashSet<Int>()
            for (hid in rec.take(8)) {
                if (used.size >= 2) break
                val a = ix.byId[hid] ?: continue
                if (a.id in used) continue
                val items = rows.filter { it !== a && it.id !in seen }.map { it to pairScore(a, it) }.filter { it.second > 0.1 }
                if (items.size >= 6) {
                    used.add(a.id)
                    make("b${a.id}", "personal", pick(listOf("${a.item.title} adlı yapımı izleyenlere öneriler", "${a.item.title} tadında yapımlar", "${a.item.title} sevenler bunlara bayılıyor"), hash(a.id.toString())), items, 1.0, jitter = 0.05, personal = true)
                }
            }
            for (fid in favIds.shuffled(rnd).take(2)) {
                val a = ix.byId[fid] ?: continue
                if (a.id in used) continue
                val items = rows.filter { it !== a && it.id !in seen }.map { it to pairScore(a, it) }.filter { it.second > 0.1 }
                if (items.size >= 6) {
                    used.add(a.id)
                    make("f${a.id}", "personal", "${a.item.title} listendeyse bunlara da bir bak", items, 0.9, jitter = 0.05, personal = true)
                }
            }
            taste.topActors.take(2).forEach { (name, w) ->
                val items = rows.filter { name in it.castN && it.id !in seen }.map { it to (1.0 + if (it.castN.indexOf(name) < 3) 0.5 else 0.0) }
                val disp = rows.firstOrNull { name in it.castN }
                val nm = disp?.let { d -> d.cast.getOrNull(d.castN.indexOf(name)) } ?: name
                if (items.size >= 4 && w > 0.2) make("ta$name", "personal", "Sevdiğin Oyuncu $nm Başrolde", items, 1.0, minN = 4, personal = true)
            }
            taste.topConcepts.take(2).forEach { (cid, _) ->
                for (type in listOf("movie", "series", "anime")) {
                    val items = rows.filter { it.type == type && it.cv(cid) >= 1.0 && it.id !in seen }.map { it to 1.0 }
                    if (items.size >= 8) make("tc$cid$type", "personal", "Sevdiğin ${CONCEPT_LABEL[cid] ?: cid} ${TYPE_WORD[type]!![0]}", items, 0.75, types = listOf(type), concept = cid, personal = true)
                }
            }
        }

        /* 2) ruh hâli */
        for (m in MOODS) {
            val items = rows.map { r ->
                if (m.need.none { r.cv(it) >= 1.0 } || m.ban.any { r.cv(it) >= 1.0 }) r to 0.0
                else r to m.plus.entries.sumOf { (c, w) -> r.cv(c) * w }
            }
            make("m${m.id}", "mood", pick(m.titles, hash(m.id) + dom), items, 0.7, night = m.night, morning = m.morning, concept = m.need[0])
        }

        /* 3) tür birleşimleri */
        val cc = HashMap<String, Int>()
        for (r in rows) for ((c, w) in r.c) if (w >= 1.0) cc[c] = (cc[c] ?: 0) + 1
        val cids = cc.filter { it.value >= 6 && it.key != "animasyon" && it.key != "genc" }.keys.sorted()
        for (i in cids.indices) for (j in i + 1 until cids.size) {
            val (a, b) = listOf(cids[i], cids[j]).sorted()
            val items = rows.filter { it.cv(a) >= 1.0 && it.cv(b) >= 1.0 && !(it.cv("aile") >= 1.0 && (a in FAMILY_HARD || b in FAMILY_HARD)) }.map { it to 1.0 }
            if (items.size < 6) continue
            val key = "$a+$b"
            val t = COMBO_TITLES[key]?.let { pick(it, hash(key) + dom) } ?: "${CONCEPT_LABEL[a]} ve ${CONCEPT_LABEL[b]} Bir Arada"
            make("c$key", "combo", t, items, 0.65, concept = a)
        }

        /* 4) tür + tip */
        for (cid in cids) for (type in listOf("movie", "series", "anime")) {
            val items = rows.filter { it.type == type && it.cv(cid) >= 1.0 && !(it.cv("aile") >= 1.0 && cid in FAMILY_HARD) }.map { it to it.cv(cid) }
            if (items.size < 8) continue
            val l = CONCEPT_LABEL[cid] ?: cid; val w = TYPE_WORD[type]!!
            val t = pick(listOf("En İyi $l ${w[0]}", "$l Severlere ${w[1]} Önerileri", "Soluksuz İzlenen $l ${w[0]}"), hash(cid + type) + dom)
            make("t$cid$type", "genre", t, items, 0.5, concept = cid, types = listOf(type))
        }

        /* 5) açıklama temaları */
        for (th in THEMES) {
            val items = rows.filter { th.id in it.th && !(it.cv("aile") >= 1.0 && th.id in setOf("mafya", "polis", "zombi", "intikam")) }.map { it to 1.0 + it.imdb / 20 }
            if (items.size >= 6) make("h${th.id}", "theme", THEME_LABEL[th.id]!!, items, 0.55)
        }

        /* 6) oyuncular */
        val actorCount = HashMap<String, Int>()
        for (r in rows) r.castN.take(5).forEach { actorCount[it] = (actorCount[it] ?: 0) + 1 }
        actorCount.entries.filter { it.value >= 4 }.sortedByDescending { it.value }.take(12).forEach { (name, _) ->
            val items = rows.filter { name in it.castN.take(5) }.map { it to (1.0 + if (it.castN.indexOf(name) < 2) 0.5 else 0.0) }
            if (items.isEmpty()) return@forEach
            val disp = items[0].first
            val nm = disp.cast.getOrNull(disp.castN.indexOf(name)) ?: name
            make("a$name", "actor", pick(listOf("$nm Filmografisinden", "$nm Başrolde", "$nm Hayranlarına"), hash(name)), items, 0.6, minN = 4)
        }

        /* 7) dönem ve puan */
        fun yr(lo: Int, hi: Int, minImdb: Double) = rows.filter { it.year in lo..hi && it.imdb >= minImdb }.map { it to 0.5 + it.imdb / 20 }
        make("y90", "decade", "Nostalji: 90'lar ve Öncesinin Klasikleri", yr(1900, 1999, 6.5), 0.55)
        make("y00", "decade", "2000'lerden Unutulmayanlar", yr(2000, 2009, 6.8), 0.55)
        make("y10", "decade", "2010'ların Efsaneleri", yr(2010, 2019, 7.4), 0.5)
        make("ynew", "recent", pick(listOf("Yeni Çıkanlar", "Vizyondan Taze", "Bu Dönemin Yapımları"), dom),
            rows.filter { it.year >= thisYear - 1 }.map { it to 0.6 + (it.year - (thisYear - 2)) * 0.2 }, 0.75,
            order = Comparator<R> { a, b -> if (a.year != b.year) b.year.compareTo(a.year) else b.id.compareTo(a.id) }, jitter = 0.0)
        make("yadd", "recent", "Yeni Eklenenler", rows.sortedByDescending { it.id }.take(24).map { it to 1.0 }, 0.6, order = Comparator<R> { a, b -> b.id.compareTo(a.id) }, jitter = 0.0)
        make("p8", "quality", pick(listOf("IMDb 8 Üstü Seçkin Yapımlar", "Puanı Yüksek, Seveni Çok"), dom), rows.filter { it.imdb >= 8 }.map { it to it.imdb / 10 }, 0.6)
        make("gem", "quality", "Gözünden Kaçan Hazineler", rows.filter { it.imdb >= 7.3 && it.top10 == 0 && it.id !in seen }.map { it to 0.4 + it.imdb / 20 }, 0.6)

        /* 8) süre / format (bölüm sayısı uygulamada yok: sezon sayısına göre) */
        make("short", "length", "Bir Oturuşta Biten Filmler", rows.filter { it.type == "movie" && it.mins in 1..100 }.map { it to 1.0 }, 0.55, weekend = true)
        make("long", "length", "Uzun Soluklu Filmler", rows.filter { it.type == "movie" && it.mins >= 140 }.map { it to 1.0 }, 0.45)
        make("mini", "length", pick(listOf("Hafta Sonunda Bitirmelik Mini Diziler", "Kısa ve Öz: Tek Sezonluk Diziler"), dom), rows.filter { it.type == "series" && it.seasons == 1 }.map { it to 1.0 }, 0.6, weekend = true)
        make("mara", "length", "Maraton Yapmalık Uzun Diziler", rows.filter { it.type != "movie" && it.seasons >= 3 }.map { it to 1.0 }, 0.55, weekend = true)
        make("dub", "format", "Türkçe Dublajlı Yapımlar", rows.filter { it.dubbed }.map { it to 1.0 }, 0.6)
        make("q4k", "format", "4K Ultra HD ile İzle", rows.filter { it.q4k }.map { it to 1.0 }, 0.5)

        /* 9) bölge */
        for (rid in REGION_LABEL.keys) for ((type, w) in listOf("series" to "Dizileri", "movie" to "Filmleri", "anime" to "Animeleri")) {
            if (rid == "japon" && type != "anime") continue
            if (rid != "japon" && type == "anime") continue
            val items = rows.filter { it.type == type && rid in it.rg }.map { it to 0.5 + it.imdb / 20 }
            make("r$rid$type", "region", if (rid == "turk") "Yerli $w" else "${REGION_LABEL[rid]} $w", items, 0.5, minN = 7, types = listOf(type))
        }

        /* 10) seri / koleksiyonlar */
        for ((key, group) in ix.franchises) {
            val name = group.minByOrNull { it.item.title.length }!!.item.title.replace(Regex("\\s*[:\\-–(].*$"), "").replace(Regex("\\s+\\d+$"), "").trim()
            make("fr$key", "franchise", "$name Serisi", group.map { it to 1.0 + it.imdb / 20 }, 0.65, minN = 3,
                order = Comparator<R> { a, b -> if (a.year != b.year) a.year.compareTo(b.year) else a.id.compareTo(b.id) }, jitter = 0.0, keepSeen = true)
        }

        /* 11) ham etiketler + tür karmaları */
        val rawCount = HashMap<String, Int>()
        for (r in rows) r.tagSet.forEach { rawCount[it] = (rawCount[it] ?: 0) + 1 }
        val skipTag = Regex("^(gorev|agresif|epic|trajedi|dokuman|dokumen)$")
        val skipTag2 = Regex("serisi$|^anime filmi$|^webtoon")
        for ((t, n) in rawCount) {
            val nt = norm(t)
            if (n < 6 || REGIONS.any { it.re.containsMatchIn(nt) } || skipTag.containsMatchIn(nt) || skipTag2.containsMatchIn(nt)) continue
            val label = Regex("(^|\\s)\\S").replace(t) { it.value.uppercase(Locale("tr")) }
            val items = rows.filter { t in it.tagSet }.map { it to 1.0 }
            val cn = CONCEPTS.firstOrNull { it.re.containsMatchIn(nt) }
            if (cn != null && norm(label) == norm(cn.label)) continue
            val pc = HashMap<String, Int>()
            for ((r, _) in items) for ((c, w) in r.c) if (w >= 1.0) pc[c] = (pc[c] ?: 0) + 1
            val purity = (pc.values.maxOrNull() ?: 0).toDouble() / max(1, items.size)
            if (purity < 0.5) continue
            make("g$t", "tag", pick(listOf("$label Temalı Seçki", "Soluksuz İzlenen $label Yapımları", "$label Sevenlere"), hash(t) + dom), items, 0.45, tag = t, minN = 6)
        }
        for (cid in cids) {
            val items = rows.filter { it.cv(cid) >= 1.0 && !(it.cv("aile") >= 1.0 && cid in FAMILY_HARD) }.map { it to 1.0 }
            make("ga$cid", "genreAll", pick(listOf("${CONCEPT_LABEL[cid]} Seçkisi", "Bugünün ${CONCEPT_LABEL[cid]} Önerileri"), hash(cid) + dom), items, 0.4, concept = cid)
        }

        /* seçici: açgözlü, örtüşme ve tekrar cezalı */
        val cap = mapOf("personal" to 5, "mood" to 4, "combo" to 3, "genre" to 3, "theme" to 2, "actor" to 2, "decade" to 1, "recent" to 2, "quality" to 2, "length" to 2, "format" to 2, "region" to 1, "franchise" to 2, "tag" to 2, "genreAll" to 2)
        val chosen = ArrayList<Cand>()
        val shown = HashMap<Int, Int>(); val famCount = HashMap<String, Int>(); val conceptCount = HashMap<String, Int>(); val typeRun = ArrayList<String>()
        val titles = HashSet<String>(exclude)
        val pool = ArrayList(cands)
        for ((ov, capAdd) in listOf(0.55 to 0, 0.7 to 1, 0.85 to 2)) {
            while (chosen.size < count && pool.isNotEmpty()) {
                var best = -1; var bestScore = -1e9
                for (i in pool.indices) {
                    val c = pool[i]
                    if (c.title in titles || (famCount[c.family] ?: 0) >= (cap[c.family] ?: 2) + capAdd) continue
                    val overlap = c.ids.count { it in shown }
                    val frac = overlap.toDouble() / c.ids.size
                    if (frac > ov) continue
                    var s = c.score - frac * 1.4
                    val cn = c.concept
                    if (cn != null && (conceptCount[cn] ?: 0) >= 2) s -= 0.6 * ((conceptCount[cn] ?: 0) - 1)
                    val ty = c.types
                    if (ty != null && typeRun.size >= 2 && typeRun.takeLast(2).all { it == ty[0] }) s -= 0.4
                    if (s > bestScore) { bestScore = s; best = i }
                }
                if (best < 0) break
                val c = pool.removeAt(best)
                chosen.add(c); titles.add(c.title); famCount[c.family] = (famCount[c.family] ?: 0) + 1
                c.concept?.let { conceptCount[it] = (conceptCount[it] ?: 0) + 1 }
                typeRun.add(c.types?.get(0) ?: "x")
                c.ids.forEach { shown[it] = (shown[it] ?: 0) + 1 }
            }
        }
        return chosen.map { RecRowOut(it.title, it.items, it.tag, it.family, it.personal) }
    }
}
