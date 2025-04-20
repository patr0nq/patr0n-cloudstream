package com.patr0n

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class DizilabProvider : MainAPI() {
    override var mainUrl = "https://dizilab.live"
    override var name = "Dizilab"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allSeriesUrl = "$mainUrl/arsiv?page=$page"
        val actionSeriesUrl = "$mainUrl/arsiv?tur=Aksiyon&page=$page"

        val allSeriesDoc = app.get(allSeriesUrl).document
        val actionSeriesDoc = app.get(actionSeriesUrl).document

        val allSeries = allSeriesDoc.select("div.col-6.col-md-3.mb-4").mapNotNull {
            val aTag = it.selectFirst("a")
            val imgTag = it.selectFirst("img")
            val title = imgTag?.attr("alt") ?: return@mapNotNull null
            val posterUrl = imgTag.attr("src")
            val link = fixUrl(aTag?.attr("href") ?: return@mapNotNull null)
            newAnimeSearchResponse(title, link, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }

        val actionSeries = actionSeriesDoc.select("div.col-6.col-md-3.mb-4").mapNotNull {
            val aTag = it.selectFirst("a")
            val imgTag = it.selectFirst("img")
            val title = imgTag?.attr("alt") ?: return@mapNotNull null
            val posterUrl = imgTag.attr("src")
            val link = fixUrl(aTag?.attr("href") ?: return@mapNotNull null)
            newAnimeSearchResponse(title, link, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }

        return HomePageResponse(
            listOf(
                HomePageList("Tüm Diziler", allSeries),
                HomePageList("Aksiyon Dizileri", actionSeries)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/arama?q=$query"
        val doc = app.get(url).document
        return doc.select("div.col-6.col-md-3.mb-4").mapNotNull {
            val aTag = it.selectFirst("a")
            val imgTag = it.selectFirst("img")
            val title = imgTag?.attr("alt") ?: return@mapNotNull null
            val posterUrl = imgTag.attr("src")
            val link = fixUrl(aTag?.attr("href") ?: return@mapNotNull null)
            newAnimeSearchResponse(title, link, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text() ?: "Bilinmeyen"
        val poster = doc.selectFirst("img.img-fluid.rounded")?.attr("src")
        val episodes = doc.select("ul.list-group.list-group-flush li a").mapNotNull {
            val epName = it.text()
            val epUrl = fixUrl(it.attr("href"))
            Episode(epUrl, epName)
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val videoUrl = doc.selectFirst("video source")?.attr("src")
        if (videoUrl != null) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = videoUrl,
                    referer = mainUrl,
                    quality = Qualities.P1080.value,
                    isM3u8 = videoUrl.endsWith(".m3u8")
                )
            )
        }
        return true
    }
}