package com.patr0n

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.jsoup.Jsoup // Added for direct HTML parsing if needed, though app.get.document is preferred
import java.net.URLDecoder // For URI decoding
import android.util.Log // Add Log import

class DizifunProvider : MainAPI() { // All providers must be subclasses of MainAPI
    override var mainUrl = "https://dizifun2.com"
    override var name = "Dizifun"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasDownloadSupport = true // Assuming download support, can be verified later
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    companion object {
        // Keys for different sections on the main page
        const val EDITOR_PICKS = "editor-picks"
        const val NEWLY_ADDED_SERIES = "newly-added-series"
        const val NEWLY_ADDED_MOVIES = "newly-added-movies"
        const val FUN_SERIES = "fun-series"
        const val FUN_MOVIES = "fun-movies"
        // TODO: Add keys for platform-specific sections if needed e.g. NETFLIX_SERIES = "netflix-series"

        // Helper function to decode hex string to normal string
        fun hexToString(hex: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < hex.length) {
                val str = hex.substring(i, i + 2)
                sb.append(str.toInt(16).toChar())
                i += 2
            }
            return sb.toString()
        }
    }

    override val mainPage = mainPageOf(
        EDITOR_PICKS to "Editör Seçimleri",
        NEWLY_ADDED_SERIES to "Yeni Eklenen Diziler",
        NEWLY_ADDED_MOVIES to "Yeni Eklenen Filmler",
        FUN_SERIES to "Fun Diziler",
        FUN_MOVIES to "Fun Filmler",
        // Example for a category page that might have pagination
        "/diziler" to "Tüm Diziler",
        "/filmler" to "Tüm Filmler"
    )

    // Helper to ensure URLs are absolute
    private fun String.toAbsoluteUrl(): String {
        return if (this.startsWith("http")) this else if (this.startsWith("//")) "https:${this}" else mainUrl + this
    }

    private fun Element.toSearchResponseEditorPicks(): SearchResponse? {
        val href = this.selectFirst("a.editor_sec-link")?.attr("href")?.toAbsoluteUrl() ?: return null
        val title = this.selectFirst("h3.editor_sec-title")?.text() ?: return null
        val posterUrl = this.selectFirst("img.editor_sec-image")?.attr("src")?.toAbsoluteUrl()
        // val year = this.selectFirst("p.editor_sec-date")?.text()?.toIntOrNull()
        // For simplicity, assuming TvType based on URL or section title later if ambiguous
        // For now, we can try to infer from href. Dizifun uses /dizi/ and /film/ in paths.
        val tvType = if (href.contains("/dizi/")) TvType.TvSeries else if (href.contains("/film/")) TvType.Movie else null

        return if (tvType == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, tvType) {
                this.posterUrl = posterUrl
            }
        } else if (tvType == TvType.Movie) {
            newMovieSearchResponse(title, href, tvType) {
                this.posterUrl = posterUrl
            }
        } else null
    }

    private fun Element.toSearchResponseMoviesRecent(): SearchResponse? {
        val href = this.selectFirst("a.movies_recent-link")?.attr("href")?.toAbsoluteUrl() ?: return null
        val title = this.selectFirst("h3.movies_recent-title")?.text() ?: return null
        val posterUrl = this.selectFirst("img.movies_recent-image")?.attr("src")?.toAbsoluteUrl()
        val tvType = if (href.contains("/dizi/")) TvType.TvSeries else if (href.contains("/film/")) TvType.Movie else null
        
        return if (tvType == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, tvType) {
                this.posterUrl = posterUrl
            }
        } else if (tvType == TvType.Movie) {
            newMovieSearchResponse(title, href, tvType) {
                this.posterUrl = posterUrl
            }
        } else null
    }

    private fun Element.toSearchResponseFeaturedSeriesRecent(): SearchResponse? {
        val href = this.selectFirst("a.featuredseries_recent-link")?.attr("href")?.toAbsoluteUrl() ?: return null
        val title = this.selectFirst("h3.featuredseries_recent-title")?.text() ?: return null
        val posterUrl = this.selectFirst("img.featuredseries_recent-image")?.attr("src")?.toAbsoluteUrl()
        val tvType = if (href.contains("/dizi/")) TvType.TvSeries else if (href.contains("/film/")) TvType.Movie else null

        return if (tvType == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, tvType) {
                this.posterUrl = posterUrl
            }
        } else if (tvType == TvType.Movie) {
            newMovieSearchResponse(title, href, tvType) {
                this.posterUrl = posterUrl
            }
        } else null
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document // Fetch main page HTML

        val homePageList = when (request.data) {
            EDITOR_PICKS -> {
                val items = document.select("div.editor_sec-grid div.editor_sec-item")
                    .mapNotNull { it.toSearchResponseEditorPicks() }
                HomePageList(request.name, items)
            }
            NEWLY_ADDED_SERIES -> {
                // "Yeni Eklenen Diziler" uses movies_recent-grid and movies_recent-item classes
                // This section specifically targets the h4 title to distinguish from other similar structures
                val section = document.selectFirst("h4.title:has(span.uk-icon-video-camera):containsOwn(Yeni Eklenen Diziler)")
                val items = section?.nextElementSibling()?.select("div.movies_recent-item")
                    ?.mapNotNull { it.toSearchResponseMoviesRecent() } ?: emptyList()
                HomePageList(request.name, items.filter { it.type == TvType.TvSeries })
            }
            NEWLY_ADDED_MOVIES -> {
                 // "Yeni Eklenen Filmler" uses movies_recent-grid and movies_recent-item classes
                val section = document.selectFirst("h4.title:has(span.uk-icon-video-camera):containsOwn(Yeni Eklenen Filmler)")
                val items = section?.nextElementSibling()?.select("div.movies_recent-item")
                    ?.mapNotNull { it.toSearchResponseMoviesRecent() } ?: emptyList()
                HomePageList(request.name, items.filter { it.type == TvType.Movie })
            }
            FUN_SERIES -> {
                // "Fun Diziler" uses featuredseries_recent-grid and featuredseries_recent-item
                val section = document.selectFirst("h4.title:has(span.uk-icon-rocket):containsOwn(Fun Diziler)")
                val items = section?.nextElementSibling()?.select("div.featuredseries_recent-item")
                    ?.mapNotNull { it.toSearchResponseFeaturedSeriesRecent() } ?: emptyList()
                HomePageList(request.name, items.filter { it.type == TvType.TvSeries })
            }
            FUN_MOVIES -> {
                 // "Fun Filmler" uses featuredseries_recent-grid and featuredseries_recent-item
                val section = document.selectFirst("h4.title:has(span.uk-icon-rocket):containsOwn(Fun Filmler)")
                val items = section?.nextElementSibling()?.select("div.featuredseries_recent-item")
                    ?.mapNotNull { it.toSearchResponseFeaturedSeriesRecent() } ?: emptyList()
                HomePageList(request.name, items.filter { it.type == TvType.Movie })
            }
            // Handle general /diziler or /filmler pages which might require different parsing or pagination
            // For now, this is a placeholder as the main page HTML doesn't show their structure.
            // These might need a different approach if they are paginated.
            else -> {
                 if (request.data.startsWith("/")) { // For paths like "/diziler", "/filmler"
                    //val paginatedUrl = mainUrl + request.data + if (page > 1) "/sayfa/$page" else "" // Example pagination
                    //val doc = app.get(paginatedUrl).document
                    // TODO: Parse items from these pages. The structure might be different.
                    // For now, returning empty list as we are focused on main page sections.
                     val pathDoc = app.get(mainUrl + request.data + if(page > 1) "?sayfa=$page" else "").document
                     // Assuming they also use .movies_recent-item for listing for now
                     // This is a guess and needs verification by inspecting /diziler and /filmler pages
                     var items = pathDoc.select("div.movies_recent-item")
                                .mapNotNull { it.toSearchResponseMoviesRecent() }
                     if (items.isEmpty()) { // Fallback to editor_sec-item style if no movies_recent-item found
                         items = pathDoc.select("div.editor_sec-item")
                                 .mapNotNull { it.toSearchResponseEditorPicks() }
                     }
                     HomePageList(request.name, items, true) // hasNext might need adjustment based on actual pagination cues
                 } else {
                     null
                 }
            }
        }

        return homePageList?.let { HomePageResponse(listOf(it), hasNext = it.list.isNotEmpty() && request.data.startsWith("/")) }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // The search form action is "https://dizifun2.com/arama" with query parameter "query"
        // <form class="uk-search uk-margin-small-top uk-margin-large-left uk-hidden-small" action="https://dizifun2.com/arama" method="GET">
        // <input class="uk-search-field" type="search" name="query" placeholder="ara bul izle..." autocomplete="off" required>
        val searchUrl = "$mainUrl/arama?query=$query"
        val document = app.get(searchUrl).document
        // Search results might use either .movies_recent-item or .editor_sec-item or a unique structure
        // From dizifunmainPage.html, "Editör Seçimleri" uses .editor_sec-item
        // "Yeni Eklenen Diziler/Filmler" uses .movies_recent-item
        // Let's try to find a common wrapper or specific search result item class
        // Based on the main page structure, the site uses varied class names for listings.
        // We'll try a few common ones.
        var results = document.select("div.movies_recent-item").mapNotNull { it.toSearchResponseMoviesRecent() }
        if (results.isEmpty()) {
            results = document.select("div.editor_sec-item").mapNotNull { it.toSearchResponseEditorPicks() }
        }
        // If there's a more specific class for search results, that would be better.
        // Example: results = document.select("div.search-result-item").mapNotNull { it.toSearchResponseMoviesRecent() }
        return results.ifEmpty { null }
        // throw NotImplementedError("Search is not implemented yet. Need to inspect $searchUrl")
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.text-bold")?.ownText()?.trim() ?: document.title().substringAfter("Dizifun - ").substringBeforeLast(" izle").trim()
        val posterUrl = document.selectFirst("div.media-cover img.responsive-img")?.attr("src")?.toAbsoluteUrl()
        val plot = document.selectFirst("p.text-muted")?.text()?.trim()
        
        var year: Int? = null
        var originalTitle: String? = null
        val subnavItems = document.select("ul.subnav li")
        subnavItems.forEach { item ->
            val text = item.text()
            if (text.contains("Dizi Yılı:")) {
                year = text.substringAfter("Dizi Yılı:").trim().toIntOrNull()
            } else if (text.contains("Orjinal Adı:")) {
                originalTitle = text.substringAfter("Orjinal Adı:").trim()
            }
        }

        val genres = document.selectFirst("div.series-info")?.text()?.substringAfter("Türü:")?.trim()?.split(",")?.map { it.trim() }

        val trailerUrl = document.selectFirst("div.gosterButonu#link1 + div.kutu iframe")?.attr("src")
        // Create a TvSeriesLoadResponse or MovieLoadResponse

        val recommendations = document.select("div.editor_sec-grid div.editor_sec-item").mapNotNull {
            it.toSearchResponseEditorPicks()
        }

        if (url.contains("/dizi/")) { // TV Series
            val episodesBySeason = mutableMapOf<Int, MutableList<Episode>>()
            val seasonButtons = document.select("div#season-menu button.season-btn")
            
            seasonButtons.forEach { seasonButton ->
                val seasonNumText = seasonButton.text().filter { it.isDigit() }
                val seasonNum = seasonNumText.toIntOrNull() ?: return@forEach // if no digit, skip

                val seasonId = seasonButton.attr("id").substringAfter("season-btn-")
                val episodeElements = document.select("div#season-$seasonId div.uk-grid div.uk-width-1-3") // uk-width-1-3 wraps each episode

                val episodes = mutableListOf<Episode>()
                episodeElements.forEach { epElement ->
                    val linkTag = epElement.selectFirst("a")
                    val epHref = linkTag?.attr("href")
                    val epName = linkTag?.selectFirst("div.episode-button")?.textNodes()?.lastOrNull()?.text()?.trim() // Get text after the icon

                    if (epHref != null && epName != null) {
                        // The episode href is relative like "?izle=1&episode=24105"
                        // It should be appended to the current series URL
                        val episodeUrl = url.substringBefore("?") + epHref 
                        episodes.add(Episode(
                            data = episodeUrl,
                            name = epName,
                            season = seasonNum,
                            // episode number can be parsed from epName if it's like "X.Bölüm"
                            episode = epName.filter { it.isDigit() }.toIntOrNull()
                        ))
                    }
                }
                if (episodes.isNotEmpty()) {
                     episodesBySeason.getOrPut(seasonNum) { mutableListOf() }.addAll(episodes)
                }
            }
            // Flatten the map to a list of episodes, ensuring correct season numbers
            val allEpisodes = episodesBySeason.flatMap { entry ->
                entry.value.map { ep ->
                    // Ensure season number from map key if not parsed from name
                    if (ep.season == null) ep.copy(season = entry.key) else ep
                }
            }.sortedWith(compareBy({ it.season }, { it.episode }))


            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, allEpisodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = genres
                this.recommendations = recommendations
                // Add other details like rating, duration, actors if available
                // this.rating = document.selectFirst("span.rating")?.text()?.toRatingInt()
                // this.actors = document.select("div.actors-container div.actor-card").mapNotNull {
                //     val name = it.selectFirst("span.actor-name")?.text()
                //     val image = it.selectFirst("img")?.attr("src")?.toAbsoluteUrl()
                //     ActorData(Actor(name ?: "", image), roleString = "Actor")
                // }
                if (trailerUrl != null) {
                    this.trailers = listOf(TrailerData(trailerUrl, "Trailer", trailerUrl.contains("youtube")))
                }
            }
        } else if (url.contains("/film/")) { // Movie
             // For movies, the loadLinks 'data' is usually the movie page URL itself or a direct player page URL
            // If the movie page itself has the player or leads to it.
            return newMovieLoadResponse(title, url, TvType.Movie, url) { // data = url for loadLinks
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = genres
                this.recommendations = recommendations
                if (trailerUrl != null) {
                    this.trailers = listOf(TrailerData(trailerUrl, "Trailer", trailerUrl.contains("youtube")))
                }
                // Add other details like rating, duration, actors if available
            }
        }
        return null // Should not happen if URL is correctly identified
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document // data is the watch page URL
        var sourcesFound = false

        // Extract the hex encoded URLs from the script within the watch page HTML
        val scriptContent = document.select("script").firstOrNull { 
            it.html().contains("function hexToString(hex)") && it.html().contains("changePlayer(playerType)") 
        }?.html() ?: return false

        val jwPlayerHex = Regex("playerUrl = decodeURIComponent\\(hexToString\\(\"(.*?)\"\\)\\);\\s*else if \\(playerType === \"videojs\"\\)").find(scriptContent)?.groupValues?.get(1)
        val videojsPlayerHex = Regex("else if \\(playerType === \"videojs\"\\) {\\s*playerUrl = decodeURIComponent\\(hexToString\\(\"(.*?)\"\\)\\);")
            .find(scriptContent)?.groupValues?.get(1)
        
        val potentialHexSources = listOfNotNull(jwPlayerHex, videojsPlayerHex)
        val referer = data // Referer should be the watch page URL

        for (hexSource in potentialHexSources) {
            try {
                val decodedUrlPart = hexToString(hexSource)
                val fullIframeUrl = URLDecoder.decode(decodedUrlPart, "UTF-8").toAbsoluteUrl()
                Log.d(name, "Loading extractor for: $fullIframeUrl")
                if (loadExtractor(fullIframeUrl, referer, subtitleCallback, callback)) {
                    sourcesFound = true
                    // Optionally, break if you only want links from the first successful source
                    // break 
                }
            } catch (e: Exception) {
                Log.e(name, "Error processing hex source $hexSource: ${e.message}")
            }
        }
        
        return sourcesFound
    }
} 