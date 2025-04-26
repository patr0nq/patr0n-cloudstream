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
            Log.d(this.name, "Response for $url: ${response.substring(0, 200)}...")
        } else {
            Log.d(this.name, "Response for $url: $response")
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        Log.d(this.name, "Loading main page: ${request.data}")
        
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
        Log.d(this.name, "Found ${specialCategoryItems.size} special category items in ${request.name}")
        
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
            
            Log.d(this.name, "Found total ${allContentItems.size} items in category: ${request.name}")
            
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
        
        Log.d(this.name, "Total processed items for ${request.name}: ${home.size}")
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
        
        // URL'ye göre içerik tipini belirle
        val type = if (href.contains("/dizi/") || href.contains("sezon") || href.contains("bolum") ||
                     (title.contains("Sezon") && !title.contains("Film"))) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/arama?query=$query"
        Log.d(this.name, "Searching: $searchUrl")
        
        val document = app.get(searchUrl, headers = defaultHeaders).document
        val searchResults = ArrayList<SearchResponse>()
        
        // Ana arama sonuçlarını tara
        val colItems = document.select("div.col-md-2")
        Log.d(this.name, "Found ${colItems.size} col-md-2 items in search")
        
        colItems.forEach { element ->
            val result = element.toMainPageResult()
            if (result != null) {
                searchResults.add(result)
            }
        }
        
        // Alternatif arama sonuçlarını tara
        if (searchResults.isEmpty()) {
            val altItems = document.select("div.uk-overlay.uk-overlay-hover, div.editor_sec-item, div.movies_recent-item, div.featuredseries_recent-item, div.episode-item")
            Log.d(this.name, "Found ${altItems.size} alternative items in search")
            
            altItems.forEach { element ->
                val result = element.toSearchResult()
                if (result != null) {
                    searchResults.add(result)
                }
            }
        }
        
        Log.d(this.name, "Total search results: ${searchResults.size}")
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
        Log.d(this.name, "Loading details: $url")
        
        val document = app.get(url, headers = defaultHeaders).document

        // Başlığı bul
        val title = document.selectFirst("h1.text-bold, div.titlemob, div.head-title > h1")?.text()?.trim() ?: return null
        
        // Posteri bul - web sayfası içerik resmi kullanabilir
        val poster = fixUrlNull(document.selectFirst("div.media-cover img, img.imgboyut, img.responsive-img, div.platformmobile img")?.attr("src"))
        
        // Yılı bul
        val yearText = document.select("ul.subnav li, div.content_data").find { it.text().contains("Yıl:") || it.text().contains("Dizi Yılı:") }?.text() ?: ""
        val year = yearText.substringAfter("Yıl:").substringAfter("Dizi Yılı:").trim().toIntOrNull()
        
        // Açıklamayı bul
        val description = document.selectFirst("p.text-muted, div.descmobi, div.content_data > p")?.text()?.trim()
            ?: document.select("div.content_data, div.detail-text, div.description").firstOrNull()?.text()?.trim()
        
        // İçerik tipini belirle
        val type = if (url.contains("/dizi/") || url.contains("sezon") || url.contains("bolum")) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }
        
        // Ek bilgileri bul
        val rating = document.selectFirst("span.rating, span.rate")?.text()?.toRatingInt()
        val duration = document.selectFirst("span.runtime, div.content_data")?.text()?.let { 
            val durationRegex = Regex("(\\d+)\\s*dk")
            val match = durationRegex.find(it)
            match?.groupValues?.get(1)?.toIntOrNull()
        }
        
        // Kategori bilgilerini bul
        val tags = document.select("div.genres a, ul.subnav li a, div.series-info, div.content_data span").map { it.text().trim() }
        
        // Oyuncuları bul
        val actors = document.select("div.actors-container div.actor-card, div.oyuncular span").mapNotNull {
            val actorName = it.selectFirst("span.actor-name")?.text()?.trim() 
                ?: it.text().trim()
                ?: return@mapNotNull null
                
            val actorImg = fixUrlNull(it.selectFirst("img")?.attr("src"))
            
            Actor(actorName, actorImg)
        }
        
        // Fragmanı bul
        val trailer = document.selectFirst("iframe[src*=youtube], a.trailer-button")?.attr("src")?.let { fixUrl(it) }

        // Bölümleri bul (dizi ise)
        val episodes = if (type == TvType.TvSeries) {
            val allEpisodes = ArrayList<Episode>()
            
            // Season/Tab kutularını bul
            val seasonTabs = document.select("div.tabcontent2, div.seasons, div.episodes-container")
            if (seasonTabs.isNotEmpty()) {
                Log.d(this.name, "Found ${seasonTabs.size} season tabs")
                seasonTabs.forEach { seasonTab ->
                    // Sezon ID'sini bul (season1, season2 gibi ID'ler veya sıra numarası)
                    val seasonId = seasonTab.attr("id").replace(Regex("[^0-9]"), "").toIntOrNull() 
                        ?: seasonTabs.indexOf(seasonTab) + 1
                    
                    // Her sezon tab'ındaki bölümleri bul
                    val episodeElements = seasonTab.select("div.bolumtitle a, a[href*=episode], a.episode-button, a[href*=bolum], div.episode-button")
                    Log.d(this.name, "Found ${episodeElements.size} episodes in season $seasonId")
                    
                    episodeElements.forEach { episodeElement ->
                        val name = episodeElement.text().trim()
                        val href = episodeElement.attr("href")
                        
                        // Bölüm numarasını extrakt et
                        val episodeNum = when {
                            name.contains("Bölüm") -> {
                                val numText = name.substringAfter("Bölüm").trim()
                                numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                            }
                            name.contains("Bölum") -> {
                                val numText = name.substringAfter("Bölum").trim()
                                numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                            }
                            else -> name.replace(Regex("[^0-9]"), "").toIntOrNull()
                        } ?: return@forEach
                        
                        // URL'yi fix
                        val data = fixUrlNull(href) ?: return@forEach
                        val fullUrl = if (data.startsWith("?")) url + data else data
                        
                        allEpisodes.add(
                            newEpisode(fullUrl) {
                                this.name = name
                                this.season = seasonId
                                this.episode = episodeNum
                            }
                        )
                    }
                }
            } else {
                // Alternatif bölüm seçicisi - sezon tabları yoksa
                val episodeElements = document.select("div.episode-button, div.bolumtitle a, a.episode-button, div.episodes div, div.episodes a, a[href*=bolum]")
                Log.d(this.name, "Found ${episodeElements.size} episode elements")
                
                episodeElements.forEach { episodeElement ->
                    val name = episodeElement.text().trim()
                    if (name.contains("Bölüm") || name.contains("Bölum") || name.matches(Regex(".*\\d+.*"))) {
                        val episodeNum = when {
                            name.contains("Bölüm") -> {
                                val numText = name.substringAfter("Bölüm").trim()
                                numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                            }
                            name.contains("Bölum") -> {
                                val numText = name.substringAfter("Bölum").trim()
                                numText.replace(Regex("[^0-9]"), "").toIntOrNull()
                            }
                            else -> name.replace(Regex("[^0-9]"), "").toIntOrNull()
                        } ?: return@forEach
                        
                        val href = episodeElement.attr("href")
                        val parent = episodeElement.parent()
                        val data = if (href.isNotBlank()) {
                            fixUrlNull(href)
                        } else {
                            fixUrlNull(parent?.attr("href"))
                        } ?: return@forEach
                        
                        val fullUrl = if (data.startsWith("?")) url + data else data
                        
                        allEpisodes.add(
                            newEpisode(fullUrl) {
                                this.name = name
                                this.episode = episodeNum
                            }
                        )
                    }
                }
            }
            
            Log.d(this.name, "Total episodes found: ${allEpisodes.size}")
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
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = data
        Log.d(this.name, "Video linkleri yükleniyor: $url")
        
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
                Log.d(this.name, "İframe kaynağı bulundu: $normalizedIframeSrc")
                
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
                        Log.d(this.name, "Hex-encoded URL bulunamadı, alternatif arama yöntemleri deneniyor...")
                        
                        // Doğrudan video tag'ı ara
                        val videoSources = doc.select("video source").map { it.attr("src") }
                        if (videoSources.isNotEmpty()) {
                            Log.d(this.name, "Video kaynak tag'ları bulundu: ${videoSources.size}")
                            videoSources.forEach { sourceUrl ->
                                val normalizedSourceUrl = normalizeUrl(sourceUrl)
                                callback.invoke(
                                    newExtractorLink(
                                        this.name,
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
                        Log.d(this.name, "${hexUrls.size} hex-encoded URL bulundu")
                    }
                    
                    // Bulunan URL'leri işle
                    hexUrls.forEach { videoUrl ->
                        val normalizedVideoUrl = normalizeUrl(videoUrl)
                        Log.d(this.name, "Bulunan video URL'si: $normalizedVideoUrl")
                        
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
                                this.name,
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
                        Log.d(this.name, "Alternatif embedler bulundu: ${altEmbeds.size}")
                        altEmbeds.forEach { embedUrl ->
                            try {
                                val normalizedEmbedUrl = normalizeUrl(embedUrl)
                                Log.d(this.name, "Alternatif embed işleniyor: $normalizedEmbedUrl")
                                
                                val embedDoc = app.get(
                                    normalizedEmbedUrl,
                                    headers = mapOf(
                                        "User-Agent" to USER_AGENT,
                                        "Referer" to url
                                    )
                                ).text
                                
                                val altHexUrls = findHexEncodedUrls(embedDoc)
                                Log.d(this.name, "Alternatif embeddeki hex URL sayısı: ${altHexUrls.size}")
                                
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
                                            this.name,
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
                                Log.e(this.name, "Alternatif embed yüklenirken hata: ${e.message}")
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
                            Log.d(this.name, "Altyazı bulundu: $subLabel - $normalizedSubUrl")
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    subLang,
                                    normalizedSubUrl
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(this.name, "İframe içeriği işlenirken hata: ${e.message}")
                }
            } else {
                Log.e(this.name, "İframe bulunamadı, doğrudan video tag'ı aranıyor...")
                
                // Doğrudan video tag'ı ara
                val videoSources = doc.select("video source").map { it.attr("src") }
                if (videoSources.isNotEmpty()) {
                    Log.d(this.name, "Video kaynak tag'ları bulundu: ${videoSources.size}")
                    videoSources.forEach { sourceUrl ->
                        val normalizedSourceUrl = normalizeUrl(sourceUrl)
                        callback.invoke(
                            newExtractorLink(
                                this.name,
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
                    Log.e(this.name, "Hiçbir video kaynağı bulunamadı")
                }
            }
        } catch (e: Exception) {
            Log.e(this.name, "Video linkleri yüklenirken hata: ${e.message}")
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
                            Log.e(this.name, "Hex decode error: ${e.message}")
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
                    Log.e(this.name, "Hex function call decode error: ${e.message}")
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
                    Log.e(this.name, "Direct hex decode error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(this.name, "Error finding hex URLs: ${e.message}")
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
                        Log.d(this.name, "Javascript'ten çözülen URL: $httpUrl")
                        return httpUrl
                    }
                } catch (e: Exception) {
                    Log.e(this.name, "Javascript iframe src çözülemedi: ${e.message}")
                }
            }
        }
        
        // Doğrudan hex-encoded olabilir mi?
        if (iframeSrc.length > 20 && iframeSrc.all { it.isLetterOrDigit() }) {
            try {
                val decoded = hexToString(iframeSrc)
                if (decoded.startsWith("http")) {
                    Log.d(this.name, "Hex-encoded iframe src çözüldü: $decoded")
                    return decoded
                }
            } catch (e: Exception) {
                Log.e(this.name, "Hex iframe src çözülemedi: ${e.message}")
            }
        }
        
        // Hiçbir şey yapılamadıysa orijinal değeri döndür
        return iframeSrc
    }

    // Debug için bilgileri logla - suspend fonksiyon olarak değiştirildi
    private suspend fun logSourceInfo(url: String) {
        try {
            Log.d(this.name, "URL işleniyor: $url")
            val response = app.get(url, headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to mainUrl
            ))
            Log.d(this.name, "HTTP Durum: ${response.code}")
            Log.d(this.name, "Content-Type: ${response.headers["Content-Type"]}")
            
            val contentLength = response.headers["Content-Length"]?.toIntOrNull() ?: 0
            Log.d(this.name, "İçerik Boyutu: ${contentLength / 1024} KB")
            
            // İlk 100 karakteri logla
            val previewContent = response.text.take(100).replace("\n", " ")
            Log.d(this.name, "İçerik Önizleme: $previewContent...")
        } catch (e: Exception) {
            Log.e(this.name, "Kaynak bilgisi loglanırken hata: ${e.message}")
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