package com.patr0n

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class Dizifun : MainAPI() {
    override var mainUrl = "https://dizifun2.com"
    override var name = "Dizifun"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    companion object {
        // User agent for all requests
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    // Standart Request Headers - Tüm isteklerde kullanılacak
    private val defaultHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Connection" to "keep-alive",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",
        "Upgrade-Insecure-Requests" to "1"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/diziler" to "Diziler",
        "$mainUrl/filmler" to "Filmler",
        "$mainUrl/netflix" to "Netflix",
        "$mainUrl/disney" to "Disney+",
        "$mainUrl/primevideo" to "Prime Video",
        "$mainUrl/hbomax" to "HBO Max",
        "$mainUrl/exxen" to "Exxen",
        "$mainUrl/tabii-dizileri" to "Tabii",
        "$mainUrl/blutv" to "BluTV",
        "$mainUrl/todtv" to "TodTV",
        "$mainUrl/gain" to "Gain",
        "$mainUrl/hulu" to "Hulu",
        "$mainUrl/paramount" to "Paramount+",
        "$mainUrl/unutulmaz" to "Unutulmaz Diziler"
    )

    // Debug için isteklerin durumunu logla
    private fun logResponse(url: String, response: String) {
        if (response.length > 200) {
            Log.d(this@Dizifun.name, "Response for $url: ${response.substring(0, 200)}...")
        } else {
            Log.d(this@Dizifun.name, "Response for $url: $response")
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        Log.d(this@Dizifun.name, "Loading main page: ${request.data}")
        
        // Sayfayı yükle
        val response = app.get(
            request.data,
            headers = defaultHeaders,
            referer = mainUrl,
            cacheTime = 0
        )
        
        val document = response.document
        val home = ArrayList<SearchResponse>()
        
        // Kategoriye göre uygun seçicileri tanımla
        val selectors = when {
            request.name.contains("Netflix") || request.name.contains("Disney") || 
            request.name.contains("Prime") || request.name.contains("HBO") || 
            request.name.contains("Exxen") || request.name.contains("BluTV") || 
            request.name.contains("Gain") || request.name.contains("Hulu") ||
            request.name.contains("Paramount") || request.name.contains("Tabii") ||
            request.name.contains("TodTV") || request.name.contains("Unutulmaz") -> {
                // Platform sayfaları için özel seçici
                "div.uk-overlay.uk-overlay-hover, article, div.col-md-2"
            }
            else -> {
                // Genel sayfalar için tüm seçiciler
                "div.uk-overlay.uk-overlay-hover, div.col-md-2, div.movies_recent-item, div.editor_sec-item, " +
                "div.featuredseries_recent-item, div.plattab_recent-item, div.episode-item, article"
            }
        }
        
        // Seçilen tüm içerikleri topla
        val allContentItems = document.select(selectors)
        Log.d(this@Dizifun.name, "Found ${allContentItems.size} content items in ${request.name}")
        
        allContentItems.forEach { element ->
            try {
                // Başlık bulma
                val title = extractTitle(element)
                if (!title.isNullOrBlank()) {
                    // URL bulma
                    val href = extractHref(element)
                    if (!href.isNullOrBlank()) {
                        // Poster URL bulma
                        val posterUrl = extractPosterUrl(element)
                        
                        // Yıl bulma
                        val year = extractYear(element)
                        
                        // İçerik tipi belirleme
                        val type = determineContentType(href, title)
                        
                        home.add(newMovieSearchResponse(title, href, type) {
                            this.posterUrl = posterUrl
                            this.year = year
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(this@Dizifun.name, "Error processing content item: ${e.message}")
            }
        }
        
        Log.d(this@Dizifun.name, "Total processed items for ${request.name}: ${home.size}")
        return newHomePageResponse(request.name, home)
    }
    
    // Başlık çıkarma fonksiyonu
    private fun extractTitle(element: Element): String? {
        return when {
            element.selectFirst("h3.editor_sec-title, h3.movies_recent-title, h3.featuredseries_recent-title, h5.uk-panel-title") != null -> 
                element.selectFirst("h3.editor_sec-title, h3.movies_recent-title, h3.featuredseries_recent-title, h5.uk-panel-title")?.text()?.trim()
            element.selectFirst("div.name") != null -> element.selectFirst("div.name")?.text()?.trim()
            element.selectFirst("a > strong") != null -> element.selectFirst("a > strong")?.text()?.trim()
            element.selectFirst("h1, h2, h3, h4, h5, h6") != null -> element.selectFirst("h1, h2, h3, h4, h5, h6")?.text()?.trim()
            element.selectFirst("strong") != null -> element.selectFirst("strong")?.text()?.trim()
            else -> {
                // Alternatif başlık bulma yöntemleri
                val img = element.selectFirst("img")
                if (img != null) {
                    img.attr("alt").takeIf { !it.isNullOrBlank() }
                } else {
                    // Eğer hiçbir başlık bulunamadıysa, doğrudan metin içeriğini dene
                    element.ownText().takeIf { !it.isBlank() }
                }
            }
        }
    }
    
    // URL çıkarma fonksiyonu
    private fun extractHref(element: Element): String? {
        val href = when {
            element.selectFirst("a.editor_sec-link, a.movies_recent-link, a.featuredseries_recent-link, a.plattab_recent-link") != null -> 
                element.selectFirst("a.editor_sec-link, a.movies_recent-link, a.featuredseries_recent-link, a.plattab_recent-link")?.attr("href")
            element.selectFirst("a.uk-position-cover") != null -> element.selectFirst("a.uk-position-cover")?.attr("href")
            element.selectFirst("a[href*=dizi], a[href*=film]") != null -> element.selectFirst("a[href*=dizi], a[href*=film]")?.attr("href")
            element.selectFirst("a") != null -> element.selectFirst("a")?.attr("href")
            element.tagName() == "a" -> element.attr("href")
            else -> null
        }
        
        return href?.let { fixUrlNull(it) }
    }
    
    // Poster URL çıkarma fonksiyonu
    private fun extractPosterUrl(element: Element): String? {
        val posterUrl = when {
            element.selectFirst("img.editor_sec-image, img.movies_recent-image, img.featuredseries_recent-image, img.plattab_recent-image") != null -> 
                element.selectFirst("img.editor_sec-image, img.movies_recent-image, img.featuredseries_recent-image, img.plattab_recent-image")?.attr("src")
            element.selectFirst("img") != null -> {
                val imgSrc = element.selectFirst("img")?.attr("src")
                // Data URI'leri filtrele
                if (imgSrc?.startsWith("data:") == true) null else imgSrc
            }
            else -> null
        }
        
        return posterUrl?.let { fixUrlNull(it) }
    }
    
    // Yıl çıkarma fonksiyonu
    private fun extractYear(element: Element): Int? {
        val yearText = element.text()
        return when {
            element.selectFirst("p.editor_sec-date, p.movies_recent-date, p.featuredseries_recent-date, p.plattab_recent-date") != null -> 
                element.selectFirst("p.editor_sec-date, p.movies_recent-date, p.featuredseries_recent-date, p.plattab_recent-date")?.text()?.trim()?.toIntOrNull()
            element.selectFirst("span.uk-display-block.uk-text-muted") != null -> element.selectFirst("span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
            element.selectFirst("div.date") != null -> element.selectFirst("div.date")?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
            yearText.contains(Regex("\\b\\d{4}\\b")) -> Regex("\\b(\\d{4})\\b").find(yearText)?.groupValues?.get(1)?.toIntOrNull()
            else -> null
        }
    }
    
    // İçerik tipini belirle
    private fun determineContentType(url: String, title: String): TvType {
        return if (url.contains("/dizi/") || url.contains("/diziler/") || url.contains("sezon") || url.contains("bolum") || 
            url.contains("netflix") || url.contains("disney") || url.contains("primevideo") || 
            url.contains("blutv") || url.contains("gain") || url.contains("exxen") || 
            url.contains("tabii-dizileri") || url.contains("hulu") || url.contains("todtv") || 
            url.contains("paramount") || url.contains("unutulmaz") || 
            (title.contains("Sezon", ignoreCase = true) && !title.contains("Film", ignoreCase = true))) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/arama?query=${java.net.URLEncoder.encode(query, "UTF-8")}"
        Log.d(this@Dizifun.name, "Searching: $searchUrl")
        
        val document = app.get(searchUrl, headers = defaultHeaders).document
        val searchResults = ArrayList<SearchResponse>()
        
        // Tüm olası içerik seçicileri
        val selectors = listOf(
            "div.col-md-2",
            "div.uk-overlay.uk-overlay-hover",
            "div.editor_sec-item",
            "div.movies_recent-item",
            "div.featuredseries_recent-item",
            "div.episode-item",
            "article",
            "div.platformmobile"
        )
        
        // Tüm seçicileri birleştir
        val allItemSelector = selectors.joinToString(", ")
        
        // Tüm içerikleri topla
        val allItems = document.select(allItemSelector)
        Log.d(this@Dizifun.name, "Found ${allItems.size} items in search results")
        
        allItems.forEach { element ->
            try {
                val title = extractTitle(element)
                if (!title.isNullOrBlank()) {
                    val href = extractHref(element)
                    if (!href.isNullOrBlank()) {
                        val posterUrl = extractPosterUrl(element)
                        val year = extractYear(element)
                        val type = determineContentType(href, title)
                        
                        searchResults.add(newMovieSearchResponse(title, href, type) {
                            this.posterUrl = posterUrl
                            this.year = year
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(this@Dizifun.name, "Error processing search item: ${e.message}")
            }
        }
        
        Log.d(this@Dizifun.name, "Total search results: ${searchResults.size}")
        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        Log.d(this@Dizifun.name, "Loading details: $url")
        
        try {
            val response = app.get(url, headers = defaultHeaders)
            val document = response.document
            
            // Başlığı bul
            var title = document.selectFirst("h1.text-bold, div.titlemob, div.head-title > h1, h1.title, .movie-title, .dizi-title")?.text()?.trim()
            Log.d(this@Dizifun.name, "Found title: $title")
            if (title.isNullOrBlank()) {
                Log.d(this@Dizifun.name, "Title not found, trying alternative selectors")
                title = document.selectFirst("h1, .title, .movie-title, .dizi-title, .head-title, .main-title")?.text()?.trim()
                if (title.isNullOrBlank()) {
                    Log.e(this@Dizifun.name, "No title found for $url")
                    return null
                }
            }
            
            // Posteri bul
            val poster = extractPosterFromPage(document)
            Log.d(this@Dizifun.name, "Found poster: $poster")
            
            // Yılı bul
            val year = extractYearFromPage(document)
            Log.d(this@Dizifun.name, "Found year: $year")
            
            // Açıklamayı bul
            val description = extractDescriptionFromPage(document)
            Log.d(this@Dizifun.name, "Found description: ${description?.take(50)}...")
            
            // İçerik tipini belirle
            val type = determineContentType(url, title)
            Log.d(this@Dizifun.name, "Content type: $type")
            
            // Ek bilgileri bul
            val rating = document.selectFirst("span.rating, span.rate, .imdb-score")?.text()?.toRatingInt()
            val duration = extractDurationFromPage(document)
            
            // Kategori bilgilerini bul
            val tags = document.select("div.genres a, ul.subnav li a, div.series-info, div.content_data span, .genres span").map { 
                it.text().trim() 
            }.filter { it.isNotBlank() }
            Log.d(this@Dizifun.name, "Found tags: $tags")
            
            // Oyuncuları bul
            val actors = document.select("div.actors-container div.actor-card, div.oyuncular span, .cast div, .actor").mapNotNull {
                val actorName = it.selectFirst("span.actor-name")?.text()?.trim() 
                    ?: it.selectFirst("span.name")?.text()?.trim()
                    ?: it.text().trim()
                    ?: return@mapNotNull null
                    
                val actorImg = fixUrlNull(it.selectFirst("img")?.attr("src"))
                
                Actor(actorName, actorImg)
            }
            Log.d(this@Dizifun.name, "Found ${actors.size} actors")
            
            // Fragmanı bul
            val trailer = document.selectFirst("iframe[src*=youtube], a.trailer-button, [data-trailer]")?.let {
                val trailerUrl = it.attr("src").takeIf { s -> s.isNotBlank() } 
                    ?: it.attr("data-trailer").takeIf { s -> s.isNotBlank() } 
                    ?: it.attr("href").takeIf { s -> s.isNotBlank() }
                trailerUrl?.let { url -> fixUrl(url) }
            }
            Log.d(this@Dizifun.name, "Found trailer: $trailer")

            // Dizi ise bölümleri bulalım, film ise doğrudan yükleyelim
            return if (type == TvType.TvSeries) {
                val episodes = ArrayList<Episode>()
                
                // Sezon ve bölüm yapısını analiz et
                extractEpisodes(document, url, episodes)
                
                Log.d(this@Dizifun.name, "Total episodes found: ${episodes.size}")
                
                newTvSeriesLoadResponse(title, url, type, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.rating = rating
                    this.duration = duration
                    addActors(actors)
                    if (trailer != null) addTrailer(trailer)
                }
            } else {
                newMovieLoadResponse(title, url, type, url) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.rating = rating
                    this.duration = duration
                    addActors(actors)
                    if (trailer != null) addTrailer(trailer)
                }
            }
        } catch (e: Exception) {
            Log.e(this@Dizifun.name, "Error loading detail page: ${e.message}")
            return null
        }
    }
    
    // Detay sayfasından poster çıkarma
    private fun extractPosterFromPage(document: Document): String? {
        return document.selectFirst("div.media-cover img, img.imgboyut, img.responsive-img, div.platformmobile img, .poster img, .thumbnail img")?.attr("src")
            ?.takeIf { !it.startsWith("data:") }
            ?.let { fixUrlNull(it) }
    }
    
    // Detay sayfasından yıl çıkarma
    private fun extractYearFromPage(document: Document): Int? {
        val yearText = document.select("ul.subnav li, div.content_data, .series-info, .movie-info").find { 
            it.text().contains("Yıl:") || it.text().contains("Dizi Yılı:") || it.text().contains("Film Yılı:") 
        }?.text() ?: ""
        
        return yearText.substringAfter("Yıl:").substringAfter("Dizi Yılı:").substringAfter("Film Yılı:").trim().toIntOrNull()
            ?: Regex("\\b(\\d{4})\\b").find(document.text())?.groupValues?.get(1)?.toIntOrNull()
    }
    
    // Detay sayfasından açıklama çıkarma
    private fun extractDescriptionFromPage(document: Document): String? {
        return document.selectFirst("p.text-muted, div.descmobi, div.content_data > p, .description, .plot")?.text()?.trim()
            ?: document.select("div.content_data, div.detail-text, div.description").firstOrNull()?.text()?.trim()
    }
    
    // Detay sayfasından süre çıkarma
    private fun extractDurationFromPage(document: Document): Int? {
        val durationText = document.select("span.runtime, div.content_data, .duration, .info").text()
        val durationRegex = Regex("(\\d+)\\s*(?:dk|min)")
        return durationRegex.find(durationText)?.groupValues?.get(1)?.toIntOrNull()
    }
    
    // Bölümleri çıkarma
    private suspend fun extractEpisodes(document: Document, url: String, episodes: ArrayList<Episode>) {
        // 1. Sezon seçicileri bulma
        val seasonSelectors = document.select("div.sezon-select, div.season-select, select.season-change, div.seasons-bk, .seasons, .season-buttons")
        Log.d(this@Dizifun.name, "Sezon seçicileri: ${seasonSelectors.size}")
        
        // 2. Sezon butonları var mı?
        if (seasonSelectors.isNotEmpty()) {
            Log.d(this@Dizifun.name, "Sezon seçicileri bulundu")
            
            // Sezon butonlarını/dropdown seçeneklerini bul
            val seasonButtons = seasonSelectors.select("a, option, button, span, li").filter { 
                it.text().contains("Sezon", ignoreCase = true) || 
                it.text().matches(Regex("S\\d+", RegexOption.IGNORE_CASE)) ||
                it.attr("data-season").isNotBlank()
            }
            
            Log.d(this@Dizifun.name, "Sezon butonları: ${seasonButtons.size}")
            
            if (seasonButtons.isNotEmpty()) {
                // Her bir sezon için bölümleri işle
                seasonButtons.forEachIndexed { index, seasonButton ->
                    val seasonText = seasonButton.text().trim()
                    // Sezon numarasını çıkar
                    val seasonId = seasonButton.attr("data-season").toIntOrNull()
                        ?: seasonText.replace(Regex("[^0-9]"), "").toIntOrNull()
                        ?: (index + 1)
                    
                    Log.d(this@Dizifun.name, "İşleniyor: $seasonText (Sezon $seasonId)")
                    
                    // Her sezon düğmesine ait bölüm listesini bul
                    var seasonEpisodes = ArrayList<Element>()
                    
                    // Sezon butonunda URL varsa, o sayfayı yükle
                    val seasonHref = seasonButton.attr("href").takeIf { it.isNotBlank() }
                    if (seasonHref != null && !seasonHref.startsWith("#")) {
                        val seasonUrl = if (seasonHref.startsWith("http")) seasonHref else fixUrl(seasonHref)
                        val seasonDoc = app.get(seasonUrl, headers = defaultHeaders).document
                        seasonEpisodes = ArrayList(seasonDoc.select("a[href*=bolum], a.episode-button, div.episode-button, a[href*=izle], li.episode a, a.btn-episode, a.dizi-parts"))
                    } else {
                        // Buton ID veya data-target attribute içeriyor mu?
                        val targetId = seasonButton.attr("data-target").takeIf { it.isNotBlank() }
                            ?: seasonButton.attr("href").takeIf { it.startsWith("#") }
                        
                        if (targetId != null) {
                            // Target ID ile eşleşen konteyneri bul
                            val targetSelector = targetId.removePrefix("#")
                            seasonEpisodes = ArrayList(document.select("$targetSelector a[href*=bolum], $targetSelector a.episode-button, $targetSelector a[href*=izle]"))
                        }
                        
                        // Hala bulunamadıysa, genel sezon ID'sine göre bul
                        if (seasonEpisodes.isEmpty()) {
                            seasonEpisodes = ArrayList(document.select("div[id=season$seasonId], div[id=sezon$seasonId], div.seasonContent$seasonId, div.tabcontent$seasonId, .season-$seasonId")
                                .select("a[href*=bolum], a.episode-button, a[href*=izle], li.episode a, a.btn-episode, a.dizi-parts"))
                        }
                        
                        // Yine de bulunamadıysa, bölümlerde data-season attribute'unu kontrol et
                        if (seasonEpisodes.isEmpty()) {
                            seasonEpisodes = ArrayList(document.select("a[href*=bolum], a.episode-button, a[href*=izle]").filter {
                                it.attr("data-season") == seasonId.toString() || 
                                it.parent()?.attr("data-season") == seasonId.toString() ||
                                it.hasClass("season-$seasonId") || 
                                it.parent()?.hasClass("season-$seasonId") == true
                            })
                        }
                    }
                    
                    Log.d(this@Dizifun.name, "Sezon $seasonId için ${seasonEpisodes.size} bölüm bulundu")
                    
                    // Bulunan bölümleri işle
                    seasonEpisodes.forEachIndexed { episodeIndex, episodeElement ->
                        val episodeName = episodeElement.text().trim()
                        val episodeHref = fixUrl(episodeElement.attr("href"))
                        
                        // Bölüm numarasını belirle
                        val episodeNumber = episodeElement.attr("data-episode").toIntOrNull()
                            ?: episodeName.replace(Regex("[^0-9]"), "").toIntOrNull()
                            ?: (episodeIndex + 1)
                        
                        episodes.add(
                            Episode(
                                episodeHref,
                                episodeName,
                                seasonId,
                                episodeNumber
                            )
                        )
                    }
                }
            } else {
                // Sezon butonları bulunamadıysa, tüm bölümleri yakala ve varsayılan sezon 1 olarak işaretle
                fallbackEpisodeExtraction(document, url, episodes)
            }
        } else {
            // Sezon seçicileri bulunamadıysa, tüm bölümleri yakala ve varsayılan sezon 1 olarak işaretle
            fallbackEpisodeExtraction(document, url, episodes)
        }
    }
    
    // Varsayılan bölüm çıkarma - sezon bilgisi olmadığında
    private fun fallbackEpisodeExtraction(document: Document, url: String, episodes: ArrayList<Episode>) {
        Log.d(this@Dizifun.name, "Fallback bölüm çıkarma yöntemi kullanılıyor")
        
        val allEpisodeLinks = document.select("a[href*=bolum], a.episode-button, a[href*=izle], li.episode a, a.btn-episode, a.dizi-parts")
        Log.d(this@Dizifun.name, "Toplam ${allEpisodeLinks.size} bölüm linki bulundu")
        
        allEpisodeLinks.forEachIndexed { index, episodeElement ->
            val name = episodeElement.text().trim()
            val href = fixUrl(episodeElement.attr("href"))
            
            // Bölüm adından sezon ve bölüm numaralarını çıkarmaya çalış
            val seasonEpisodeMatch = Regex("S(\\d+)(?:E|B)(\\d+)", RegexOption.IGNORE_CASE).find(name)
                ?: Regex("(\\d+)[.\\s]*Sezon[.\\s]*(\\d+)[.\\s]*Bölüm", RegexOption.IGNORE_CASE).find(name)
            
            val (seasonNumber, episodeNumber) = if (seasonEpisodeMatch != null) {
                val sNum = seasonEpisodeMatch.groupValues[1].toIntOrNull() ?: 1
                val eNum = seasonEpisodeMatch.groupValues[2].toIntOrNull() ?: (index + 1)
                Pair(sNum, eNum)
            } else {
                // Bölüm adından sadece bölüm numarasını çıkarmaya çalış
                val episodeMatch = Regex("(\\d+)[.\\s]*Bölüm", RegexOption.IGNORE_CASE).find(name)
                val eNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                Pair(1, eNum)  // Varsayılan olarak sezon 1
            }
            
            episodes.add(
                Episode(
                    href,
                    name,
                    seasonNumber,
                    episodeNumber
                )
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = data
        Log.d(this@Dizifun.name, "Video linkleri yükleniyor: $url")
        
        try {
            val doc = app.get(url, headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to mainUrl
            )).document
            
            // İframe kaynağını bul
            val iframes = doc.select("iframe")
            Log.d(this@Dizifun.name, "Bulunan iframe sayısı: ${iframes.size}")
            
            if (iframes.isNotEmpty()) {
                var videoFound = false
                
                for (iframe in iframes) {
                    val rawIframeSrc = iframe.attr("src")
                    if (rawIframeSrc.isNotBlank()) {
                        // İframe src'yi kontrol et ve gerekirse decode et
                        val iframeSrc = checkAndDecodeIframeSrc(rawIframeSrc)
                        val normalizedIframeSrc = normalizeUrl(iframeSrc)
                        Log.d(this@Dizifun.name, "İframe kaynağı işleniyor: $normalizedIframeSrc")
                        
                        try {
                            val iframeDoc = app.get(
                                normalizedIframeSrc,
                                headers = mapOf(
                                    "User-Agent" to USER_AGENT,
                                    "Referer" to url
                                )
                            ).text
                            
                            // JavaScript'ten hex-encoded URL'leri bul
                            val hexUrls = findHexEncodedUrls(iframeDoc)
                            
                            // JavaScript'ten direkt URL'leri bul (hex olmayan)
                            val directUrls = findDirectUrls(iframeDoc)
                            
                            // Tüm URL'leri birleştir
                            val allUrls = (hexUrls + directUrls).distinct()
                            
                            if (allUrls.isNotEmpty()) {
                                Log.d(this@Dizifun.name, "${allUrls.size} video URL'si bulundu")
                                videoFound = true
                                
                                allUrls.forEach { videoUrl ->
                                    val normalizedVideoUrl = normalizeUrl(videoUrl)
                                    
                                    // Video kalitesini belirle
                                    val quality = when {
                                        normalizedVideoUrl.contains("1080") -> Qualities.P1080.value
                                        normalizedVideoUrl.contains("720") -> Qualities.P720.value
                                        normalizedVideoUrl.contains("480") -> Qualities.P480.value
                                        else -> Qualities.P360.value
                                    }
                                    
                                    // Video kaynağını belirle
                                    val sourceName = when {
                                        normalizedVideoUrl.contains("dizifun") -> "Dizifun"
                                        normalizedVideoUrl.contains("youtube") || normalizedVideoUrl.contains("youtu.be") -> "YouTube"
                                        normalizedVideoUrl.contains("vimeo") -> "Vimeo"
                                        normalizedVideoUrl.contains("googlevideo") -> "Google"
                                        normalizedVideoUrl.contains("dailymotion") || normalizedVideoUrl.contains("dai.ly") -> "Dailymotion"
                                        normalizedVideoUrl.contains("cloudfront") -> "CloudFront"
                                        normalizedVideoUrl.contains("amazonaws") -> "AWS"
                                        else -> "Direct"
                                    }
                                    
                                    // HLS stream kontrolü
                                    val isHLS = normalizedVideoUrl.contains(".m3u8")
                                    
                                    callback.invoke(
                                        ExtractorLink(
                                            this@Dizifun.name,
                                            if (isHLS) "${sourceName} HLS" else sourceName,
                                            normalizedVideoUrl,
                                            normalizedIframeSrc,
                                            quality,
                                            isHLS
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(this@Dizifun.name, "İframe işlenirken hata: ${e.message}")
                        }
                    }
                }
                
                // Video bulunamadıysa, tüm alternatif methodları dene
                if (!videoFound) {
                    Log.d(this@Dizifun.name, "İframe içinde video bulunamadı, alternatif yöntemler deneniyor...")
                    extractAlternativeVideoSources(doc, url, callback, subtitleCallback)
                }
            } else {
                Log.d(this@Dizifun.name, "Iframe bulunamadı, alternatif yöntemler deneniyor...")
                extractAlternativeVideoSources(doc, url, callback, subtitleCallback)
            }
            
            // Altyazıları bul
            doc.select("track").forEach { track ->
                val subLabel = track.attr("label").ifEmpty { "Türkçe" }
                val subLang = track.attr("srclang").ifEmpty { "tr" }
                val subUrl = track.attr("src")
                
                if (subUrl.isNotBlank()) {
                    val normalizedSubUrl = normalizeUrl(subUrl)
                    Log.d(this@Dizifun.name, "Altyazı bulundu: $subLabel - $normalizedSubUrl")
                    subtitleCallback.invoke(
                        SubtitleFile(
                            subLang,
                            normalizedSubUrl
                        )
                    )
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.e(this@Dizifun.name, "Video linkleri yüklenirken hata: ${e.message}")
            return false
        }
    }
    
    // Alternatif video kaynaklarını çıkar
    private suspend fun extractAlternativeVideoSources(
        doc: Document,
        url: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        // 1. Doğrudan video tag'lerini kontrol et
        val videoTags = doc.select("video")
        videoTags.forEach { video ->
            // Video kaynaklarını bul
            val sources = video.select("source")
            sources.forEach { source ->
                val sourceUrl = source.attr("src")
                if (sourceUrl.isNotBlank()) {
                    val normalizedSourceUrl = normalizeUrl(sourceUrl)
                    val type = source.attr("type")
                    val isHLS = normalizedSourceUrl.contains(".m3u8") || type.contains("application/x-mpegURL")
                    
                    callback.invoke(
                        ExtractorLink(
                            this@Dizifun.name,
                            "Direct (Video Tag)",
                            normalizedSourceUrl,
                            url,
                            Qualities.Unknown.value,
                            isHLS
                        )
                    )
                }
            }
            
            // Doğrudan video tag'inde src varsa
            val videoSrc = video.attr("src")
            if (videoSrc.isNotBlank()) {
                val normalizedVideoSrc = normalizeUrl(videoSrc)
                val isHLS = normalizedVideoSrc.contains(".m3u8")
                
                callback.invoke(
                    ExtractorLink(
                        this@Dizifun.name,
                        "Direct (Video Src)",
                        normalizedVideoSrc,
                        url,
                        Qualities.Unknown.value,
                        isHLS
                    )
                )
            }
        }
        
        // 2. Alternatif embed butonlarını kontrol et
        val altEmbeds = doc.select("a.alter_video, a.alternate-server, a.server-button, a[data-video], button[data-video]")
        altEmbeds.forEach { element ->
            val embedUrl = element.attr("href").takeIf { it.isNotBlank() } 
                ?: element.attr("data-video").takeIf { it.isNotBlank() }
                ?: element.attr("data-src").takeIf { it.isNotBlank() }
            
            if (embedUrl != null) {
                try {
                    val normalizedEmbedUrl = normalizeUrl(embedUrl)
                    Log.d(this@Dizifun.name, "Alternatif embed işleniyor: $normalizedEmbedUrl")
                    
                    val embedDoc = app.get(
                        normalizedEmbedUrl,
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to url
                        )
                    ).text
                    
                    // Hem hex-encoded hem de direkt URL'leri bul
                    val altUrls = findHexEncodedUrls(embedDoc) + findDirectUrls(embedDoc)
                    
                    altUrls.distinct().forEach { videoUrl ->
                        val normalizedAltVideoUrl = normalizeUrl(videoUrl)
                        // Video kalitesini belirle
                        val quality = when {
                            normalizedAltVideoUrl.contains("1080") -> Qualities.P1080.value
                            normalizedAltVideoUrl.contains("720") -> Qualities.P720.value
                            normalizedAltVideoUrl.contains("480") -> Qualities.P480.value
                            else -> Qualities.P360.value
                        }
                        
                        // Video kaynağını belirle
                        val sourceName = when {
                            normalizedAltVideoUrl.contains("dizifun") -> "Dizifun (Alt)"
                            normalizedAltVideoUrl.contains("youtube") || normalizedAltVideoUrl.contains("youtu.be") -> "YouTube (Alt)"
                            normalizedAltVideoUrl.contains("vimeo") -> "Vimeo (Alt)"
                            normalizedAltVideoUrl.contains("googlevideo") -> "Google (Alt)"
                            normalizedAltVideoUrl.contains("dailymotion") || normalizedAltVideoUrl.contains("dai.ly") -> "Dailymotion (Alt)"
                            else -> "Direct (Alt)"
                        }
                        
                        callback.invoke(
                            ExtractorLink(
                                this@Dizifun.name,
                                if (normalizedAltVideoUrl.contains(".m3u8")) "${sourceName} HLS" else sourceName,
                                normalizedAltVideoUrl,
                                normalizedEmbedUrl,
                                quality,
                                normalizedAltVideoUrl.contains(".m3u8")
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(this@Dizifun.name, "Alternatif embed yüklenirken hata: ${e.message}")
                }
            }
        }
        
        // 3. JavaScript içindeki URL'leri kontrol et
        doc.select("script").forEach { script ->
            val scriptContent = script.html()
            if (scriptContent.contains("http") || scriptContent.contains(".mp4") || scriptContent.contains(".m3u8")) {
                val scriptUrls = findDirectUrls(scriptContent)
                
                scriptUrls.forEach { scriptUrl ->
                    val normalizedScriptUrl = normalizeUrl(scriptUrl)
                    val isHLS = normalizedScriptUrl.contains(".m3u8")
                    
                    callback.invoke(
                        ExtractorLink(
                            this@Dizifun.name,
                            if (isHLS) "JavaScript HLS" else "JavaScript",
                            normalizedScriptUrl,
                            url,
                            Qualities.Unknown.value,
                            isHLS
                        )
                    )
                }
            }
        }
    }
    
    // JavaScript içinden direkt URL'leri bul (hex olmayan)
    private fun findDirectUrls(html: String): List<String> {
        val foundUrls = mutableListOf<String>()
        
        try {
            // MP4 veya M3U8 URL'leri için regex
            val urlPatterns = listOf(
                "https?://[^\"'\\s]+\\.(?:mp4|m3u8)[^\"'\\s]*".toRegex(),  // MP4/M3U8 uzantılı URL'ler
                "https?://[^\"'\\s]+/embed/[^\"'\\s]+".toRegex(),  // Embed URL'leri
                "https?://[^\"'\\s]+/player/[^\"'\\s]+".toRegex(),  // Player URL'leri
                "https?://[^\"'\\s]+/stream/[^\"'\\s]+".toRegex()   // Stream URL'leri
            )
            
            urlPatterns.forEach { pattern ->
                pattern.findAll(html).forEach { matchResult ->
                    val url = matchResult.value
                    // URL'yi temizle (sondaki gereksiz karakterleri kaldır)
                    val cleanUrl = url.replace(Regex("[\"'\\s]+$"), "")
                    foundUrls.add(cleanUrl)
                }
            }
            
            // JavaScript değişken atamalarını da kontrol et
            val jsVariablePatterns = listOf(
                "(?:var|let|const)\\s+\\w+\\s*=\\s*[\"']([^\"']*?(?:mp4|m3u8)[^\"']*?)[\"']".toRegex(),
                "file\\s*:\\s*[\"']([^\"']*?(?:mp4|m3u8)[^\"']*?)[\"']".toRegex(),
                "source\\s*:\\s*[\"']([^\"']*?(?:mp4|m3u8)[^\"']*?)[\"']".toRegex(),
                "src\\s*:\\s*[\"']([^\"']*?(?:mp4|m3u8)[^\"']*?)[\"']".toRegex()
            )
            
            jsVariablePatterns.forEach { pattern ->
                pattern.findAll(html).forEach { matchResult ->
                    val url = matchResult.groupValues[1]
                    if (url.isNotBlank() && 
                        (url.startsWith("http") || url.startsWith("//") || url.contains(".mp4") || url.contains(".m3u8"))) {
                        foundUrls.add(url)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(this@Dizifun.name, "Error finding direct URLs: ${e.message}")
        }
        
        return foundUrls.distinct()
    }

    // İframe src değerinin hex-encoded olup olmadığını kontrol et ve deşifre et
    private fun checkAndDecodeIframeSrc(iframeSrc: String): String {
        // Boş kontrolü
        if (iframeSrc.isBlank()) return ""
        
        // Eğer normal bir URL ise doğrudan döndür
        if (iframeSrc.startsWith("http") || iframeSrc.startsWith("//")) {
            return iframeSrc
        }
        
        // JavaScript:something(...) formatında mı?
        if (iframeSrc.startsWith("javascript:")) {
            Log.d(this@Dizifun.name, "JavaScript iframe src işleniyor: $iframeSrc")
            
            // Hex-encoded URL içeriyor mu kontrol et
            val hexMatches = "\\\\x[0-9a-fA-F]{2}".toRegex().findAll(iframeSrc)
            if (hexMatches.any()) {
                try {
                    // JavaScript: kısmını çıkar
                    val jsContent = iframeSrc.substringAfter("javascript:")
                    // Hex encode edilmiş kısımları bul
                    val possibleUrls = findHexEncodedUrls(jsContent)
                    // HTTP ile başlayan ilk URL'yi döndür
                    val httpUrl = possibleUrls.firstOrNull { it.startsWith("http") || it.startsWith("//") }
                    if (httpUrl != null) {
                        Log.d(this@Dizifun.name, "Javascript'ten çözülen URL: $httpUrl")
                        return httpUrl
                    }
                } catch (e: Exception) {
                    Log.e(this@Dizifun.name, "Javascript iframe src çözülemedi: ${e.message}")
                }
            }
            
            // Base64 encoding içeriyor mu kontrol et
            val base64Matches = "atob\\([\"']([^\"']+)[\"']\\)".toRegex().find(iframeSrc)
            if (base64Matches != null) {
                try {
                    val base64Content = base64Matches.groupValues[1]
                    val decodedContent = base64Decode(base64Content)
                    if (decodedContent.contains("http")) {
                        Log.d(this@Dizifun.name, "Base64'ten çözülen URL: $decodedContent")
                        return extractUrlFromString(decodedContent)
                    }
                } catch (e: Exception) {
                    Log.e(this@Dizifun.name, "Base64 iframe src çözülemedi: ${e.message}")
                }
            }
            
            // Direkt URL içeriyor mu kontrol et
            val directUrlMatches = "(?:document\\.write\\(['\"](https?://[^'\"]+)['\"]\\))".toRegex().find(iframeSrc)
            if (directUrlMatches != null) {
                val directUrl = directUrlMatches.groupValues[1]
                if (directUrl.isNotBlank()) {
                    Log.d(this@Dizifun.name, "JavaScript'ten direkt URL: $directUrl")
                    return directUrl
                }
            }
        }
        
        // Doğrudan hex-encoded olabilir mi?
        if (iframeSrc.length > 20 && iframeSrc.all { it.isLetterOrDigit() }) {
            try {
                val decoded = hexToString(iframeSrc)
                if (decoded.contains("http") || decoded.contains("//")) {
                    Log.d(this@Dizifun.name, "Hex-encoded iframe src çözüldü: $decoded")
                    return extractUrlFromString(decoded)
                }
            } catch (e: Exception) {
                Log.e(this@Dizifun.name, "Hex iframe src çözülemedi: ${e.message}")
            }
        }
        
        // Base64 encoded olabilir mi?
        if (iframeSrc.length > 20 && iframeSrc.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            try {
                val decoded = base64Decode(iframeSrc)
                if (decoded.contains("http") || decoded.contains("//")) {
                    Log.d(this@Dizifun.name, "Base64-encoded iframe src çözüldü: $decoded")
                    return extractUrlFromString(decoded)
                }
            } catch (e: Exception) {
                // Base64 değilse sessizce devam et
            }
        }
        
        // Hiçbir şey yapılamadıysa orijinal değeri döndür
        return iframeSrc
    }
    
    // String içinden URL'yi çıkar
    private fun extractUrlFromString(input: String): String {
        // Basit URL regex
        val urlRegex = "https?://[^\\s\"'<>]+".toRegex()
        val match = urlRegex.find(input)
        
        return match?.value ?: input
    }

    // URL'yi düzelt ve normalleştir
    private fun normalizeUrl(url: String): String {
        if (url.isBlank()) return ""
        
        var fixedUrl = url.trim()
        
        // URL'yi temizle - sondaki gereksiz karakterleri kaldır
        fixedUrl = fixedUrl.replace(Regex("[\"'\\s]+$"), "")
        
        // URL'nin başında iki slash kaldıysa düzelt
        if (fixedUrl.startsWith("//")) {
            fixedUrl = "https:$fixedUrl"
        }
        
        // URL protokol içermiyor mu?
        if (!fixedUrl.startsWith("http")) {
            // Eğer root path ise
            fixedUrl = if (fixedUrl.startsWith("/")) {
                "${mainUrl}${fixedUrl}"
            } else {
                "${mainUrl}/${fixedUrl}"
            }
        }
        
        // UTF-8 karakter sorunu düzeltme
        try {
            val uri = java.net.URI(fixedUrl)
            val encodedPath = uri.rawPath?.let { path ->
                path.replace(" ", "%20")
                    .replace("|", "%7C")
                    .replace("\"", "%22")
                    .replace("'", "%27")
                    .replace("<", "%3C")
                    .replace(">", "%3E")
            }
            
            if (encodedPath != null && encodedPath != uri.rawPath) {
                val scheme = uri.scheme
                val authority = uri.authority
                val query = uri.rawQuery?.let { "?$it" } ?: ""
                val fragment = uri.fragment?.let { "#$it" } ?: ""
                
                fixedUrl = "$scheme://$authority$encodedPath$query$fragment"
            }
        } catch (e: Exception) {
            Log.e(this@Dizifun.name, "URL normalization error: ${e.message}")
        }
        
        return fixedUrl
    }

    // JavaScript içindeki hex-encoded URL'leri bul
    private fun findHexEncodedUrls(html: String): List<String> {
        val foundUrls = mutableListOf<String>()
        
        try {
            // 1. \x ile başlayan hex dizilerini bul (JavaScript hex encoding)
            val hexPattern = "\\\\x[0-9a-fA-F]{2}".toRegex()
            
            // HTML içeriğini var tanımlamalarına göre parçala
            val splitByVar = html.split("var")
            
            // Hex içeren blokları filtrele
            splitByVar.forEach { block ->
                if (hexPattern.containsMatchIn(block)) {
                    // Uzun hex dizileri URL olabilir
                    val potentialHexString = block.substringAfter("=", "").substringBefore(";", "").trim()
                    
                    // Birçok hex karakteri içeren string'leri kontrol et
                    if (potentialHexString.contains("\\x") && potentialHexString.count { it == '\\' } > 5) {
                        // Temizle ve decode et
                        val cleanHex = potentialHexString
                            .replace("\"", "")
                            .replace("'", "")
                            .replace("\\\\x", "")  // \\x formatındaki hex karakterlerini temizle
                            .replace("\\x", "")    // \x formatındaki hex karakterlerini temizle
                        
                        try {
                            val decodedString = hexToString(cleanHex)
                            if (decodedString.contains("http") || 
                                decodedString.contains(".mp4") || 
                                decodedString.contains(".m3u8")) {
                                foundUrls.add(decodedString)
                            }
                        } catch (e: Exception) {
                            Log.e(this@Dizifun.name, "Hex decode error: ${e.message}")
                        }
                    }
                }
            }
            
            // 2. hexToString fonksiyonuna yapılan çağrıları ara
            val hexFunctionCallPattern = "(?:hexToString|fromHex|decodeHex|hexDecode)\\([\"']([0-9a-fA-F]+)[\"']\\)".toRegex()
            val hexFunctionCalls = hexFunctionCallPattern.findAll(html)
            
            hexFunctionCalls.forEach { matchResult ->
                try {
                    val hexValue = matchResult.groupValues[1]
                    val decodedValue = hexToString(hexValue)
                    if (decodedValue.contains("http") || 
                        decodedValue.contains(".mp4") || 
                        decodedValue.contains(".m3u8")) {
                        foundUrls.add(decodedValue)
                    }
                } catch (e: Exception) {
                    Log.e(this@Dizifun.name, "Hex function call decode error: ${e.message}")
                }
            }
            
            // 3. Doğrudan hex string'leri bul
            val directHexPatterns = listOf(
                "[\"']([0-9a-fA-F]{30,})[\"']".toRegex(),  // Uzun hex dizisi
                "\\bvar\\s+\\w+\\s*=\\s*[\"']([0-9a-fA-F]{30,})[\"']".toRegex(),  // Değişken atama
                "\\bconst\\s+\\w+\\s*=\\s*[\"']([0-9a-fA-F]{30,})[\"']".toRegex(),  // Const değişken
                "\\blet\\s+\\w+\\s*=\\s*[\"']([0-9a-fA-F]{30,})[\"']".toRegex()   // Let değişken
            )
            
            directHexPatterns.forEach { pattern ->
                val directHexMatches = pattern.findAll(html)
                directHexMatches.forEach { matchResult ->
                    try {
                        val hexValue = matchResult.groupValues[1]
                        if (hexValue.length >= 30) {  // Çok kısa hex string'ler URL olmayacaktır
                            val decodedValue = hexToString(hexValue)
                            if (decodedValue.contains("http") || 
                                decodedValue.contains(".mp4") || 
                                decodedValue.contains(".m3u8")) {
                                foundUrls.add(decodedValue)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(this@Dizifun.name, "Direct hex decode error: ${e.message}")
                    }
                }
            }
            
            // 4. Base64 ile şifrelenmiş içerikleri kontrol et
            val base64Pattern = "(?:atob|window\\.atob)\\([\"']([A-Za-z0-9+/=]+)[\"']\\)".toRegex()
            val base64Matches = base64Pattern.findAll(html)
            
            base64Matches.forEach { matchResult ->
                try {
                    val base64Value = matchResult.groupValues[1]
                    if (base64Value.length > 20) {  // Çok kısa base64 string'ler URL olmayacaktır
                        val decodedValue = base64Decode(base64Value)
                        if (decodedValue.contains("http") || 
                            decodedValue.contains(".mp4") || 
                            decodedValue.contains(".m3u8")) {
                            foundUrls.add(decodedValue)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(this@Dizifun.name, "Base64 decode error: ${e.message}")
                }
            }
            
            // 5. Doğrudan base64 string'lerini kontrol et
            val directBase64Pattern = "[\"']([A-Za-z0-9+/=]{40,})[\"']".toRegex()
            val directBase64Matches = directBase64Pattern.findAll(html)
            
            directBase64Matches.forEach { matchResult ->
                try {
                    val base64Value = matchResult.groupValues[1]
                    val decodedValue = base64Decode(base64Value)
                    if (decodedValue.contains("http") || 
                        decodedValue.contains(".mp4") || 
                        decodedValue.contains(".m3u8")) {
                        foundUrls.add(decodedValue)
                    }
                } catch (e: Exception) {
                    // Sadece log'la, Bu çoğu zaman base64 olmayan normal string'ler için hata verir
                    // Log.e(this@Dizifun.name, "Direct base64 decode error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(this@Dizifun.name, "Error finding encoded URLs: ${e.message}")
        }
        
        // Tekrarlanan URL'leri temizle
        return foundUrls.distinct()
    }
    
    // Base64 stringini decode et
    private fun base64Decode(base64String: String): String {
        return try {
            android.util.Base64.decode(base64String, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            // İkinci bir yöntem dene
            try {
                String(java.util.Base64.getDecoder().decode(base64String), Charsets.UTF_8)
            } catch (e2: Exception) {
                throw e2
            }
        }
    }
}