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

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 250L // ? 0.25 saniye
    override var sequentialMainPageScrollDelay = 250L // ? 0.25 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/netflix"           to "Netflix Dizileri",
        "${mainUrl}/exxen"            to "Exxen Dizileri",
        "${mainUrl}/disney"           to "Disney+ Dizileri",
        "${mainUrl}/tabii-dizileri"   to "Tabii Dizileri",
        "${mainUrl}/blutv"            to "BluTV Dizileri",
        "${mainUrl}/todtv"            to "TodTV Dizileri",
        "${mainUrl}/gain"             to "Gain Dizileri",
        "${mainUrl}/hulu"             to "Hulu Dizileri",
        "${mainUrl}/primevideo"       to "PrimeVideo Dizileri",
        "${mainUrl}/hbomax"           to "HboMax Dizileri",
        "${mainUrl}/paramount"        to "Paramount+ Dizileri",
        "${mainUrl}/unutulmaz"        to "Unutulmaz Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

        val document = app.get(request.data, headers=headers).document
        val home     = document.select(".uk-width-medium-1-3.uk-width-large-1-6.uk-margin-bottom").mapNotNull { it.diziler() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val title     = this.selectFirst(".uk-panel-title.uk-text-truncate")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst(".uk-position-cover")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst(".uk-overlay img")?.attr("src"))

        return if (href.contains("/film/")) {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

        val document = app.get("${mainUrl}/?s=${query}", headers=headers).document

        return document.select(".uk-width-medium-1-3.uk-width-large-1-6.uk-margin-bottom").mapNotNull { it.diziler() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h1.film")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val year        = document.selectFirst("li.release")?.text()?.trim()?.let { Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val description = document.selectFirst("div.description")?.text()?.trim()
        val tags        = document.select("ul.post-categories a").map { it.text().trim() }
        val rating      = document.selectFirst("div.imdb-count")?.text()?.trim()?.split(" ")?.first()?.toRatingInt()
        val actors      = document.select("[href*='oyuncular']").map { Actor(it.text().trim()) }

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
            val episodes = document.select("div.episode-box").mapNotNull {
                val epName    = it.selectFirst("div.episode-name a")?.text()?.trim() ?: return@mapNotNull null
                val epHref    = fixUrlNull(it.selectFirst("div.episode-name a")?.attr("href")) ?: return@mapNotNull null
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