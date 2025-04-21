package com.nikyokki

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class Tlc : MainAPI() {
    override var mainUrl              = "https://www.tlctv.com.tr"
    override var name                 = "Tlc"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/kesfet/"                        to "Öne Çıkarılanlar",
        "${mainUrl}/kesfet/a-z/"                    to "A-Z Programlar",
        "${mainUrl}/kesfet/sira-disi-hayatlar/"     to "Sıra Dışı Hayatlar",
        "${mainUrl}/kesfet/ask-ve-iliski/"          to "Aşk ve İlişki",
        "${mainUrl}/kesfet/yemek/"                  to "Yemek",
        "${mainUrl}/kesfet/moda-ve-guzellik/"       to "Moda ve Güzellik",
        "${mainUrl}/kesfet/ev-ve-dekorasyon/"       to "Ev ve Dekorasyon"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}").document
        // Kategori sayfasındaki içerikleri doğru şekilde seçelim
        val home = document.select("div.poster").mapNotNull { 
            try {
                // Her poster için içindeki a etiketini bulalım
                val anchor = it.selectFirst("a") ?: return@mapNotNull null
                
                // href özelliğini doğrudan alıyoruz ve tam URL'ye çeviriyoruz
                val href = fixUrlNull(anchor.attr("href"))
                if (href == null || !href.startsWith(mainUrl)) {
                    return@mapNotNull null
                }
                
                // Resim için img etiketini seçiyoruz
                val img = anchor.selectFirst("img") ?: return@mapNotNull null
                val posterUrl = fixUrlNull(img.attr("src"))
                
                // Başlık için alt özelliğini veya href'ten çıkarıyoruz
                val title = img.attr("alt").takeIf { it.isNotBlank() } 
                    ?: href.substringAfterLast("/").replace("-", " ").split(" ").joinToString(" ") { 
                        it.capitalize() 
                    }
                
                Log.d("TLC", "Found item in ${request.name}: title=$title, href=$href")
                
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { 
                    this.posterUrl = posterUrl 
                }
            } catch (e: Exception) {
                Log.e("TLC", "Error parsing item: ${e.message}")
                null
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun load(url: String): LoadResponse? {
        Log.d("TLC", "Loading URL: $url")
        val document = app.get(url).document

        val title = document.selectFirst("h1.show-title, h1.program-title, div.program-header h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.show-img img, div.program-image img, div.program-header img")?.attr("src"))
        val description = document.selectFirst("div.show-description, div.program-description, div.program-header p")?.text()?.trim()
        
        Log.d("TLC", "Found program: title=$title, poster=$poster")
        
        // Bölümleri topla - daha geniş seçiciler kullanarak
        val episodes = document.select("div.episode-card, div.episode-item, div.video-card, div.episode").mapNotNull {
            try {
                val epTitle = it.selectFirst("h3.episode-title, div.episode-info h3, h3.video-title, div.episode-title")?.text() ?: return@mapNotNull null
                
                // Link için daha geniş seçiciler kullanıyoruz
                val epLink = it.selectFirst("a[href]")
                val epHref = fixUrlNull(epLink?.attr("href")) ?: return@mapNotNull null
                
                // Tam URL kontrolü
                val fullEpHref = if (epHref.startsWith("http")) epHref else "$mainUrl$epHref"
                
                val epThumb = fixUrlNull(it.selectFirst("img")?.attr("src"))
                val epDesc = it.selectFirst("p.episode-desc, p.episode-description, div.episode-description")?.text()
                
                Log.d("TLC", "Found episode: title=$epTitle, href=$fullEpHref")
                
                newEpisode(fullEpHref) {
                    this.name = epTitle
                    this.posterUrl = epThumb
                    this.description = epDesc
                }
            } catch (e: Exception) {
                Log.e("TLC", "Error parsing episode: ${e.message}")
                null
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama?q=${query}").document

        return document.select("div.card-container").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.card-title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a.card-link")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img.card-img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { 
            this.posterUrl = posterUrl 
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.show-title, h1.program-title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.show-img img, div.program-image img")?.attr("src"))
        val description = document.selectFirst("div.show-description, div.program-description")?.text()?.trim()
        
        // Bölümleri topla
        val episodes = document.select("div.episode-card, div.episode-item, div.video-card").mapNotNull {
            val epTitle = it.selectFirst("h3.episode-title, div.episode-info h3, h3.video-title")?.text() ?: return@mapNotNull null
            val epHref = fixUrlNull(it.selectFirst("a.episode-link, a")?.attr("href")) ?: return@mapNotNull null
            val epThumb = fixUrlNull(it.selectFirst("img.episode-img, img")?.attr("src"))
            val epDesc = it.selectFirst("p.episode-desc, p.episode-description")?.text()
            
            newEpisode(epHref) {
                this.name = epTitle
                this.posterUrl = epThumb
                this.description = epDesc
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("TLC", "data » ${data}")
        val document = app.get(data).document

        // Video kaynağını bul
        val videoUrl = document.select("video source, source").firstOrNull()?.attr("src")
            ?: document.select("iframe").firstOrNull()?.attr("src")
            ?: document.select("div.video-player video").firstOrNull()?.attr("src")
            ?: document.select("div.video-container video").firstOrNull()?.attr("src")
            ?: document.select("div.player-container video").firstOrNull()?.attr("src")
        
        Log.d("TLC", "videoUrl » ${videoUrl}")
        
        if (videoUrl != null) {
            if (videoUrl.contains("youtube") || videoUrl.contains("vimeo")) {
                loadExtractor(videoUrl, mainUrl, subtitleCallback, callback)
            } else {
                val type = if (videoUrl.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = videoUrl,
                        type = type
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        return true
    }
}
