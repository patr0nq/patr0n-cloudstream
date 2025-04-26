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
        "${mainUrl}/netflix"            to "Netflix Dizileri",
        "${mainUrl}/exxen"              to "Exxen Dizileri",
        "${mainUrl}/disney"             to "Disney+ Dizileri",
        "${mainUrl}/tabii-dizileri"     to "Tabii Dizileri",
        "${mainUrl}/blutv"              to "BluTV Dizileri",
        "${mainUrl}/todtv"              to "TodTV Dizileri",
        "${mainUrl}/gain"               to "Gain Dizileri",
        "${mainUrl}/hulu"               to "Hulu Dizileri",
        "${mainUrl}/primevideo"         to "PrimeVideo Dizileri",
        "${mainUrl}/hbomax"             to "HboMax Dizileri",
        "${mainUrl}/paramount"          to "Paramount+ Dizileri",
        "${mainUrl}/unutulmaz"          to "Unutulmaz Diziler",
        "${mainUrl}/category/filmler"   to "Filmler",
        "${mainUrl}/category/diziler"  to "Güncel Diziler"
        "${mainUrl}/category/filmler"   to "Filmler"
    )

    private fun Element.diziler(): SearchResponse? {
        try {
            val title = this.selectFirst(".uk-panel-title.uk-text-truncate, h3, .film-name, .dizi-name, a[title], .uk-h3")
                ?.text()?.substringBefore(" izle")?.trim()
                ?: return null

            val href = fixUrlNull(this.selectFirst(".uk-position-cover, a[href], a")?.attr("href")) ?: return null
            
            val posterUrl = fixUrlNull(this.selectFirst("img[data-src], .uk-overlay img, .film-image img, .dizi-image img")?.attr("data-src"))
            
            val year = this.selectFirst(".release, span.year, div.year")?.text()?.trim()?.let { 
                Regex("(\\d{4})(\\s*[-–]\\s*(\\d{4}))?").find(it)?.groupValues?.get(1)?.toIntOrNull() 
            }

            return if (href.contains("/film/") || href.contains("/movie/") || href.contains("/filmler/")) {
                newMovieSearchResponse(title, href, TvType.Movie) { 
                    this.posterUrl = posterUrl
                    this.year = year
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { 
                    this.posterUrl = posterUrl 
                    this.year = year
                }
            }
        } catch (e: Exception) {
            Log.e("DZF", "Error parsing search item", e)
            return null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to mainUrl
        )

        val url = if (page > 1) {
            "${request.data}/page/${page}"
        } else {
            request.data
        }

        Log.d("DZF", "Loading main page: $url")
        val document = app.get(url, headers=headers).document
        
        // Farklı kapsayıcıları destekle (.uk-width-medium-1-3.uk-width-large-1-6 ve diğer muhtemel sınıflar)
        val homeItems = document.select(".uk-width-medium-1-3.uk-width-large-1-6.uk-margin-bottom, div.uk-panel-box").mapNotNull { it.diziler() }
        
        if (homeItems.isEmpty()) {
            Log.d("DZF", "No content found on page, trying alternate selectors")
            // Fallback selectors - siteye özgü içerik kutularını bul
            val altItems = document.select("article, .uk-panel, div[class*='dizi-box'], div[class*='film-box']").mapNotNull { it.diziler() }
            return newHomePageResponse(request.name, altItems)
        }

        return newHomePageResponse(request.name, homeItems)
    }

    private fun Element.diziler(): SearchResponse? {
        try {
            // Farklı başlık seçicilerini dene
            val title = this.selectFirst(".uk-panel-title.uk-text-truncate, h3, .film-name, .dizi-name, a[title], .uk-h3")
                ?.text()?.substringBefore(" izle")?.trim()
                ?: return null

            // Farklı bağlantı seçicilerini dene
            val href = fixUrlNull(this.selectFirst(".uk-position-cover, a[href], a")?.attr("href")) ?: return null
            
            // Farklı resim seçicilerini dene
            val posterUrl = fixUrlNull(this.selectFirst("img[data-src], .uk-overlay img, .film-image img, .dizi-image img")?.attr("data-src"))
            
            // Yıl bilgisini bulmaya çalış
            val year = this.selectFirst(".release, span.year, div.year")?.text()?.trim()?.let { 
                Regex("(\\d{4})(\\s*[-–]\\s*(\\d{4}))?").find(it)?.groupValues?.get(1)?.toIntOrNull() 
            }

            return if (href.contains("/film/") || href.contains("/movie/") || href.contains("/filmler/")) {
                newMovieSearchResponse(title, href, TvType.Movie) { 
                    this.posterUrl = posterUrl
                    this.year = year
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { 
                    this.posterUrl = posterUrl 
                    this.year = year
                }
            }
        } catch (e: Exception) {
            Log.e("DZF", "Error parsing search item", e)
            return null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to mainUrl
        )

        val document = app.get("${mainUrl}/?s=${query}", headers=headers).document
        
        // Ana arama sonuçlarını dene
        val searchResults = document.select(".uk-width-medium-1-3.uk-width-large-1-6.uk-margin-bottom, div.uk-panel-box, article, div[class*='dizi-box'], div[class*='film-box']").mapNotNull { it.diziler() }
        
        if (searchResults.isEmpty()) {
            Log.d("DZF", "No search results found, trying alternate selectors")
            // Alternatif seçicileri dene
            return document.select("article, .uk-panel, div[class*='item']").mapNotNull { it.diziler() }
        }
        
        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to mainUrl
        )

        try {
            Log.d("DZF", "Loading content: $url")
            val document = app.get(url, headers=headers).document

            // Farklı başlık seçicilerini dene
            val title = document.selectFirst("h1.film, h1.title, h1, .content-title")
                ?.text()?.substringBefore(" izle")?.trim() ?: return null
                
            // Farklı poster seçicilerini dene
            val poster = fixUrlNull(document.selectFirst("[property='og:image'], .film-image img, .dizi-image img, .content-image img")?.attr("content") 
                ?: document.selectFirst(".film-image img, .dizi-image img, .content-image img, img.poster")?.attr("src"))
                
            // Yıl bilgisini farklı seçicilerden bulmaya çalış
            val year = document.selectFirst("li.release, span.year, div.year, [itemprop='dateCreated']")
                ?.text()?.trim()?.let { Regex("(\\d{4})(\\s*[-–]\\s*(\\d{4}))?").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                
            // Farklı seçicilerden açıklama bilgisini al
            val description = document.selectFirst("div.description, [itemprop='description'], .content-desc, .summary")?.text()?.trim()
            
            // Kategori bilgilerini farklı seçicilerden topla
            val tags = document.select("ul.post-categories a, .genres a, span.genres, [itemprop='genre']").map { it.text().trim() }
            
            // IMDb puanını farklı seçicilerden bul
            val rating = document.selectFirst("div.imdb-count, span.imdb, [itemprop='ratingValue']")
                ?.text()?.trim()?.split(" ")?.first()?.toRatingInt()
                
            // Oyuncu bilgilerini farklı seçicilerden topla
            val actors = document.select("[href*='oyuncular'], .actors a, [itemprop='actors'] a").map { Actor(it.text().trim()) }
            
            // Süre bilgisini farklı seçicilerden bul
            val duration = document.selectFirst("li.duration, span.duration, [itemprop='duration']")
                ?.text()?.trim()?.let { Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }

            return if (url.contains("/film/") || url.contains("/movie/") || url.contains("/filmler/")) {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year      = year
                    this.plot      = description
                    this.tags      = tags
                    this.rating    = rating
                    this.duration  = duration
                    addActors(actors)
                }
            } else {
                // Dizi bölümlerini farklı seçicilerden bul
                val episodeElements = document.select("div.episode-box, li.episode, .episodes-list li, .seasons-list li")
                Log.d("DZF", "Found ${episodeElements.size} episode elements")
                
                val episodes = episodeElements.mapNotNull {
                    try {
                        val epName = it.selectFirst("a, span.episode-name")?.text()?.trim() ?: return@mapNotNull null
                        val epHref = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                        
                        // Bölüm numarasını farklı regex'lerle bul
                        val epEpisode = Regex("(\\d+)\\.Bölüm|Bölüm\\s*(\\d+)|B(\\d+)").find(epName)?.let {
                            val matchGroups = it.groupValues.filter { it.isNotEmpty() }
                            if (matchGroups.size > 1) matchGroups[1].toIntOrNull() else null
                        }
                        
                        // Sezon numarasını farklı regex'lerle bul
                        val epSeason = Regex("(\\d+)\\.Sezon|Sezon\\s*(\\d+)|S(\\d+)").find(epName)?.let {
                            val matchGroups = it.groupValues.filter { it.isNotEmpty() }
                            if (matchGroups.size > 1) matchGroups[1].toIntOrNull() else null
                        } ?: 1
                        
                        Log.d("DZF", "Parsed episode: S${epSeason}E${epEpisode} - $epName")

                        newEpisode(epHref) {
                            this.name    = epName
                            this.season  = epSeason
                            this.episode = epEpisode
                        }
                    } catch (e: Exception) {
                        Log.e("DZF", "Error parsing episode: ${it.text()}", e)
                        null
                    }
                }
                
                if (episodes.isEmpty()) {
                    Log.d("DZF", "No episodes found, adding current URL as single episode")
                    // Eğer bölüm bulunamazsa, mevcut URL'yi tek bölüm olarak ekle
                    val singleEpisode = listOf(
                        newEpisode(url) {
                            this.name = title
                            this.season = 1
                            this.episode = 1
                        }
                    )
                    
                    newTvSeriesLoadResponse(title, url, TvType.TvSeries, singleEpisode) {
                        this.posterUrl = poster
                        this.year      = year
                        this.plot      = description
                        this.tags      = tags
                        this.rating    = rating
                        addActors(actors)
                    }
                } else {
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
        } catch (e: Exception) {
            Log.e("DZF", "Error in load", e)
            return null
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZF", "Loading links: $data")
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to data
        )

        try {
            val document = app.get(data, headers=headers).document

            // Farklı iframe seçicilerini dene
            val iframes = document.select("div.player-embed iframe, div.video-player iframe, iframe[src*='player'], iframe[data-src], iframe, div.embed-responsive iframe").mapNotNull { 
                val src = it.attr("src")
                val dataSrc = it.attr("data-src")
                fixUrlNull(if (src.isNotBlank()) src else dataSrc)
            }

            if (iframes.isEmpty()) {
                Log.e("DZF", "No iframes found on page, trying to find direct source links")
                // İframe bulunamazsa, doğrudan kaynak linkleri aramayı dene
                val sources = document.select("source[src], video[src]").mapNotNull { fixUrlNull(it.attr("src")) }
                
                sources.forEach { source ->
                    Log.d("DZF", "Found direct source: $source")
                    callback.invoke(
                        ExtractorLink(
                            name,
                            "Direct",
                            source,
                            data,
                            Qualities.Unknown.value,
                            source.contains(".m3u8")
                        )
                    )
                }
                
                return sources.isNotEmpty()
            }

            var success = false
            iframes.forEach { iframe ->
                Log.d("DZF", "Processing iframe: $iframe")
                try {
                    if (loadExtractor(iframe, data, subtitleCallback, callback)) {
                        Log.d("DZF", "Successfully loaded extractor for: $iframe")
                        success = true
                    } else {
                        Log.d("DZF", "No extractor found for: $iframe, trying to load as direct link")
                        // Extractor bulunamazsa, iframe içeriğini kontrol et
                        try {
                            val iframeContent = app.get(iframe, headers = headers).document
                            val directLinks = iframeContent.select("source[src], video[src]").mapNotNull { fixUrlNull(it.attr("src")) }
                            
                            directLinks.forEach { link ->
                                Log.d("DZF", "Found direct link in iframe: $link")
                                callback.invoke(
                                    ExtractorLink(
                                        name,
                                        "Direct",
                                        link,
                                        iframe,
                                        Qualities.Unknown.value,
                                        link.contains(".m3u8")
                                    )
                                )
                                success = true
                            }
                        } catch (e: Exception) {
                            Log.e("DZF", "Error loading iframe content: $iframe", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DZF", "Error processing iframe: $iframe", e)
                }
            }

            return success
        } catch (e: Exception) {
            Log.e("DZF", "Error in loadLinks", e)
            return false
        }
    }
}