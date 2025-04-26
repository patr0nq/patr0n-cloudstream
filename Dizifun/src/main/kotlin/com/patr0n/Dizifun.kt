// ! Bu araç @patr0n tarafından yazılmıştır.

package com.patr0n

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class Dizifun : MainAPI() {
    override var mainUrl              = "https://dizifun2.com"
    override var name                 = "Dizifun"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler"           to "Tüm Diziler",
        "${mainUrl}/yabanci-diziler"   to "Yabancı Diziler",
        "${mainUrl}/yerli-diziler"     to "Yerli Diziler",
        "${mainUrl}/kore-dizileri"     to "Kore Dizileri",
        "${mainUrl}/netflix-dizileri"  to "Netflix Dizileri",
        "${mainUrl}/filmler"          to "Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("div.movie-item").mapNotNull { it.diziler() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val title     = this.selectFirst("div.movie-title h3")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return if (href.contains("/film/")) {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.movie-item").mapNotNull { it.diziler() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("div.movie-title h1")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.movie-poster img")?.attr("src"))
        val year        = document.selectFirst("div.movie-info span:contains(Yapım Yılı)")?.nextElementSibling()?.text()?.toIntOrNull()
        val description = document.selectFirst("div.movie-description")?.text()?.trim()
        val tags        = document.select("div.movie-info span:contains(Tür) + a").map { it.text().trim() }
        val rating      = document.selectFirst("div.movie-info span:contains(IMDB)")?.nextElementSibling()?.text()?.toRatingInt()
        val actors      = document.selectFirst("div.movie-info span:contains(Oyuncular)")?.nextElementSibling()?.text()?.split(", ")?.map { Actor(it.trim()) }

        return if (url.contains("/film/")) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year      = year
                this.plot      = description
                this.tags      = tags
                this.rating    = rating
                addActors(actors)
            }
        } else {
            val episodes = document.select("div.episode-item").mapNotNull {
                val epName    = it.selectFirst("span.episode-title")?.text()?.trim() ?: return@mapNotNull null
                val epHref    = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val epEpisode = Regex("(\\d+)\\.Bölüm").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                val epSeason  = Regex("(\\d+)\\.Sezon").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year      = year
                this.plot      = description
                this.tags      = tags
                this.rating    = rating
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZF", "data » $data")
        val document = app.get(data).document

        val iframes = document.select("div.player-embed iframe").mapNotNull { it.attr("src") }

        iframes.forEach { iframe ->
            Log.d("DZF", "iframe » $iframe")
            loadExtractor(iframe, data, subtitleCallback, callback)
        }

        return true
    }
}