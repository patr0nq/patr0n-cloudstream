package com.nikyokki

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class Tlc : MainAPI() {
    override var mainUrl              = "https://www.tlctv.com.tr"
    override var name                 = "Tlc"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/kesfet/a-z/"                    to "A-Z Programlar",
        "${mainUrl}/kesfet/sira-disi-hayatlar/"     to "Sıra Dışı Hayatlar",
        "${mainUrl}/kesfet/ask-ve-iliski/"          to "Aşk ve İlişki",
        "${mainUrl}/kesfet/yemek/"                  to "Yemek",
        "${mainUrl}/kesfet/moda-ve-guzellik/"       to "Moda ve Güzellik",
        "${mainUrl}/kesfet/ev-ve-dekorasyon/"       to "Ev ve Dekorasyon"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}").document
        val home = document.select("div.card, div.card-container, div.program-card").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h3.card-title, h3.program-title, div.card-body h3")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a.card-link, a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img.card-img, img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { 
            this.posterUrl = posterUrl 
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama?q=${query}").document

        return document.select("div.card-container").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.card-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a.card-link")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img.card-img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { 
            this.posterUrl = posterUrl 
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.show-title, h1.program-title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.show-img img, div.program-image img")?.attr("src"))
        val description = document.selectFirst("div.show-description, div.program-description")?.text()?.trim()
        
        // Bölümleri topla
        val episodes = document.select("div.episode-card, div.episode-item, div.video-card").mapNotNull {
            val epTitle = it.selectFirst("h3.episode-title, div.episode-info h3, h3.video-title")?.text() ?: return@mapNotNull null
            val epHref = fixUrlNull(it.selectFirst("a.episode-link, a")?.attr("href")) ?: return@mapNotNull null
            val epThumb = fixUrlNull(it.selectFirst("img.episode-img, img")?.attr("src"))
            val epDesc = it.selectFirst("p.episode-desc, p.episode-description")?.text()
            
            newEpisode(epHref) {
                this.name = epTitle
                this.posterUrl = epThumb
                this.description = epDesc
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("TLC", "data » ${data}")
        val document = app.get(data).document

        // Video kaynağını bul
        val videoUrl = document.select("video source, source").firstOrNull()?.attr("src")
            ?: document.select("iframe").firstOrNull()?.attr("src")
            ?: document.select("div.video-player video").firstOrNull()?.attr("src")
            ?: document.select("div.video-container video").firstOrNull()?.attr("src")
            ?: document.select("div.player-container video").firstOrNull()?.attr("src")
        
        Log.d("TLC", "videoUrl » ${videoUrl}")
        
        if (videoUrl != null) {
            if (videoUrl.contains("youtube") || videoUrl.contains("vimeo")) {
                loadExtractor(videoUrl, mainUrl, subtitleCallback, callback)
            } else {
                val type = if (videoUrl.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = videoUrl,
                        type = type
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        return true
    }
}
