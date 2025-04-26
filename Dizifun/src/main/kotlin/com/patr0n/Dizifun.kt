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
        "Diziler" to "diziler",
        "Filmler" to "filmler",
        "Netflix" to "netflix",
        "Disney+" to "disney",
        "Prime Video" to "primevideo",
        "HBO Max" to "hbomax",
        "Exxen" to "exxen",
        "Tabii" to "tabii-dizileri",
        "BluTV" to "blutv",
        "TodTV" to "todtv",
        "Gain" to "gain",
        "Hulu" to "hulu",
        "Paramount+" to "paramount",
        "Unutulmaz Diziler" to "unutulmaz"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.name == "Diziler" || request.name == "Filmler") {
            "$mainUrl/${request.data}"
        } else {
            "$mainUrl/${request.data}-dizileri"
        }
        
        val document = app.get(url).document
        val home = document.select("div.uk-overlay.uk-overlay-hover").mapNotNull {
            it.toMainPageResult()
        }
        
        return HomePageResponse(
            listOf(HomePageList(request.name, home))
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.select("h5.uk-panel-title")?.text() ?: return null
        val href = this.select("a.uk-position-cover")?.attr("href") ?: return null
        val posterUrl = this.select("img")?.attr("src")
        val year = this.select("span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
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
        val title = this.select("h5.uk-panel-title")?.text() ?: return null
        val href = this.select("a.uk-position-cover")?.attr("href") ?: return null
        val posterUrl = this.select("img")?.attr("src")
        val year = this.select("span.uk-display-block.uk-text-muted")?.text()?.trim()?.toIntOrNull()
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.select("h1.text-bold")?.text() ?: return null
        val poster = document.select("div.media-cover img")?.attr("src")
        val year = document.select("ul.subnav li")?.text()?.substringAfter("Dizi Yılı: ")?.toIntOrNull()
        val description = document.select("p.text-muted")?.text()
        val type = if (document.select("div.series-info")?.text()?.contains("Dizi") == true) TvType.TvSeries else TvType.Movie
        val rating = document.select("span.rating")?.text()?.toRatingInt()
        val duration = document.select("span.runtime")?.text()?.split(" ")?.firstOrNull()?.toIntOrNull()
        val tags = document.select("div.genres a")?.map { it.text() }
        val recommendations = document.select("div.related-items div.uk-overlay")?.mapNotNull { it.toRecommendationResult() }
        val actors = document.select("div.actors-container div.actor-card")?.mapNotNull {
            Actor(
                it.select("span.actor-name")?.text() ?: return@mapNotNull null,
                it.select("img")?.attr("src")
            )
        }
        val trailer = document.select("iframe")?.attr("src")?.let { 
            if (it.contains("youtube")) it else null 
        }

        val episodes = if (type == TvType.TvSeries) {
            document.select("div.episode-button").mapNotNull {
                val episode = it.text().substringAfter("Bölüm").toIntOrNull() ?: return@mapNotNull null
                val data = it.parent()?.attr("href") ?: return@mapNotNull null
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
                addActors(actors ?: emptyList())
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
                addActors(actors ?: emptyList())
                addTrailer(trailer)
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.select("h5.uk-panel-title")?.text() ?: return null
        val href = this.select("a.uk-position-cover")?.attr("href") ?: return null
        val posterUrl = this.select("img")?.attr("src")
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
            val videoUrl = document.select("iframe")?.attr("src")
            
            if (!videoUrl.isNullOrBlank()) {
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

                document.select("track")?.forEach { track ->
                    val subUrl = track.attr("src")
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
            }
        } catch (e: Exception) {
            Log.e("Dizifun", "Error loading links: ${e.message}")
            e.printStackTrace()
        }
        return false
    }
}
