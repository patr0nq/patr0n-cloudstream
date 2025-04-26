package com.patr0n

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Dizifun : MainAPI() {
    override var mainUrl = "https://dizifun2.com"
    override var name = "Dizifun"
    override val hasMainPage = true
    override var lang = "tr"

    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val home = document.select("div.editor_sec-grid").mapNotNull {
            it.toSearchResponse()
        }
        return HomePageResponse(home)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = this.select("h3.editor_sec-title").text() ?: return null
        val href = this.select("a.editor_sec-link").attr("href") ?: return null
        val posterUrl = this.select("img.editor_sec-image").attr("src")
        val year = this.select("p.editor_sec-date").text().toIntOrNull()

        return newMovieSearchResponse(title, href) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama?query=$query").document
        return document.select("div.editor_sec-item").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val title = document.select("h1.text-bold").text()
        val poster = document.select("div.media-cover img").attr("src")
        val year = document.select("ul.subnav li").text().substringAfter("Dizi Yılı: ").toIntOrNull()
        val description = document.select("p.text-muted").text()
        val type = if (document.select("div.series-info").text().contains("Dizi")) TvType.TvSeries else TvType.Movie

        val episodes = if (type == TvType.TvSeries) {
            document.select("div.episode-button").mapNotNull {
                val episode = it.text().substringAfter("Bölüm").toIntOrNull() ?: return@mapNotNull null
                Episode(
                    data = it.parent().attr("href"),
                    episode = episode,
                    name = "Bölüm $episode"
                )
            }
        } else null

        return newMovieLoadResponse(title, url, type) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.episodes = episodes
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val videoUrl = document.select("iframe").attr("src")
        
        if (videoUrl.isNotBlank()) {
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    videoUrl,
                    "",
                    Qualities.Unknown.value,
                    false
                )
            )
            return true
        }
        return false
    }
}
