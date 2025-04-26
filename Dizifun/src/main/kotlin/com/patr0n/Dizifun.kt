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
        
        // Sayfa içeriğini al, headers ekleyerek
        val response = app.get(
            request.data,
            headers = defaultHeaders,
            referer = mainUrl,
            cacheTime = 0
        )
        
        // HTML yanıtının ilk birkaç karakterini logla
        val html = response.document.outerHtml()
        logResponse(request.data, html)
        
        val document = response.document
        
        // Dizifun ana sayfasındaki içerikleri çekmek için ana seçiciler
        val home = ArrayList<SearchResponse>()
        
        // Netflix ve diğer kategori sayfaları için özel olarak bu seçiciyi kullan
        val specialCategoryItems = document.select("div.uk-overlay.uk-overlay-hover")
        Log.d(this@Dizifun.name, "Found ${specialCategoryItems.size} special category items in ${request.name}")
        
        specialCategoryItems.forEach { element ->
            val parent = element.parent() ?: return@forEach
            val panel = parent.selectFirst("div.uk-panel")
            
            // Başlık bul
            val title = panel?.selectFirst("h5.uk-panel-title")?.text()?.trim()
            if (!title.isNullOrBlank()) {
                // Link bul
                val href = element.selectFirst("a.uk-position-cover")?.attr("href")
                if (!href.isNullOrBlank()) {
                    // Poster bul
                    val posterUrl = element.selectFirst("div.platformmobile img, img")?.attr("src")
                    
                    // Yıl bul
                    val yearText = panel.selectFirst("span.uk-display-block.uk-text-muted")?.text()?.trim()
                    val year = yearText?.toIntOrNull()
                    
                    // Tür belirle
                    val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie
                    
                    home.add(newMovieSearchResponse(title, href, type) {
                        this.posterUrl = fixUrlNull(posterUrl)
                        this.year = year
                    })
                }
            }
        }
        
        // Eğer yukarıdaki özel yapıda içerik bulunamadıysa, normal içerik yapılarını dene
        if (home.isEmpty()) {
            // Tüm içerik türlerini tek bir seferde topla
            val allContentItems = document.select(
                "div.col-md-2, " +
                "div.movies_recent-item, " + 
                "div.editor_sec-item, " + 
                "div.featuredseries_recent-item, " + 
                "div.plattab_recent-item, " +
                "div.episode-item, " +
                "article" // Bazı kategorilerde içerikler article içinde olabilir
            )
            
            Log.d(this@Dizifun.name, "Found total ${allContentItems.size} items in category: ${request.name}")
            
            allContentItems.forEach { element ->
                var result: SearchResponse? = null
                
                // Her bir HTML yapısı için uygun işleme fonksiyonunu çağır
                if (element.hasClass("episode-item")) {
                    result = element.toEpisodeResult()
                } else {
                    result = element.toMainPageResult()
                }
                
                if (result != null) {
                    home.add(result)
                }
            }
        }
        
        Log.d(this@Dizifun.name, "Total processed items for ${request.name}: ${home.size}")
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toEpisodeResult(): SearchResponse? {
        val title = this.selectFirst("div.name")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        
        // Diziler için TvType.TvSeries olarak işaretle
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        // HTML yapısını debug için loglayalım
        // Log.d("Dizifun", "Examining element: ${this.outerHtml().take(200)}...")
        
        // Dizi/film başlığını bul
        val title = when {
            this.selectFirst("h3.editor_sec-title") != null -> this.selectFirst("h3.editor_sec-title")?.text()?.trim()
            this.selectFirst("h3.movies_recent-title") != null -> this.selectFirst("h3.movies_recent-title")?.text()?.trim()
            this.selectFirst("h3.featuredseries_recent-title") != null -> this.selectFirst("h3.featuredseries_recent-title")?.text()?.trim()
            this.selectFirst("h3.plattab_recent-title") != null -> this.selectFirst("h3.plattab_recent-title")?.text()?.trim()
            this.selectFirst("h5.uk-panel-title") != null -> this.selectFirst("h5.uk-panel-title")?.text()?.trim()
            this.selectFirst("div.name") != null -> this.selectFirst("div.name")?.text()?.trim()
            this.selectFirst("a > strong") != null -> this.selectFirst("a > strong")?.text()?.trim()
            this.selectFirst("h1, h2, h3, h4, h5, h6") != null -> this.selectFirst("h1, h2, h3, h4, h5, h6")?.text()?.trim()
            this.selectFirst("strong") != null -> this.selectFirst("strong")?.text()?.trim()
            else -> {
                // Alternatif başlık bulma yöntemleri
                val img = this.selectFirst("img")
                if (img != null) {
                    img.attr("alt").takeIf { !it.isNullOrBlank() }
                } else {
                    // Eğer hiçbir başlık bulunamadıysa, doğrudan metin içeriğini dene
                    this.ownText().takeIf { !it.isBlank() }
                }
            }
        }
        
        if (title.isNullOrBlank()) {
            // Log.d("Dizifun", "No title found in element")
            return null
        }
        
        // Link URL'sini bul
        val href = when {
            this.selectFirst("a.editor_sec-link") != null -> fixUrlNull(this.selectFirst("a.editor_sec-link")?.attr("href"))
            this.selectFirst("a.movies_recent-link") != null -> fixUrlNull(this.selectFirst("a.movies_recent-link")?.attr("href"))
            this.selectFirst("a.featuredseries_recent-link") != null -> fixUrlNull(this.selectFirst("a.featuredseries_recent-link")?.attr("href"))
            this.selectFirst("a.plattab_recent-link") != null -> fixUrlNull(this.selectFirst("a.plattab_recent-link")?.attr("href"))
            this.selectFirst("a.uk-position-cover") != null -> fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href"))
            this.selectFirst("a[href*=dizi], a[href*=film]") != null -> fixUrlNull(this.selectFirst("a[href*=dizi], a[href*=film]")?.attr("href"))
            this.selectFirst("a") != null -> fixUrlNull(this.selectFirst("a")?.attr("href"))
            else -> null
        }
        
        if (href.isNullOrBlank()) {
            // Kendimiz bir link mi?
            if (this.tagName() == "a") {
                val selfHref = fixUrlNull(this.attr("href"))
                if (!selfHref.isNullOrBlank()) {
                    return newMovieSearchResponse(title, selfHref, TvType.TvSeries) {
                        this.posterUrl = this@toMainPageResult.selectFirst("img")?.attr("src")
                            ?.let { if (it.startsWith("data:")) null else fixUrlNull(it) }
                    }
                }
            }
            
            Log.d("Dizifun", "No href found for title: $title")
            return null
        }
        
        // Poster URL'sini bul
        val posterUrl = when {
            this.selectFirst("img.editor_sec-image") != null -> fixUrlNull(this.selectFirst("img.editor_sec-image")?.attr("src"))
            this.selectFirst("img.movies_recent-image") != null -> fixUrlNull(this.selectFirst("img.movies_recent-image")?.attr("src"))
            this.selectFirst("img.featuredseries_recent-image") != null -> fixUrlNull(this.selectFirst("img.featuredseries_recent-image")?.attr("src")) 
            this.selectFirst("img.plattab_recent-image") != null -> fixUrlNull(this.selectFirst("img.plattab_recent-image")?.attr("src"))
            this.selectFirst("img") != null -> {
                val imgSrc = this.selectFirst("img")?.attr("src")
                // Data URI'leri filtrele
                if (imgSrc?.startsWith("data:") == true) null else fixUrlNull(imgSrc)
            }
            else -> null
        }
        
        // Yılı bul
        val yearText = this.text()
        val year = when {
            this.selectFirst("p.editor_sec-date") != null -> this.selectFirst("p.editor_sec-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("p.movies_recent-date") != null -> this.selectFirst("p.movies_recent-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("p.featuredseries_recent-date") != null -> this.selectFirst("p.featuredseries_recent-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("p.plattab_recent-date") != null -> this.selectFirst("p.plattab_recent-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("span.uk-display-block.uk-text-muted") != null -> this.selectFirst("span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("div.date") != null -> this.selectFirst("div.date")?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
            yearText.contains(Regex("\\b\\d{4}\\b")) -> Regex("\\b(\\d{4})\\b").find(yearText)?.groupValues?.get(1)?.toIntOrNull()
            else -> null
        }
        
        // İçerik tipini belirle
        val type = if (href.contains("/dizi/") || href.contains("/diziler/") || href.contains("sezon") || href.contains("bolum") || 
                   href.contains("netflix") || href.contains("disney") || href.contains("primevideo") || 
                   href.contains("blutv") || href.contains("gain") || href.contains("exxen") || 
                   href.contains("tabii-dizileri") || href.contains("hulu") || href.contains("todtv") || 
                   href.contains("paramount") || href.contains("unutulmaz") || 
                   (title.contains("Sezon") && !title.contains("Film"))) {
            Log.d(this@Dizifun.name, "İçerik tipi: Dizi olarak belirlendi (URL: $href)")
            TvType.TvSeries
        } else {
            Log.d(this@Dizifun.name, "İçerik tipi: Film olarak belirlendi (URL: $href)")
            TvType.Movie
        }

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/arama?query=$query"
        Log.d(this@Dizifun.name, "Searching: $searchUrl")
        
        val document = app.get(searchUrl, headers = defaultHeaders).document
        val searchResults = ArrayList<SearchResponse>()
        
        // Ana arama sonuçlarını tara
        val colItems = document.select("div.col-md-2")
        Log.d(this@Dizifun.name, "Found ${colItems.size} col-md-2 items in search")
        
        colItems.forEach { element ->
            val result = element.toMainPageResult()
            if (result != null) {
                searchResults.add(result)
            }
        }
        
        // Alternatif arama sonuçlarını tara
        if (searchResults.isEmpty()) {
            val altItems = document.select("div.uk-overlay.uk-overlay-hover, div.editor_sec-item, div.movies_recent-item, div.featuredseries_recent-item, div.episode-item")
            Log.d(this@Dizifun.name, "Found ${altItems.size} alternative items in search")
            
            altItems.forEach { element ->
                val result = element.toSearchResult()
                if (result != null) {
                    searchResults.add(result)
                }
            }
        }
        
        Log.d(this@Dizifun.name, "Total search results: ${searchResults.size}")
        return searchResults
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Başlık, URL ve poster için birçok olası seçiciyi kontrol et
        val title = when {
            this.selectFirst("h3.editor_sec-title, h3.movies_recent-title, h3.featuredseries_recent-title, h5.uk-panel-title") != null -> 
                this.selectFirst("h3.editor_sec-title, h3.movies_recent-title, h3.featuredseries_recent-title, h5.uk-panel-title")?.text()?.trim()
            this.selectFirst("div.name") != null -> this.selectFirst("div.name")?.text()?.trim()
            this.selectFirst("a > strong") != null -> this.selectFirst("a > strong")?.text()?.trim()
            else -> {
                // Alternatif başlık bulma
                val img = this.selectFirst("img")
                if (img != null) {
                    img.attr("alt").takeIf { !it.isNullOrBlank() }
                } else null
            }
        } ?: return null
        
        // URL'yi bul
        val href = when {
            this.selectFirst("a.editor_sec-link, a.movies_recent-link, a.featuredseries_recent-link") != null -> 
                fixUrlNull(this.selectFirst("a.editor_sec-link, a.movies_recent-link, a.featuredseries_recent-link")?.attr("href"))
            this.selectFirst("a.uk-position-cover") != null -> fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href"))
            this.selectFirst("a") != null -> fixUrlNull(this.selectFirst("a")?.attr("href"))
            else -> null
        } ?: return null
        
        // Poster URL'yi bul
        val posterUrl = when {
            this.selectFirst("img.editor_sec-image, img.movies_recent-image, img.featuredseries_recent-image") != null -> 
                fixUrlNull(this.selectFirst("img.editor_sec-image, img.movies_recent-image, img.featuredseries_recent-image")?.attr("src"))
            this.selectFirst("img") != null -> fixUrlNull(this.selectFirst("img")?.attr("src"))
            else -> null
        }
        
        // Yılı bul
        val yearText = this.text()
        val year = when {
            this.selectFirst("p.editor_sec-date, p.movies_recent-date, p.featuredseries_recent-date") != null -> 
                this.selectFirst("p.editor_sec-date, p.movies_recent-date, p.featuredseries_recent-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("span.uk-display-block.uk-text-muted") != null -> this.selectFirst("span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("div.date") != null -> this.selectFirst("div.date")?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
            yearText.contains(Regex("\\b\\d{4}\\b")) -> Regex("\\b(\\d{4})\\b").find(yearText)?.groupValues?.get(1)?.toIntOrNull()
            else -> null
        }
        
        // İçerik tipini belirle
        val type = if (href.contains("/dizi/") || href.contains("sezon") || href.contains("bolum")) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        Log.d(this@Dizifun.name, "Loading details: $url")
        
        try {
            val response = app.get(url, headers = defaultHeaders)
            Log.d(this@Dizifun.name, "HTTP Status: ${response.code}")
            Log.d(this@Dizifun.name, "Content Type: ${response.headers["Content-Type"]}")
            
            val document = response.document
            val htmlContent = document.outerHtml()
            logResponse(url, htmlContent)

            // Başlığı bul
            var title = document.selectFirst("h1.text-bold, div.titlemob, div.head-title > h1")?.text()?.trim()
            Log.d(this@Dizifun.name, "Found title: $title")
            if (title.isNullOrBlank()) {
                Log.d(this@Dizifun.name, "Title not found, trying alternative selectors")
                // Try alternative selectors for title
                title = document.selectFirst("h1, .title, .movie-title, .dizi-title")?.text()?.trim()
                Log.d(this@Dizifun.name, "Alternative title found: $title")
                if (title.isNullOrBlank()) {
                    Log.e(this@Dizifun.name, "No title found for $url")
                    return null
                }
            }
            
            // Posteri bul - web sayfası içerik resmi kullanabilir
            val poster = fixUrlNull(document.selectFirst("div.media-cover img, img.imgboyut, img.responsive-img, div.platformmobile img")?.attr("src"))
            Log.d(this@Dizifun.name, "Found poster: $poster")
            
            // Yılı bul
            val yearText = document.select("ul.subnav li, div.content_data").find { it.text().contains("Yıl:") || it.text().contains("Dizi Yılı:") }?.text() ?: ""
            val year = yearText.substringAfter("Yıl:").substringAfter("Dizi Yılı:").trim().toIntOrNull()
            Log.d(this@Dizifun.name, "Found year: $year")
            
            // Açıklamayı bul
            val description = document.selectFirst("p.text-muted, div.descmobi, div.content_data > p")?.text()?.trim()
                ?: document.select("div.content_data, div.detail-text, div.description").firstOrNull()?.text()?.trim()
            Log.d(this@Dizifun.name, "Found description: ${description?.take(50)}...")
            
            // İçerik tipini belirle
            val type = if (url.contains("/dizi/") || url.contains("/diziler/") || url.contains("sezon") || url.contains("bolum") || 
                       url.contains("netflix") || url.contains("disney") || url.contains("primevideo") || 
                       url.contains("blutv") || url.contains("gain") || url.contains("exxen") || 
                       url.contains("tabii-dizileri") || url.contains("hulu") || url.contains("todtv") || 
                       url.contains("paramount") || url.contains("unutulmaz") || 
                       (title?.contains("Sezon", ignoreCase = true) == true && title?.contains("Film", ignoreCase = true) != true)) {
                Log.d(this@Dizifun.name, "İçerik tipi: Dizi olarak belirlendi (URL: $url)")
                TvType.TvSeries
            } else {
                Log.d(this@Dizifun.name, "İçerik tipi: Film olarak belirlendi (URL: $url)")
                TvType.Movie
            }
            Log.d(this@Dizifun.name, "Content type: $type")
            
            // Ek bilgileri bul
            val rating = document.selectFirst("span.rating, span.rate")?.text()?.toRatingInt()
            val duration = document.selectFirst("span.runtime, div.content_data")?.text()?.let { 
                val durationRegex = Regex("(\\d+)\\s*dk")
                val match = durationRegex.find(it)
                match?.groupValues?.get(1)?.toIntOrNull()
            }
            
            // Kategori bilgilerini bul
            val tags = document.select("div.genres a, ul.subnav li a, div.series-info, div.content_data span").map { it.text().trim() }
            Log.d(this@Dizifun.name, "Found tags: $tags")
            
            // Oyuncuları bul
            val actors = document.select("div.actors-container div.actor-card, div.oyuncular span").mapNotNull {
                val actorName = it.selectFirst("span.actor-name")?.text()?.trim() 
                    ?: it.text().trim()
                    ?: return@mapNotNull null
                    
                val actorImg = fixUrlNull(it.selectFirst("img")?.attr("src"))
                
                Actor(actorName, actorImg)
            }
            Log.d(this@Dizifun.name, "Found ${actors.size} actors")
            
            // Fragmanı bul
            val trailer = document.selectFirst("iframe[src*=youtube], a.trailer-button")?.attr("src")?.let { fixUrl(it) }
            Log.d(this@Dizifun.name, "Found trailer: $trailer")

            // Bölümleri bul (dizi ise)
            val episodes = if (type == TvType.TvSeries) {
                val allEpisodes = ArrayList<Episode>()
                
                // Season/Tab kutularını bul
                val seasonTabs = document.select("div.tabcontent2, div.seasons, div.episodes-container, div.episodeitem, div.episodelist, div.bolumler, div.seasons-bk")
                Log.d(this@Dizifun.name, "Sezon tabları: ${seasonTabs.size}")
                
                if (seasonTabs.isNotEmpty()) {
                    Log.d(this@Dizifun.name, "Found ${seasonTabs.size} season tabs")
                    seasonTabs.forEach { seasonTab ->
                        // Sezon ID'sini bul (season1, season2 gibi ID'ler veya sıra numarası)
                        val seasonId = seasonTab.attr("id").replace(Regex("[^0-9]"), "").toIntOrNull() 
                            ?: seasonTabs.indexOf(seasonTab) + 1
                        
                        // Her sezon tab'ındaki bölümleri bul
                        val episodeElements = seasonTab.select("div.bolumtitle a, a[href*=episode], a.episode-button, a[href*=bolum], div.episode-button, li.episode a, a.btn-episode, a[href*=izle], a.dizi-parts")
                        Log.d(this@Dizifun.name, "Found ${episodeElements.size} episodes in season $seasonId")
                        
                        episodeElements.forEach { episodeElement ->
                            val name = episodeElement.text().trim()
                            val href = episodeElement.attr("href")
                            
                            Log.d(this@Dizifun.name, "Episode element: name=$name, href=$href")
                            
                            // Bölüm numarasını extract et
                            val episodeNum = when {
                                name.contains("Bölüm", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("bölüm").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                name.contains("Bölum", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("bölum").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                name.contains("Bolum", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("bolum").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                name.contains("Episode", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("episode").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                name.contains("B.", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("b.").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                name.matches(Regex(".*\\d+.*", RegexOption.IGNORE_CASE)) -> {
                                    name.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                else -> 1 // Bölüm numarası bulunamazsa 1 atanır
                            }
                            
                            Log.d(this@Dizifun.name, "Extracted episode number: $episodeNum")
                            
                            // URL'yi fix
                            val data = if (href.isNotBlank()) {
                                fixUrlNull(href)
                            } else {
                                null
                            }
                            
                            if (data == null) {
                                Log.d(this@Dizifun.name, "Skipping episode with invalid URL")
                                return@forEach
                            }
                            
                            val fullUrl = if (data.startsWith("?") || data.startsWith("/")) {
                                if (data.startsWith("?")) url + data else mainUrl + data
                            } else {
                                data
                            }
                            
                            Log.d(this@Dizifun.name, "Adding episode: name=$name, season=$seasonId, episode=$episodeNum, url=$fullUrl")
                            
                            allEpisodes.add(
                                newEpisode(fullUrl) {
                                    this.name = name
                                    this.season = seasonId
                                    this.episode = episodeNum ?: 1
                                }
                            )
                        }
                    }
                } else {
                    // Alternatif bölüm seçicisi - sezon tabları yoksa
                    val episodeElements = document.select("div.episode-button, div.bolumtitle a, a.episode-button, div.episodes div, div.episodes a, a[href*=bolum], li.episode a, a.btn-episode, a[href*=izle], a.dizi-parts, a[href*=sezon], a[href*=season]")
                    Log.d(this@Dizifun.name, "Found ${episodeElements.size} episode elements (alternative selector)")
                    
                    episodeElements.forEach { episodeElement ->
                        val name = episodeElement.text().trim()
                        val href = episodeElement.attr("href")
                        
                        Log.d(this@Dizifun.name, "Alternative episode element: name=$name, href=$href")
                        
                        if (name.isNotBlank() && href.isNotBlank()) {
                            // Sezon numarasını bulmaya çalış
                            val seasonNum = when {
                                href.contains("sezon", ignoreCase = true) || href.contains("season", ignoreCase = true) -> {
                                    val seasonRegex = Regex("(?:sezon|season)[\\s-]*([0-9]+)", RegexOption.IGNORE_CASE)
                                    val matchResult = seasonRegex.find(href)
                                    matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 1
                                }
                                name.contains("sezon", ignoreCase = true) || name.contains("season", ignoreCase = true) -> {
                                    val seasonRegex = Regex("(?:sezon|season)[\\s-]*([0-9]+)", RegexOption.IGNORE_CASE)
                                    val matchResult = seasonRegex.find(name)
                                    matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 1
                                }
                                else -> 1
                            }
                            
                            // Bölüm numarasını extract et
                            val episodeNum = when {
                                name.contains("Bölüm", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("bölüm").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                name.contains("Bölum", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("bölum").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                name.contains("Bolum", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("bolum").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                name.contains("Episode", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("episode").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                name.contains("B.", ignoreCase = true) -> {
                                    val lowerName = name.lowercase()
                                    val numText = lowerName.substringAfter("b.").trim()
                                    numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                href.contains("bolum", ignoreCase = true) || href.contains("episode", ignoreCase = true) -> {
                                    val episodeRegex = Regex("(?:bolum|episode)[\\s-]*([0-9]+)", RegexOption.IGNORE_CASE)
                                    val matchResult = episodeRegex.find(href)
                                    matchResult?.groupValues?.get(1)?.toIntOrNull()
                                }
                                name.matches(Regex(".*\\d+.*", RegexOption.IGNORE_CASE)) -> {
                                    name.replace(Regex("[^0-9]"), "").toIntOrNull()
                                }
                                href.matches(Regex(".*\\d+.*", RegexOption.IGNORE_CASE)) -> {
                                    // URL'den sayıyı çıkar - son rakam genellikle bölüm numarasıdır
                                    val numbers = Regex("\\d+").findAll(href).map { it.value.toInt() }.toList()
                                    numbers.lastOrNull()
                                }
                                else -> 1 // Bölüm numarası bulunamazsa 1 atanır
                            }
                            
                            // URL'yi fix
                            val data = fixUrlNull(href)
                            if (data != null) {
                                val fullUrl = if (data.startsWith("?") || data.startsWith("/")) {
                                    if (data.startsWith("?")) url + data else mainUrl + data
                                } else {
                                    data
                                }
                                
                                Log.d(this@Dizifun.name, "Adding alternative episode: name=$name, season=$seasonNum, episode=$episodeNum, url=$fullUrl")
                                
                                allEpisodes.add(
                                    newEpisode(fullUrl) {
                                        this.name = name
                                        this.season = seasonNum
                                        this.episode = episodeNum ?: 1
                                    }
                                )
                            }
                        }
                    }
                    
                    // Eğer hala bölüm bulunamadıysa, bu içerik tek bölümlük bir dizi/film olabilir
                    if (allEpisodes.isEmpty()) {
                        Log.d(this@Dizifun.name, "No episodes found, treating as a single episode")
                        allEpisodes.add(
                            newEpisode(url) {
                                this.name = title
                                this.season = 1
                                this.episode = 1
                            }
                        )
                    }
                }
                
                Log.d(this@Dizifun.name, "Total episodes found: ${allEpisodes.size}")
                allEpisodes
            } else null

            return if (type == TvType.TvSeries) {
                newTvSeriesLoadResponse(title, url, type, episodes ?: emptyList()) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.rating = rating
                    this.duration = duration
                    this.tags = tags
                    addActors(actors)
                    addTrailer(trailer)
                }
            } else {
                newMovieLoadResponse(title, url, type, url) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.rating = rating
                    this.duration = duration
                    this.tags = tags
                    addActors(actors)
                    addTrailer(trailer)
                }
            }
        } catch (e: Exception) {
            Log.e(this@Dizifun.name, "Video detayları yüklenirken hata: ${e.message}")
            e.printStackTrace()
            return null
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
            val rawIframeSrc = doc.select("iframe").attr("src")
            if (rawIframeSrc.isNotEmpty()) {
                // İframe src'yi kontrol et ve gerekirse decode et
                val iframeSrc = checkAndDecodeIframeSrc(rawIframeSrc)
                val normalizedIframeSrc = normalizeUrl(iframeSrc)
                Log.d(this@Dizifun.name, "İframe kaynağı bulundu: $normalizedIframeSrc")
                
                // İframe kaynağı ile ilgili detaylı bilgileri logla
                logSourceInfo(normalizedIframeSrc)
                
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
                    
                    if (hexUrls.isEmpty()) {
                        Log.d(this@Dizifun.name, "Hex-encoded URL bulunamadı, alternatif arama yöntemleri deneniyor...")
                        
                        // Doğrudan video tag'ı ara
                        val videoSources = doc.select("video source").map { it.attr("src") }
                        if (videoSources.isNotEmpty()) {
                            Log.d(this@Dizifun.name, "Video kaynak tag'ları bulundu: ${videoSources.size}")
                            videoSources.forEach { sourceUrl ->
                                val normalizedSourceUrl = normalizeUrl(sourceUrl)
                                callback.invoke(
                                    newExtractorLink(
                                        this@Dizifun.name,
                                        "Direct (Video Tag)",
                                        normalizedSourceUrl,
                                        if (normalizedSourceUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    ) {
                                        this.referer = url
                                        this.quality = Qualities.Unknown.value
                                    }
                                )
                            }
                        }
                    } else {
                        Log.d(this@Dizifun.name, "${hexUrls.size} hex-encoded URL bulundu")
                    }
                    
                    // Bulunan URL'leri işle
                    hexUrls.forEach { videoUrl ->
                        val normalizedVideoUrl = normalizeUrl(videoUrl)
                        Log.d(this@Dizifun.name, "Bulunan video URL'si: $normalizedVideoUrl")
                        
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
                            else -> "Direct"
                        }
                        
                        // .m3u8 dosyaları için özel işlem
                        callback.invoke(
                            newExtractorLink(
                                this@Dizifun.name,
                                if (normalizedVideoUrl.contains(".m3u8")) "${sourceName} HLS" else sourceName,
                                normalizedVideoUrl,
                                if (normalizedVideoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = normalizedIframeSrc
                                this.quality = quality
                            }
                        )
                    }
                    
                    // Alternatif embed URL'leri kontrol et
                    val altEmbeds = doc.select("a.alter_video").map { it.attr("href") }
                    if (altEmbeds.isNotEmpty()) {
                        Log.d(this@Dizifun.name, "Alternatif embedler bulundu: ${altEmbeds.size}")
                        altEmbeds.forEach { embedUrl ->
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
                                
                                val altHexUrls = findHexEncodedUrls(embedDoc)
                                Log.d(this@Dizifun.name, "Alternatif embeddeki hex URL sayısı: ${altHexUrls.size}")
                                
                                altHexUrls.forEach { videoUrl ->
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
                                        newExtractorLink(
                                            this@Dizifun.name,
                                            if (normalizedAltVideoUrl.contains(".m3u8")) "${sourceName} HLS" else sourceName,
                                            normalizedAltVideoUrl,
                                            if (normalizedAltVideoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                        ) {
                                            this.referer = normalizedEmbedUrl
                                            this.quality = quality
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(this@Dizifun.name, "Alternatif embed yüklenirken hata: ${e.message}")
                            }
                        }
                    }
                    
                    // Altyazıları bul
                    doc.select("track").forEach { track ->
                        val subLabel = track.attr("label").ifEmpty { "Türkçe" }
                        val subLang = track.attr("srclang").ifEmpty { "tr" }
                        val subUrl = track.attr("src")
                        
                        if (subUrl.isNotEmpty()) {
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
                } catch (e: Exception) {
                    Log.e(this@Dizifun.name, "İframe içeriği işlenirken hata: ${e.message}")
                }
            } else {
                Log.e(this@Dizifun.name, "İframe bulunamadı, doğrudan video tag'ı aranıyor...")
                
                // Doğrudan video tag'ı ara
                val videoSources = doc.select("video source").map { it.attr("src") }
                if (videoSources.isNotEmpty()) {
                    Log.d(this@Dizifun.name, "Video kaynak tag'ları bulundu: ${videoSources.size}")
                    videoSources.forEach { sourceUrl ->
                        val normalizedSourceUrl = normalizeUrl(sourceUrl)
                        callback.invoke(
                            newExtractorLink(
                                this@Dizifun.name,
                                "Direct (Video Tag)",
                                normalizedSourceUrl,
                                if (normalizedSourceUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = url
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }
                } else {
                    Log.e(this@Dizifun.name, "Hiçbir video kaynağı bulunamadı")
                }
            }
        } catch (e: Exception) {
            Log.e(this@Dizifun.name, "Video linkleri yüklenirken hata: ${e.message}")
            return false
        }
        
        return true
    }
    
    // Hex string'i normal string'e çeviren fonksiyon
    private fun hexToString(hexString: String): String {
        val result = StringBuilder()
        var i = 0
        
        while (i < hexString.length) {
            // Her iki karakteri bir hex değeri olarak değerlendir
            if (i + 1 < hexString.length) {
                try {
                    val hexPair = hexString.substring(i, i + 2)
                    val decimal = hexPair.toInt(16)
                    result.append(decimal.toChar())
                    i += 2
                } catch (e: NumberFormatException) {
                    // Geçersiz hex karakteri, olduğu gibi ekle
                    result.append(hexString[i])
                    i++
                }
            } else {
                // Son tek karakteri olduğu gibi ekle
                result.append(hexString[i])
                i++
            }
        }
        
        return result.toString()
    }
    
    // JavaScript içindeki hex-encoded URL'leri bul
    private fun findHexEncodedUrls(html: String): List<String> {
        val foundUrls = mutableListOf<String>()
        
        try {
            // \x ile başlayan hex dizilerini bul (JavaScript hex encoding)
            val hexPattern = "\\\\x[0-9a-fA-F]{2}".toRegex()
            
            // HTML içeriğini var tanımlamalarına göre parçala
            val splitByVar = html.split("var")
            
            // Hex içeren blokları filtrele
            splitByVar.forEach { block ->
                if (hexPattern.containsMatchIn(block)) {
                    // Uzun hex dizileri URL olabilir
                    val potentialHexString = block.substringAfter("=").substringBefore(";").trim()
                    
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
            
            // hexToString fonksiyonuna yapılan çağrıları ara
            val hexFunctionCallPattern = "hexToString\\([\"']([0-9a-fA-F]+)[\"']\\)".toRegex()
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
            
            // Doğrudan hex string'leri bul
            val directHexPattern = "[\"']([0-9a-fA-F]{10,})[\"']".toRegex()
            val directHexMatches = directHexPattern.findAll(html)
            
            directHexMatches.forEach { matchResult ->
                try {
                    val hexValue = matchResult.groupValues[1]
                    val decodedValue = hexToString(hexValue)
                    if (decodedValue.contains("http") || 
                        decodedValue.contains(".mp4") || 
                        decodedValue.contains(".m3u8")) {
                        foundUrls.add(decodedValue)
                    }
                } catch (e: Exception) {
                    Log.e(this@Dizifun.name, "Direct hex decode error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(this@Dizifun.name, "Error finding hex URLs: ${e.message}")
        }
        
        // Tekrarlanan URL'leri temizle
        return foundUrls.distinct()
    }

    // İframe src değerinin hex-encoded olup olmadığını kontrol et ve deşifre et
    private fun checkAndDecodeIframeSrc(iframeSrc: String): String {
        // Eğer normal bir URL ise doğrudan döndür
        if (iframeSrc.startsWith("http")) {
            return iframeSrc
        }
        
        // JavaScript:something(...) formatında mı?
        if (iframeSrc.startsWith("javascript:")) {
            // Hex-encoded URL içeriyor mu kontrol et
            val hexMatches = "\\\\x[0-9a-fA-F]{2}".toRegex().findAll(iframeSrc)
            if (hexMatches.any()) {
                try {
                    // JavaScript: kısmını çıkar
                    val jsContent = iframeSrc.substringAfter("javascript:")
                    // Hex encode edilmiş kısımları bul
                    val possibleUrls = findHexEncodedUrls(jsContent)
                    // HTTP ile başlayan ilk URL'yi döndür
                    val httpUrl = possibleUrls.firstOrNull { it.startsWith("http") }
                    if (httpUrl != null) {
                        Log.d(this@Dizifun.name, "Javascript'ten çözülen URL: $httpUrl")
                        return httpUrl
                    }
                } catch (e: Exception) {
                    Log.e(this@Dizifun.name, "Javascript iframe src çözülemedi: ${e.message}")
                }
            }
        }
        
        // Doğrudan hex-encoded olabilir mi?
        if (iframeSrc.length > 20 && iframeSrc.all { it.isLetterOrDigit() }) {
            try {
                val decoded = hexToString(iframeSrc)
                if (decoded.startsWith("http")) {
                    Log.d(this@Dizifun.name, "Hex-encoded iframe src çözüldü: $decoded")
                    return decoded
                }
            } catch (e: Exception) {
                Log.e(this@Dizifun.name, "Hex iframe src çözülemedi: ${e.message}")
            }
        }
        
        // Hiçbir şey yapılamadıysa orijinal değeri döndür
        return iframeSrc
    }

    // Debug için bilgileri logla - suspend fonksiyon olarak değiştirildi
    private suspend fun logSourceInfo(url: String) {
        try {
            Log.d(this@Dizifun.name, "URL işleniyor: $url")
            val response = app.get(url, headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to mainUrl
            ))
            Log.d(this@Dizifun.name, "HTTP Durum: ${response.code}")
            Log.d(this@Dizifun.name, "Content-Type: ${response.headers["Content-Type"]}")
            
            val contentLength = response.headers["Content-Length"]?.toIntOrNull() ?: 0
            Log.d(this@Dizifun.name, "İçerik Boyutu: ${contentLength / 1024} KB")
            
            // İlk 100 karakteri logla
            val previewContent = response.text.take(100).replace("\n", " ")
            Log.d(this@Dizifun.name, "İçerik Önizleme: $previewContent...")
        } catch (e: Exception) {
            Log.e(this@Dizifun.name, "Kaynak bilgisi loglanırken hata: ${e.message}")
        }
    }
    
    // URL'yi düzelt ve normalleştir
    private fun normalizeUrl(url: String): String {
        var fixedUrl = url
        
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
        
        return fixedUrl
    }
}