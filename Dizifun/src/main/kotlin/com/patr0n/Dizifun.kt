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
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.select("h5.uk-panel-title").text() ?: return null
        val href = this.select("a.uk-position-cover").attr("href") ?: return null
        val posterUrl = this.select("img").attr("src")
        val year = this.select("span.uk-display-block.uk-text-muted").text().trim().toIntOrNull()
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
        val title = this.select("h5.uk-panel-title").text() ?: return null
        val href = this.select("a.uk-position-cover").attr("href") ?: return null
        val posterUrl = this.select("img").attr("src")
        val year = this.select("span.uk-display-block.uk-text-muted").text().trim().toIntOrNull()
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.select("h1.text-bold").text() ?: return null
        val poster = document.select("div.media-cover img").attr("src")
        val year = document.select("ul.subnav li").text().substringAfter("Dizi Yılı: ").toIntOrNull()
        val description = document.select("p.text-muted").text()
        val type = if (document.select("div.series-info").text().contains("Dizi")) TvType.TvSeries else TvType.Movie
        val rating = document.select("span.rating").text().toRatingInt()
        val duration = document.select("span.runtime").text().split(" ").first().toIntOrNull()
        val tags = document.select("div.genres a").map { it.text() }
        val recommendations = document.select("div.related-items div.uk-overlay").mapNotNull { it.toRecommendationResult() }
        val actors = document.select("div.actors-container div.actor-card").mapNotNull {
            try {
                Actor(
                    name = it.select("span.actor-name").text(),
                    image = it.select("img").attr("src")
                )
            } catch (e: Exception) {
                null
            }
        }
        val trailer = document.select("iframe").attr("src")?.let { 
            if (it.contains("youtube")) it else null 
        }

        val episodes = if (type == TvType.TvSeries) {
            document.select("div.episode-button").mapNotNull {
                try {
                    val episode = it.text().substringAfter("Bölüm").toIntOrNull() ?: return@mapNotNull null
                    Episode(
                        data = it.parent().attr("href"),
                        episode = episode,
                        name = "Bölüm $episode"
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } else null

        return newMovieLoadResponse(title, url, type, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.rating = rating
            this.duration = duration
            this.tags = tags
            this.recommendations = recommendations
            this.episodes = episodes
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.select("h5.uk-panel-title").text() ?: return null
        val href = this.select("a.uk-position-cover").attr("href") ?: return null
        val posterUrl = this.select("img").attr("src")
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
            val videoUrl = document.select("iframe").attr("src")
            
            if (videoUrl.isNotBlank()) {
                // Video kalitesini belirle
                val quality = when {
                    videoUrl.contains("1080p") -> Qualities.FullHd.value
                    videoUrl.contains("720p") -> Qualities.HD.value
                    videoUrl.contains("480p") -> Qualities.SD.value
                    else -> Qualities.Unknown.value
                }

                // Video kaynağını belirle
                val source = when {
                    videoUrl.contains("youtube") -> "YouTube"
                    videoUrl.contains("vimeo") -> "Vimeo"
                    else -> "Direct"
                }

                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = source,
                        url = videoUrl,
                        referer = mainUrl,
                        quality = quality,
                        isM3u8 = videoUrl.contains(".m3u8"),
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
                        )
                    )
                )

                // Altyazı varsa ekle
                document.select("track").forEach { track ->
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
