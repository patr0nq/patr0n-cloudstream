// Farklı bir youtube end-point kullanarak youtube canlı tv yayınlarını çekmek için kullandığım eklenti
// Kullanımı:
// Canlı yayın veren kanalların video ID'lerini "channels" listesine ekleyiyoruz.
// Örneğin: Channel("video-id-buraya", "Kanal Adı", "Kategori")
// Kanal ID'lerini bulmak için:  URL_ADDRESS.youtube.com/@KanalAdi şeklinde girip oradaki canlı yayın video ID'sini bulup "channels" listesine eklememiz yeterl

package com.sinetech.latte

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri

class YTCanliTV : MainAPI() {
    override var mainUrl = "https://iv.ggtyler.dev"
    override var name = "Youtube Canlı TV「▶️」"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    private val channels = listOf(
        // 🎭 Eğlence Kanalları
        Channel("twOviIHfDQk", "Güldür Güldür (7/24)", "🎭 Eğlence"),
        Channel("iSs2JX68DZw", "Çok Güzel Hareketler Bunlar (7/24)", "🎭 Eğlence"),
        Channel("hfx8H7YrmTw", "Gülşah Film - Kemal Sunal Filmleri (7/24)", "🎭 Eğlence"),
        Channel("UgKxh04Iv9o", "Arzu Film - Yeşilçam Canlı Yayını (7/24)", "🎭 Eğlence"),
        Channel("sF1AgroEr60", "Adanalı - (7/24)", "🎭 Eğlence"),
        Channel("FGL1GwoRRIw", "Film Atölyesi - Kemal Sunal Canlı Yayını 2 (7/24)", "🎭 Eğlence"),
        Channel("BhUr9G4e2s4", "Kurtlar Vadisi - KV Canlı Yayını (7/24)", "🎭 Eğlence"),
        Channel("XbTmCxei9R8", "Kurtlar Vadisi Pusu -  KVP Canlı Yayını (7/24)", "🎭 Eğlence"),

        // 📰 Haber Kanalları
        Channel("ztmY_cCtUl0", "Sözcü TV", "📰 Haber"),
        Channel("VXMR3YQ7W3s", "CNN Türk", "📰 Haber"),
        Channel("RNVNlJSUFoE", "HaberTürk", "📰 Haber"),
        Channel("VhukseiO-Qo", "TRT Haber", "📰 Haber"),
        Channel("NWyXUXzN7So", "TRT World (İngilizce)", "📰 Haber"),
        Channel("6BX-NUzBSp8", "Haber Global", "📰 Haber"),
        Channel("qnpfhjMhMKY", "NTV", "📰 Haber"),
        Channel("ZIPYDataQZI", "24 TV", "📰 Haber"),
        Channel("hHSmBJk6w0c", "Bloomberg HT", "📰 Haber"),
        Channel("ZSWPj9szKb8", "Halk TV", "📰 Haber"),
        Channel("fNqmmqNNGp8", "Tele1 TV", "📰 Haber"),
        Channel("rhi2aUOfKxk", "EkoTürk", "📰 Haber"),
        Channel("NtWcZDf5DZg", "Ülke TV", "📰 Haber"),
        Channel("6DEEsrkWz1c", "TVNET", "📰 Haber"),
        Channel("pE6RnH-0rz0", "BengüTürk", "📰 Haber"),
        Channel("nmY9i63t6qo", "A Haber", "📰 Haber"),
        Channel("TsB0xYOH0AU", "TGRT Haber TV", "📰 Haber"),
        Channel("j_GTLOCquIY", "Lider Haber", "📰 Haber"),
        Channel("HINEFiFt8TY", "Ekol TV", "📰 Haber"),

        // 📺 Ulusal Kanalları
        Channel("6wHAK439FDI", "Kanal D", "📺 Ulusal"),
        Channel("ZGCj2vbQaBU", "ATV", "📺 Ulusal"),
        Channel("ouuCjEjyKVI", "Show TV", "📺 Ulusal"),
        Channel("Y3vGHFrsqrs", "Show TV (MAX)", "📺 Ulusal"),
        Channel("XnvS-RZa4Qw", "Show TV (ShowTürk)", "📺 Ulusal"),
        Channel("82O6yOy_XwE", "Star TV", "📺 Ulusal"),
        Channel("6g_DvD8e2T0", "TV100", "📺 Ulusal"),
        Channel("t1TaNys7xd8", "DMax Türkiye", "📺 Ulusal"),
        Channel("XihyuKSyUD0", "CBNC-E", "📺 Ulusal"),

        // 📹 Belgesel Kanalları
        Channel("JqhCxiee9Z4", "Discovery (Yabancı)", "📹 Belgesel"),
        Channel("915BfFJ9RGY", "Love Nature (1) (Yabancı)", "📹 Belgesel"),
        Channel("daqB3i9WYIY", "Love Nature (2) (Yabancı)", "📹 Belgesel"),

        // 🌱 Yaşam/Günlük Kanalları
        Channel("whHyoC4Wwn4", "Bizim EV Tv", "🌱 Yaşam/Günlük"),
        Channel("aI65zlH3UT8", "Vav TV", "🌱 Yaşam/Günlük"),

        // 🏠 Yerel Kanalları
        Channel("mM-Q9h0B5vk", "On6", "🏠 Yerel"),

        // 🎈 Çocuk Kanalları
        Channel("5Whk9MVTpI4", "Cartoon Network Türkiye - Kral Şakir", "🎈 Çocuk"),
        Channel("ANLW4Ce_OQI", "Cartoon Network Türkiye - Adventure Time", "🎈 Çocuk"),
        Channel("TTYC2dgCMQQ", "Cartoon Network Türkiye - Teen Titans GO", "🎈 Çocuk"),

        // 🏃🏻‍♂️⛹🏾‍♀️🏌🏾‍♂️🚴🏻🏇 Spor Kanalları
        Channel("7uBpwcn2ZZ0", "A Spor", "🏃🏻‍♂️⛹🏾‍♀️🏌🏾‍♂️🚴🏻🏇 Spor"),
        Channel("RdpqsTbi_KU", "HT Spor", "🏃🏻‍♂️⛹🏾‍♀️🏌🏾‍♂️🚴🏻🏇 Spor"),
        Channel("VCl1wO81VdM", "beIN SPORTS HABER", "🏃🏻‍♂️⛹🏾‍♀️🏌🏾‍♂️🚴🏻🏇 Spor"),
        Channel("qfyam6JjSDE", "Radyo GOL (radyo)", "🏃🏻‍♂️⛹🏾‍♀️🏌🏾‍♂️🚴🏻🏇 Spor"),
        Channel("LBIl-do5Zp8", "TRT Spor", "🏃🏻‍♂️⛹🏾‍♀️🏌🏾‍♂️🚴🏻🏇 Spor"),
        Channel("g89RQMJtK6E", "TJK TV ", "🏃🏻‍♂️⛹🏾‍♀️🏌🏾‍♂️🚴🏻🏇 Spor"),
        Channel("ri4rR0H2i9c", "TJK TV YurtDışı", "🏃🏻‍♂️⛹🏾‍♀️🏌🏾‍♂️🚴🏻🏇 Spor"),

        // ⚽ 7/24 Spor Kanalları
        Channel("5AG96lUA9f8", "BeIN Sports Arşiv (Derbiler)", "⚽ 7/24 Spor"),

        // 🎶 Müzik Kanalları
        Channel("GuFTuKoXepw", "Kral Pop TV", "🎶 Müzik"),
        Channel("A49bKX8gb-8", "Kral FM (Radyo)", "🎶 Müzik"),
        Channel("5J-w9AHKHsc", "Kral Pop (Radyo))", "🎶 Müzik"),
        Channel("Bto75pRPQNA", "Ulus Müzik - Canlı Radyo (manzaralı)", "🎶 Müzik"),
        Channel("tWTHF0r2oEw", "SlowTürk - Canlı Radyo", "🎶 Müzik"),

        // 🎸 Yabancı Müzik Kanalları
        Channel("36YnV9STBqc", "The Good Life Radio x Sensual Musique", "🎸 Yabancı Müzik"),
        Channel("lCjVa1c5zKw", "Nuclear Blast Records - METAL 24/7", "🎸 Yabancı Müzik"),

        // 🍃⛅️🏞️ 7/24 Manzara Kanalları
        Channel("KxgnK2W2-Ko", "Yaban Hayatı", "🍃⛅️🏞️ 7/24 Manzara"),
        Channel("AUyURx-Mtmg", "Okyanus Görüntüleri", "🍃⛅️🏞️ 7/24 Manzara"),
        Channel("ZU922s4Zc74", "Doğa", "🍃⛅️🏞️ 7/24 Manzara"),
        Channel("mbktLGGDXw4", "Şömine", "🍃⛅️🏞️ 7/24 Manzara")

        // Genel olarak herkesin isteyebileceği kanallar sırayla eklenebilir.
        // Örnek: Channel("Kanal ID", "Kanal Adı", "Kanal Kategorisi"),
        // Listenin sonundaki kanaldan sonra virgül kullanmıyoruz sadece kodlarda sorun olmaması açısından.
        // Kategoriler: 📰 Haber, ⚽ Spor, 📺 Ulusal, 🎭 Eğlence, 🎶 Müzik, vb çeşitlendirebiliriz.
    )

    private data class Channel(val videoId: String, val name: String, val category: String)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allLiveStreams = channels.map { channel ->
            newMovieSearchResponse(
                channel.name,
                "$mainUrl/watch?v=${channel.videoId}",
                TvType.Live
            ) {
                this.posterUrl = "$mainUrl/vi/${channel.videoId}/maxres.jpg"
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }

        val categorizedContent = mutableListOf<HomePageList>()

        // Tüm kanalları içeren ana kategori
        categorizedContent.add(HomePageList(
            "Tüm Canlı Yayınlar",
            allLiveStreams.sortedBy { it.name },
            true
        ))

        // Kanalları kategorilerine göre grupla
        val groupedStreams = allLiveStreams.groupBy { stream ->
            channels.find { it.name == stream.name }?.category ?: "Diğer Kanallar"
        }

        // Her kategori için ayrı bir liste oluştur ve alfabetik sırala
        groupedStreams.forEach { (category, streams) ->
            categorizedContent.add(HomePageList(
                "${category} Kanalları",
                streams.sortedBy { it.name },
                true
            ))
        }

        return newHomePageResponse(categorizedContent, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = tryParseJson<List<SearchEntry>>(
            app.get("$mainUrl/api/v1/search?q=${query.encodeUri()}&page=1&type=video&fields=videoId,title").text
        )
        return res?.map { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val videoId = Regex("watch\\?v=([a-zA-Z0-9_-]+)").find(url)?.groups?.get(1)?.value
        val res = tryParseJson<VideoEntry>(
            app.get("$mainUrl/api/v1/videos/$videoId?fields=videoId,title,description,author,authorThumbnails").text
        )
        
        return if (res != null) {
            // Tüm kanalların listesini oluştur
            val recommendations = mutableListOf<SearchResponse>()
            for (channel in channels) {
                try {
                    val streamsDocument = app.get("$mainUrl/channel/${channel.videoId}/streams?sort_by=popular").document
                    val stream = streamsDocument.selectFirst(".pure-u-1.pure-u-md-1-4")?.let { streamElement ->
                        val streamUrl = streamElement.selectFirst(".thumbnail a")?.attr("href") ?: return@let null
                        val recVideoId = streamUrl.substringAfter("watch?v=")
                        if (recVideoId == videoId) return@let null // Mevcut videoyu önerilerden çıkar
                        val thumbnailUrl = streamElement.selectFirst(".thumbnail img")?.attr("src")
                        
                        newMovieSearchResponse(
                            channel.name,
                            "$mainUrl/watch?v=$recVideoId",
                            TvType.Live
                        ) {
                            this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else "$mainUrl/vi/$recVideoId/maxres.jpg"
                            this.posterHeaders = mapOf()
                            this.quality = SearchQuality.HD
                        }
                    }
                    if (stream != null) {
                        recommendations.add(stream)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("YTCanliTV", "Error loading recommendations for ${channel.name}: ${e.message}")
                }
            }

            newMovieLoadResponse(
                res.title,
                url,
                TvType.Live,
                videoId
            ) {
                this.plot = res.description
                this.posterUrl = "$mainUrl/vi/$videoId/maxres.jpg"
                this.actors = listOf(
                    ActorData(
                        Actor(res.author, res.authorThumbnails.lastOrNull()?.url ?: ""),
                        roleString = "Kanal Sahibi"
                    )
                )
                this.recommendations = recommendations
            }
        } else null
    }


    private data class SearchEntry(
        val title: String,
        val videoId: String,
        val lengthSeconds: Int,
        val viewCount: Int,
        val publishedText: String,
        val author: String,
        val authorId: String,
        val videoThumbnails: List<Thumbnail>
    ) {
        fun toSearchResponse(provider: YTCanliTV): SearchResponse {
            android.util.Log.d("YTCanliTV", "Video dönüştürülüyor - başlık: $title, id: $videoId")
            return provider.newMovieSearchResponse(
                title,
                "${provider.mainUrl}/watch?v=$videoId",
                TvType.Live
            ) {
                this.posterUrl = videoThumbnails.firstOrNull()?.let { "${provider.mainUrl}${it.url}" } ?: "${provider.mainUrl}/vi/$videoId/maxres.jpg"
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }
    }

    private data class VideoEntry(
        val title: String,
        val description: String,
        val videoId: String,
        val author: String,
        val authorThumbnails: List<Thumbnail>
    )


    private data class Thumbnail(
        val url: String
    )



    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val videoId = data
            val videoDocument = app.get("$mainUrl/watch?v=$videoId").document
            val hlsUrl = videoDocument.select("source[type='application/x-mpegURL']").firstOrNull()?.attr("src")
            if (!hlsUrl.isNullOrEmpty()) {
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "${this.name} (HLS)",
                        url = "$mainUrl$hlsUrl",
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        headers = mapOf(),
                        extractorData = null,
                        type = ExtractorLinkType.M3U8
                    )
                )
                return true
            }
            val videoInfo = app.get("$mainUrl/api/v1/videos/$videoId").text
            val videoData = tryParseJson<VideoEntry>(videoInfo)
            if (videoData != null) {
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "${this.name} (DASH)",
                        url = "$mainUrl/api/manifest/dash/id/$videoId",
                        referer = mainUrl,
                        quality = Qualities.P1080.value,
                        headers = mapOf(),
                        extractorData = null,
                        type = ExtractorLinkType.DASH
                    )
                )
                return true
            }
            return false
        } catch (e: Exception) {
            android.util.Log.e("YTCanliTV", "loadLinks error: ${e.message}", e)
            return false
        }
    }
}
