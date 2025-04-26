package com.patr0n

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
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
        "diziler"      to "Diziler",
        "filmler"      to "Filmler",
        "netflix"      to "Netflix",
        "disney"       to "Disney+",
        "primevideo"   to "Prime Video",
        "hbomax"       to "HBO Max",
        "exxen"        to "Exxen",
        "tabii"        to "Tabii",
        "blutv"        to "BluTV",
        "todtv"        to "TodTV",
        "gain"         to "Gain",
        "hulu"         to "Hulu",
        "paramount"    to "Paramount+",
        "unutulmaz"    to "Unutulmaz Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data == "diziler" || request.data == "filmler") {
            "$mainUrl/${request.data}"
        } else {
            "$mainUrl/${request.data}-dizileri"
        }
        
        val document = app.get(url).document
        val home = document.select("div.uk-overlay.uk-overlay-hover").mapNotNull {
            it.toMainPageResult()
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h5.uk-panel-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val year = this.selectFirst("span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama?query=$query").document
        return document.select("div.uk-overlay.uk-overlay-hover").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h5.uk-panel-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val year = this.selectFirst("span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.selectFirst("h1.text-bold")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.media-cover img")?.attr("src"))
        val year = document.selectFirst("ul.subnav li")?.text()?.substringAfter("Dizi Yılı: ")?.toIntOrNull()
        val description = document.selectFirst("p.text-muted")?.text()?.trim()
        val type = if (document.selectFirst("div.series-info")?.text()?.contains("Dizi") == true) TvType.TvSeries else TvType.Movie
        val rating = document.selectFirst("span.rating")?.text()?.toRatingInt()
        val duration = document.selectFirst("span.runtime")?.text()?.split(" ")?.firstOrNull()?.toIntOrNull()
        val tags = document.select("div.genres a").map { it.text() }
        val recommendations = document.select("div.related-items div.uk-overlay").mapNotNull { it.toRecommendationResult() }
        val actors = document.select("div.actors-container div.actor-card").mapNotNull {
            Actor(
                it.selectFirst("span.actor-name")?.text() ?: return@mapNotNull null,
                fixUrlNull(it.selectFirst("img")?.attr("src"))
            )
        }
        val trailer = document.selectFirst("iframe")?.attr("src")?.let { 
            if (it.contains("youtube")) fixUrl(it) else null 
        }

        val episodes = if (type == TvType.TvSeries) {
            document.select("div.episode-button").mapNotNull {
                val episode = it.text().substringAfter("Bölüm").trim().toIntOrNull() ?: return@mapNotNull null
                val data = fixUrlNull(it.parent()?.attr("href")) ?: return@mapNotNull null
                newEpisode(data) {
                    this.episode = episode
                    this.name = "Bölüm $episode"
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
                this.recommendations = recommendations
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
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("h5.uk-panel-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a.uk-position-cover")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Dizifun", "Loading links for: $data")
        try {
            val document = app.get(data).document
            val videoUrl = fixUrlNull(document.selectFirst("iframe")?.attr("src")) ?: return false
            
            val quality = when {
                videoUrl.contains("1080p") -> Qualities.P1080.value
                videoUrl.contains("720p") -> Qualities.P720.value
                videoUrl.contains("480p") -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }

            val source = when {
                videoUrl.contains("youtube") -> "YouTube"
                videoUrl.contains("vimeo") -> "Vimeo"
                else -> "Direct"
            }

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = source,
                    url = videoUrl,
                    referer = mainUrl,
                    quality = quality,
                    isM3u8 = videoUrl.contains(".m3u8")
                )
            )

            document.select("track").forEach { track ->
                val subUrl = fixUrlNull(track.attr("src")) ?: return@forEach
                if (subUrl.isNotBlank()) {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            track.attr("label") ?: "Türkçe",
                            subUrl
                        )
                    )
                }
            }

            return true
        } catch (e: Exception) {
            Log.e("Dizifun", "Error loading links: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}
