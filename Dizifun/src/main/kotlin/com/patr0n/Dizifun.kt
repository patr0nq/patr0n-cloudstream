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

    // Standart Request Headers - Tüm isteklerde kullanılacak
    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
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
        
        // Tüm içerik türlerini tek bir seferde topla (tüm kategoriler için çalışacak)
        val allContentItems = document.select(
            "div.col-md-2, " +
            "div.movies_recent-item, " + 
            "div.editor_sec-item, " + 
            "div.featuredseries_recent-item, " + 
            "div.plattab_recent-item, " +
            "div.episode-item, " +
            "div.uk-overlay.uk-overlay-hover, " +
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
        
        // Posteri bul
        val poster = fixUrlNull(document.selectFirst("div.media-cover img, img.imgboyut, img.responsive-img")?.attr("src"))
        
        // Yılı bul
        val yearText = document.select("ul.subnav li, div.content_data").find { it.text().contains("Yıl:") || it.text().contains("Dizi Yılı:") }?.text() ?: ""
        val year = yearText.substringAfter("Yıl:").substringAfter("Dizi Yılı:").trim().toIntOrNull()
        
        // Açıklamayı bul
        val description = document.selectFirst("p.text-muted, div.descmobi, div.content_data > p")?.text()?.trim()
        
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
            val seasonTabs = document.select("div.tabcontent2")
            if (seasonTabs.isNotEmpty()) {
                Log.d(this.name, "Found ${seasonTabs.size} season tabs")
                seasonTabs.forEach { seasonTab ->
                    val seasonId = seasonTab.attr("id").replace("season", "").toIntOrNull() ?: 1
                    
                    // Her sezon tab'ındaki bölümleri bul
                    val episodeElements = seasonTab.select("div.bolumtitle a, a[href*=episode], a.episode-button")
                    Log.d(this.name, "Found ${episodeElements.size} episodes in season $seasonId")
                    
                    episodeElements.forEach { episodeElement ->
                        val name = episodeElement.text().trim()
                        // Bölüm numarasını extrakt et
                        val episodeNum = name.substringAfter("Bölüm").trim().toIntOrNull() 
                            ?: name.replace(Regex("[^0-9]"), "").toIntOrNull()
                            ?: return@forEach
                        
                        val data = fixUrlNull(episodeElement.attr("href")) ?: return@forEach
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
                val episodeElements = document.select("div.episode-button, div.bolumtitle a, a.episode-button, div.episodes div, div.episodes a")
                Log.d(this.name, "Found ${episodeElements.size} episode elements")
                
                episodeElements.forEach { episodeElement ->
                    val name = episodeElement.text().trim()
                    if (name.contains("Bölüm") || name.contains("Bölum") || name.matches(Regex(".*\\d+.*"))) {
                        val episodeNum = name.substringAfter("Bölüm").substringAfter("Bölum").trim().toIntOrNull() 
                            ?: name.replace(Regex("[^0-9]"), "").toIntOrNull()
                            ?: return@forEach
                        
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
        Log.d(this.name, "Loading links: $data")
        
        val document = app.get(data, headers = defaultHeaders).document
        val iframe = document.selectFirst("iframe#playerFrame, iframe, div.video-bak iframe")?.attr("src")
        
        Log.d(this.name, "Found iframe: $iframe")
        
        if (iframe.isNullOrBlank()) {
            // iframe bulunmazsa, video divleri ile deneyelim
            val videoDiv = document.selectFirst("div.video-bak, div.player-frame, div#playerFrame")
            if (videoDiv != null) {
                Log.d(this.name, "Found video div: ${videoDiv.tagName()}")
                // Video div içindeki olası iframe'i kontrol et
                val nestedIframe = videoDiv.selectFirst("iframe")?.attr("src")
                if (!nestedIframe.isNullOrBlank()) {
                    return loadFromIframe(nestedIframe, data, subtitleCallback, callback)
                }
                
                // Doğrudan div içindeki video kaynağını dene
                val sourceTag = videoDiv.selectFirst("source")?.attr("src")
                if (!sourceTag.isNullOrBlank()) {
                    Log.d(this.name, "Found direct source: $sourceTag")
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = sourceTag,
                            ExtractorLinkType.VIDEO
                        ) {
                            this.referer = data
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    return true
                }
            }
            
            // Video elementleri deneyelim
            val videoElement = document.selectFirst("video")
            if (videoElement != null) {
                val sourceTag = videoElement.selectFirst("source")?.attr("src")
                if (!sourceTag.isNullOrBlank()) {
                    Log.d(this.name, "Found video element source: $sourceTag")
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = sourceTag,
                            ExtractorLinkType.VIDEO
                        ) {
                            this.referer = data
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    return true
                }
            }
            
            // JavaScript kaynağını deneyelim
            val jsContent = document.select("script").joinToString("\n") { it.html() }
            val m3u8Regex = Regex("['\"](https?://[^'\"]*\\.m3u8[^'\"]*)['\"]")
            val mp4Regex = Regex("['\"](https?://[^'\"]*\\.mp4[^'\"]*)['\"]")
            
            val m3u8Match = m3u8Regex.find(jsContent)?.groupValues?.get(1)
            val mp4Match = mp4Regex.find(jsContent)?.groupValues?.get(1)
            
            if (!m3u8Match.isNullOrBlank() || !mp4Match.isNullOrBlank()) {
                val videoUrl = m3u8Match ?: mp4Match ?: return false
                val isM3u8 = videoUrl.contains(".m3u8")
                Log.d(this.name, "Found video URL in JavaScript: $videoUrl")
                
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                        if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = data
                        this.quality = Qualities.Unknown.value
                    }
                )
                return true
            }
            
            return false
        }
        
        return loadFromIframe(iframe, data, subtitleCallback, callback)
    }
    
    private suspend fun loadFromIframe(
        iframe: String,
        refererUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Referer" to refererUrl
        )

        try {
            // Iframe URL'sini düzelt
            val decodedIframe = when {
                iframe.startsWith("//") -> "https:$iframe"
                !iframe.startsWith("http") -> "$mainUrl$iframe"
                else -> iframe
            }
            
            Log.d(this.name, "Loading iframe: $decodedIframe")
            val iframeResponse = app.get(decodedIframe, headers = headers)
            val iframeDoc = iframeResponse.document
            
            // Kaynak kodunu al, debugging için
            val fullSource = iframeDoc.outerHtml()
            Log.d(this.name, "Iframe source length: ${fullSource.length}")
            
            // Doğrudan source etiketini dene
            var videoUrl = iframeDoc.selectFirst("source")?.attr("src")
            
            // Doğrudan kaynak bulunamadıysa, gömülü oynatıcıları kontrol et
            if (videoUrl.isNullOrBlank()) {
                // Sayfa kaynaklarında m3u8/mp4 linklerini bul
                val pageSource = fullSource
                
                // Farklı regex desenlerini dene
                val m3u8Regex = Regex("['\"](https?://[^'\"]*\\.m3u8[^'\"]*)['\"]")
                val mp4Regex = Regex("['\"](https?://[^'\"]*\\.mp4[^'\"]*)['\"]")
                val srcRegex = Regex("src=['\"]([^'\"]+)['\"]")
                val hexRegex = Regex("hexToString\\(['\"]([0-9a-fA-F]+)['\"]\\)")
                val embedRegex = Regex("src=['\"](.+?)['\"]")
                
                val m3u8Match = m3u8Regex.find(pageSource)?.groupValues?.get(1)
                val mp4Match = mp4Regex.find(pageSource)?.groupValues?.get(1)
                val embedMatch = embedRegex.find(pageSource)?.groupValues?.get(1)
                
                Log.d(this.name, "m3u8 match: $m3u8Match, mp4 match: $mp4Match, embed match: $embedMatch")
                
                // Hex kodlanmış URL'yi kontrol et
                val hexMatches = hexRegex.findAll(pageSource).map { it.groupValues[1] }.toList()
                for (hexMatch in hexMatches) {
                    try {
                        val decodedHex = hexToString(hexMatch)
                        Log.d(this.name, "Decoded hex: $decodedHex")
                        if (decodedHex.contains(".m3u8") || decodedHex.contains(".mp4")) {
                            videoUrl = decodedHex
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(this.name, "Error decoding hex string: ${e.message}")
                    }
                }
                
                if (videoUrl.isNullOrBlank()) {
                    videoUrl = m3u8Match ?: mp4Match
                }
                
                // Eğer embed video varsa ve video hala bulunamadıysa
                if (videoUrl.isNullOrBlank()) {
                    // Tüm iframe'leri dene
                    val nestedIframes = iframeDoc.select("iframe")
                    for (nestedIframe in nestedIframes) {
                        val nestedSrc = nestedIframe.attr("src")
                        if (!nestedSrc.isNullOrBlank()) {
                            val nestedUrl = when {
                                nestedSrc.startsWith("//") -> "https:$nestedSrc"
                                !nestedSrc.startsWith("http") -> "$mainUrl$nestedSrc"
                                else -> nestedSrc
                            }
                            
                            try {
                                Log.d(this.name, "Loading nested iframe: $nestedUrl")
                                val nestedDoc = app.get(nestedUrl, headers = headers).document
                                val nestedSource = nestedDoc.outerHtml()
                                
                                val nestedM3u8 = m3u8Regex.find(nestedSource)?.groupValues?.get(1)
                                val nestedMp4 = mp4Regex.find(nestedSource)?.groupValues?.get(1)
                                
                                if (!nestedM3u8.isNullOrBlank() || !nestedMp4.isNullOrBlank()) {
                                    videoUrl = nestedM3u8 ?: nestedMp4
                                    break
                                }
                            } catch (e: Exception) {
                                Log.e(this.name, "Error loading nested iframe: ${e.message}")
                            }
                        }
                    }
                    
                    // Player.js ve benzeri dosyaları kontrol et
                    val scriptSrcs = iframeDoc.select("script[src]").map { it.attr("src") }
                    for (scriptSrc in scriptSrcs) {
                        if (scriptSrc.contains("player") || scriptSrc.contains(".js")) {
                            val scriptUrl = when {
                                scriptSrc.startsWith("//") -> "https:$scriptSrc"
                                !scriptSrc.startsWith("http") -> "$mainUrl$scriptSrc"
                                else -> scriptSrc
                            }
                            
                            try {
                                Log.d(this.name, "Checking JS file: $scriptUrl")
                                val jsContent = app.get(scriptUrl, headers = headers).text
                                
                                val jsM3u8 = m3u8Regex.find(jsContent)?.groupValues?.get(1)
                                val jsMp4 = mp4Regex.find(jsContent)?.groupValues?.get(1)
                                
                                if (!jsM3u8.isNullOrBlank() || !jsMp4.isNullOrBlank()) {
                                    videoUrl = jsM3u8 ?: jsMp4
                                    break
                                }
                            } catch (e: Exception) {
                                Log.e(this.name, "Error checking JS file: ${e.message}")
                            }
                        }
                    }
                    
                    // Diğer src değerlerini kontrol et
                    if (videoUrl.isNullOrBlank() && !embedMatch.isNullOrBlank()) {
                        val embedUrl = when {
                            embedMatch.startsWith("//") -> "https:$embedMatch"
                            !embedMatch.startsWith("http") -> "$mainUrl$embedMatch"
                            else -> embedMatch
                        }
                        
                        try {
                            Log.d(this.name, "Loading embed URL: $embedUrl")
                            val embedDoc = app.get(embedUrl, headers = headers).document
                            val embedSource = embedDoc.outerHtml()
                            
                            videoUrl = m3u8Regex.find(embedSource)?.groupValues?.get(1)
                                ?: mp4Regex.find(embedSource)?.groupValues?.get(1)
                            
                            Log.d(this.name, "Video URL from embed: $videoUrl")
                        } catch (e: Exception) {
                            Log.e(this.name, "Error processing embed: ${e.message}")
                        }
                    }
                }
            }
            
            if (!videoUrl.isNullOrBlank()) {
                Log.d(this.name, "Found video URL: $videoUrl")
                val isM3u8 = videoUrl.contains(".m3u8")
                val extractorType = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                
                // URL'ye göre kaliteyi belirle
                var quality = Qualities.Unknown.value
                when {
                    videoUrl.contains("1080p") -> quality = Qualities.P1080.value
                    videoUrl.contains("720p") -> quality = Qualities.P720.value
                    videoUrl.contains("480p") -> quality = Qualities.P480.value
                    videoUrl.contains("360p") -> quality = Qualities.P360.value
                }
                
                callback.invoke(
                    newExtractorLink(source = name, name = name, url = videoUrl, extractorType) {
                        this.referer = decodedIframe
                        this.quality = quality
                    }
                )

                // Altyazıları çıkart
                iframeDoc.select("track").forEach { track ->
                    val subUrl = fixUrlNull(track.attr("src")) ?: return@forEach
                    val label = track.attr("label").ifEmpty { "Türkçe" }
                    
                    Log.d(this.name, "Found subtitle: $label at $subUrl")
                    subtitleCallback.invoke(
                        SubtitleFile(
                            label,
                            subUrl
                        )
                    )
                }
                return true
            } else {
                Log.e(this.name, "No video URL found in iframe")
            }
        } catch (e: Exception) {
            Log.e(this.name, "Error loading links: ${e.message}")
            e.printStackTrace()
        }
        return false
    }
    
    // Hex string'i ASCII'ye dönüştür
    private fun hexToString(hex: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < hex.length) {
            val hexChar = hex.substring(i, i + 2)
            val decimal = Integer.parseInt(hexChar, 16)
            result.append(decimal.toChar())
            i += 2
        }
        return result.toString()
    }
}