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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        
        // Dizifun ana sayfasındaki içerikleri çekmek için ana seçiciler
        val home = ArrayList<SearchResponse>()
        
        // Editör seçimleri bölümünü tara
        document.select("div.editor_sec-item, div.movies_recent-item, div.featuredseries_recent-item, div.plattab_recent-item").forEach { element ->
            val result = element.toMainPageResult()
            if (result != null) {
                home.add(result)
            }
        }
        
        // Başka bir içerik formatını kontrol et - son eklenen bölümler
        if (home.isEmpty()) {
            document.select("div.episode-item").forEach { element ->
                val result = element.toEpisodeResult()
                if (result != null) {
                    home.add(result)
                }
            }
        }
        
        // Eski format içerikleri de kontrol et
        if (home.isEmpty()) {
            document.select("div.uk-overlay.uk-overlay-hover").forEach { element ->
                val result = element.toMainPageResult()
                if (result != null) {
                    home.add(result)
                }
            }
        }
        
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
        // Dizi/film başlığını bul
        val title = when {
            this.selectFirst("h3.editor_sec-title") != null -> this.selectFirst("h3.editor_sec-title")?.text()?.trim()
            this.selectFirst("h3.movies_recent-title") != null -> this.selectFirst("h3.movies_recent-title")?.text()?.trim()
            this.selectFirst("h3.featuredseries_recent-title") != null -> this.selectFirst("h3.featuredseries_recent-title")?.text()?.trim()
            this.selectFirst("h3.plattab_recent-title") != null -> this.selectFirst("h3.plattab_recent-title")?.text()?.trim()
            this.selectFirst("h5.uk-panel-title") != null -> this.selectFirst("h5.uk-panel-title")?.text()?.trim()
            this.selectFirst("div.name") != null -> this.selectFirst("div.name")?.text()?.trim()
            else -> null
        } ?: return null
        
        // Link URL'sini bul
        val href = when {
            this.selectFirst("a.editor_sec-link") != null -> fixUrlNull(this.selectFirst("a.editor_sec-link")?.attr("href"))
            this.selectFirst("a.movies_recent-link") != null -> fixUrlNull(this.selectFirst("a.movies_recent-link")?.attr("href"))
            this.selectFirst("a.featuredseries_recent-link") != null -> fixUrlNull(this.selectFirst("a.featuredseries_recent-link")?.attr("href"))
            this.selectFirst("a.plattab_recent-link") != null -> fixUrlNull(this.selectFirst("a.plattab_recent-link")?.attr("href"))
            this.selectFirst("a.uk-position-cover") != null -> fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href"))
            this.selectFirst("a") != null -> fixUrlNull(this.selectFirst("a")?.attr("href"))
            else -> null
        } ?: return null
        
        // Poster URL'sini bul
        val posterUrl = when {
            this.selectFirst("img.editor_sec-image") != null -> fixUrlNull(this.selectFirst("img.editor_sec-image")?.attr("src"))
            this.selectFirst("img.movies_recent-image") != null -> fixUrlNull(this.selectFirst("img.movies_recent-image")?.attr("src"))
            this.selectFirst("img.featuredseries_recent-image") != null -> fixUrlNull(this.selectFirst("img.featuredseries_recent-image")?.attr("src")) 
            this.selectFirst("img.plattab_recent-image") != null -> fixUrlNull(this.selectFirst("img.plattab_recent-image")?.attr("src"))
            this.selectFirst("img") != null -> fixUrlNull(this.selectFirst("img")?.attr("src"))
            else -> null
        }
        
        // Yılı bul
        val year = when {
            this.selectFirst("p.editor_sec-date") != null -> this.selectFirst("p.editor_sec-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("p.movies_recent-date") != null -> this.selectFirst("p.movies_recent-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("p.featuredseries_recent-date") != null -> this.selectFirst("p.featuredseries_recent-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("p.plattab_recent-date") != null -> this.selectFirst("p.plattab_recent-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("span.uk-display-block.uk-text-muted") != null -> this.selectFirst("span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("div.date") != null -> this.selectFirst("div.date")?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
            else -> null
        }
        
        // URL'ye göre içerik tipini belirle
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama?query=$query").document
        val searchResults = ArrayList<SearchResponse>()
        
        // Farklı içerik tiplerini tara
        document.select("div.uk-overlay.uk-overlay-hover, div.editor_sec-item, div.movies_recent-item, div.featuredseries_recent-item, div.episode-item").forEach { element ->
            val result = element.toSearchResult()
            if (result != null) {
                searchResults.add(result)
            }
        }
        
        return searchResults
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Başlık, URL ve poster için birçok olası seçiciyi kontrol et
        val title = when {
            this.selectFirst("h3.editor_sec-title, h3.movies_recent-title, h3.featuredseries_recent-title, h5.uk-panel-title") != null -> 
                this.selectFirst("h3.editor_sec-title, h3.movies_recent-title, h3.featuredseries_recent-title, h5.uk-panel-title")?.text()?.trim()
            this.selectFirst("div.name") != null -> this.selectFirst("div.name")?.text()?.trim()
            else -> null
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
        val year = when {
            this.selectFirst("p.editor_sec-date, p.movies_recent-date, p.featuredseries_recent-date") != null -> 
                this.selectFirst("p.editor_sec-date, p.movies_recent-date, p.featuredseries_recent-date")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("span.uk-display-block.uk-text-muted") != null -> this.selectFirst("span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
            this.selectFirst("div.date") != null -> this.selectFirst("div.date")?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
            else -> null
        }
        
        // İçerik tipini belirle
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        // Başlığı bul
        val title = document.selectFirst("h1.text-bold, div.titlemob")?.text()?.trim() ?: return null
        
        // Posteri bul
        val poster = fixUrlNull(document.selectFirst("div.media-cover img, img.imgboyut, img.responsive-img")?.attr("src"))
        
        // Yılı bul
        val yearText = document.select("ul.subnav li").find { it.text().contains("Yıl:") || it.text().contains("Dizi Yılı:") }?.text() ?: ""
        val year = yearText.substringAfter("Yıl:").substringAfter("Dizi Yılı:").trim().toIntOrNull()
        
        // Açıklamayı bul
        val description = document.selectFirst("p.text-muted, div.descmobi")?.text()?.trim()
        
        // İçerik tipini belirle
        val type = if (url.contains("/dizi/")) TvType.TvSeries else TvType.Movie
        
        // Ek bilgileri bul
        val rating = document.selectFirst("span.rating")?.text()?.toRatingInt()
        val duration = document.selectFirst("span.runtime")?.text()?.split(" ")?.firstOrNull()?.toIntOrNull()
        
        // Kategori bilgilerini bul
        val tags = document.select("div.genres a, ul.subnav li a, div.series-info").map { it.text().trim() }
        
        // Oyuncuları bul
        val actors = document.select("div.actors-container div.actor-card").mapNotNull {
            Actor(
                it.selectFirst("span.actor-name")?.text()?.trim() ?: return@mapNotNull null,
                fixUrlNull(it.selectFirst("img")?.attr("src"))
            )
        }
        
        // Fragmanı bul
        val trailer = document.selectFirst("iframe[src*=youtube]")?.attr("src")?.let { fixUrl(it) }

        // Bölümleri bul (dizi ise)
        val episodes = if (type == TvType.TvSeries) {
            val allEpisodes = ArrayList<Episode>()
            
            // Season/Tab kutularını bul
            val seasonTabs = document.select("div.tabcontent2")
            if (seasonTabs.isNotEmpty()) {
                seasonTabs.forEach { seasonTab ->
                    val seasonId = seasonTab.attr("id").replace("season", "").toIntOrNull() ?: 1
                    
                    // Her sezon tab'ındaki bölümleri bul
                    seasonTab.select("div.bolumtitle a, a[href*=episode]").forEach { episodeElement ->
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
                document.select("div.episode-button, div.bolumtitle a").forEach { episodeElement ->
                    val name = episodeElement.text().trim()
                    val episodeNum = name.substringAfter("Bölüm").trim().toIntOrNull() 
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
        val document = app.get(data).document
        val iframe = document.selectFirst("iframe#playerFrame, iframe")?.attr("src") ?: return false
        
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Referer" to mainUrl
        )

        try {
            // Iframe URL'sini düzelt
            val decodedIframe = when {
                iframe.startsWith("//") -> "https:$iframe"
                !iframe.startsWith("http") -> "$mainUrl$iframe"
                else -> iframe
            }
            
            val iframeDoc = app.get(decodedIframe, headers = headers).document
            var videoUrl = iframeDoc.selectFirst("source")?.attr("src")
            
            // Doğrudan kaynak bulunamadıysa, gömülü oynatıcıları kontrol et
            if (videoUrl.isNullOrBlank()) {
                // Sayfa kaynaklarında m3u8/mp4 linklerini bul
                val pageSource = iframeDoc.outerHtml()
                
                // Farklı regex desenlerini dene
                val m3u8Regex = Regex("['\"](https?://[^'\"]*\\.m3u8[^'\"]*)['\"]")
                val mp4Regex = Regex("['\"](https?://[^'\"]*\\.mp4[^'\"]*)['\"]")
                val hexRegex = Regex("hexToString\\(\"([0-9a-fA-F]+)\"\\)")
                
                val m3u8Match = m3u8Regex.find(pageSource)?.groupValues?.get(1)
                val mp4Match = mp4Regex.find(pageSource)?.groupValues?.get(1)
                
                // Hex kodlanmış URL'yi kontrol et
                val hexMatch = hexRegex.find(pageSource)?.groupValues?.get(1)
                if (!hexMatch.isNullOrBlank()) {
                    try {
                        val decodedHex = hexToString(hexMatch)
                        if (decodedHex.contains(".m3u8") || decodedHex.contains(".mp4")) {
                            videoUrl = decodedHex
                        }
                    } catch (e: Exception) {
                        Log.e(this.name, "Error decoding hex string: ${e.message}")
                    }
                }
                
                if (videoUrl.isNullOrBlank()) {
                    videoUrl = m3u8Match ?: mp4Match
                }
            }
            
            if (!videoUrl.isNullOrBlank()) {
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
                    
                    subtitleCallback.invoke(
                        SubtitleFile(
                            label,
                            subUrl
                        )
                    )
                }
                return true
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