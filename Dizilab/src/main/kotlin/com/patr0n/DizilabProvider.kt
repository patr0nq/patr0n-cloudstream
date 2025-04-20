package com.patr0n

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class DizilabProvider : MainAPI() {
    override var mainUrl              = "https://dizilab.live"
    override var name                 = "Dizilab"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/arsiv"                                 to "Tüm Diziler",
        "${mainUrl}/arsiv?tur=Aksiyon"                     to "Aksiyon",
        "${mainUrl}/arsiv?tur=Dram"                        to "Dram",
        "${mainUrl}/arsiv?tur=Komedi"                      to "Komedi",
        "${mainUrl}/arsiv?tur=Bilim%20Kurgu"               to "Bilim Kurgu",
        "${mainUrl}/arsiv?tur=Gerilim"                     to "Gerilim"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${if (page > 1) "?page=$page" else ""}").document
        val home = document.select("div.col-6.col-md-3.mb-4").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("img")?.attr("alt") ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama?q=${query}").document

        return document.select("div.col-6.col-md-3.mb-4").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("img")?.attr("alt") ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("img.img-fluid.rounded")?.attr("src"))
        val description = document.selectFirst("div.card-body p")?.text()?.trim()
        val year = document.select("div.card-body p").getOrNull(1)?.text()?.substringAfter("Yapım Yılı:")?.trim()?.toIntOrNull()
        val tags = document.select("div.card-body p").getOrNull(2)?.text()?.substringAfter("Tür:")?.split(",")?.map { it.trim() }
        
        val episodes = document.select("ul.list-group.list-group-flush li a").mapNotNull {
            val name = it.text().trim()
            val epHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            
            Episode(
                data = epHref,
                name = name
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("STF", "data » ${data}")
        val document = app.get(data).document

        // Örnek video URL'si bulunduğunda
        val videoUrl = document.selectFirst("video source")?.attr("src")
        if (videoUrl != null) {
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videoUrl,
                    ExtractorLinkType.VIDEO
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                    this.isM3u8 = videoUrl.endsWith(".m3u8")
                }
            )
        }

        // TODO:
        // loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)

        return true
    }
}