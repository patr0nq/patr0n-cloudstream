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
        
        // Updated selector based on the HTML structure
        val home = document.select("div.editor_sec-item").mapNotNull {
            it.toMainPageResult()
        }
        
        // If no items found with the above selector, try alternative selectors
        if (home.isEmpty()) {
            val alternativeItems = document.select("div.episode-item, div.uk-overlay.uk-overlay-hover").mapNotNull {
                it.toMainPageResult()
            }
            
            if (alternativeItems.isNotEmpty()) {
                return newHomePageResponse(request.name, alternativeItems)
            }
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        // First try editor_sec-item structure
        val title = this.selectFirst("h3.editor_sec-title, h5.uk-panel-title")?.text()?.trim()
            ?: this.selectFirst("div.name")?.text()?.trim()
            ?: return null
            
        val href = fixUrlNull(this.selectFirst("a")?.attr("href"))
            ?: fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href"))
            ?: return null
            
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        
        // Try to extract year from different possible locations
        val year = this.selectFirst("p.editor_sec-date, span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
            ?: this.selectFirst("div.date")?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
            
        // Determine type based on URL
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama?query=$query").document
        
        // Try different selectors for search results
        val mainResults = document.select("div.uk-overlay.uk-overlay-hover").mapNotNull {
            it.toSearchResult()
        }
        
        if (mainResults.isNotEmpty()) return mainResults
        
        // Try alternative selector
        return document.select("div.editor_sec-item, div.episode-item").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h5.uk-panel-title, h3.editor_sec-title")?.text()?.trim() 
            ?: this.selectFirst("div.name")?.text()?.trim()
            ?: return null
            
        val href = fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href"))
            ?: fixUrlNull(this.selectFirst("a")?.attr("href"))
            ?: return null
            
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        
        val year = this.selectFirst("span.uk-display-block.uk-text-muted, p.editor_sec-date")?.text()?.trim()?.toIntOrNull()
            ?: this.selectFirst("div.date")?.text()?.substringBefore("-")?.trim()?.toIntOrNull()
            
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.text-bold, div.titlemob")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.media-cover img, img.imgboyut")?.attr("src"))
        
        // Try to extract year from different elements
        val year = document.selectFirst("ul.subnav li")?.text()?.substringAfter("Yıl: ")?.toIntOrNull()
        
        // Try different selectors for description
        val description = document.selectFirst("p.text-muted, div.descmobi")?.text()?.trim()
        
        val type = if (url.contains("/dizi/")) TvType.TvSeries else TvType.Movie
        val rating = document.selectFirst("span.rating")?.text()?.toRatingInt()
        val duration = document.selectFirst("span.runtime")?.text()?.split(" ")?.firstOrNull()?.toIntOrNull()
        val tags = document.select("div.genres a, ul.subnav li a").map { it.text().trim() }
        
        val actors = document.select("div.actors-container div.actor-card").mapNotNull {
            Actor(
                it.selectFirst("span.actor-name")?.text()?.trim() ?: return@mapNotNull null,
                fixUrlNull(it.selectFirst("img")?.attr("src"))
            )
        }
        
        val trailer = document.selectFirst("iframe[src*=youtube]")?.attr("src")?.let { fixUrl(it) }

        // Try different selectors for episodes
        val episodes = if (type == TvType.TvSeries) {
            val mainEpisodes = document.select("div.bolumtitle a").mapNotNull {
                val name = it.text().trim()
                val episode = name.substringAfter("Bölüm").trim().toIntOrNull() 
                    ?: name.replace(Regex("[^0-9]"), "").toIntOrNull()
                    ?: return@mapNotNull null
                    
                val data = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val fullUrl = if (data.startsWith("?")) url + data else data
                
                newEpisode(fullUrl) {
                    this.name = name
                    this.episode = episode
                }
            }
            
            if (mainEpisodes.isNotEmpty()) mainEpisodes else {
                // Alternative episode selector
                document.select("div.episode-button").mapNotNull {
                    val name = it.text().trim()
                    val episode = name.substringAfter("Bölüm").trim().toIntOrNull() ?: return@mapNotNull null
                    val data = fixUrlNull(it.parent()?.attr("href")) ?: return@mapNotNull null
                    
                    newEpisode(data) {
                        this.name = name
                        this.episode = episode
                    }
                }
            }
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
            // Handle encoded iframe URLs
            val decodedIframe = if (iframe.startsWith("//")) {
                "https:$iframe"
            } else {
                iframe
            }
            
            val iframeDoc = app.get(decodedIframe, headers = headers).document
            var videoUrl = iframeDoc.selectFirst("source")?.attr("src")
            
            // If direct source not found, look for embedded players
            if (videoUrl.isNullOrBlank()) {
                // Try to find m3u8 in page source
                val pageSource = iframeDoc.outerHtml()
                
                val m3u8Regex = Regex("['\"](https?://[^'\"]*\\.m3u8[^'\"]*)['\"]")
                val mp4Regex = Regex("['\"](https?://[^'\"]*\\.mp4[^'\"]*)['\"]")
                
                val m3u8Match = m3u8Regex.find(pageSource)?.groupValues?.get(1)
                val mp4Match = mp4Regex.find(pageSource)?.groupValues?.get(1)
                
                videoUrl = m3u8Match ?: mp4Match
            }
            
            if (!videoUrl.isNullOrBlank()) {
                val isM3u8 = videoUrl.contains(".m3u8")
                val extractorType = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                
                // Determine quality based on URL
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

                // Extract subtitles
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
}