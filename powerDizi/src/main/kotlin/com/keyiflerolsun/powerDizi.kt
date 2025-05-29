package com.keyiflerolsun

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import com.sinetech.latte.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-yabanci-dizi.m3u"
    override var name                 = "powerboard Dizi「🍿🎥」"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        // Parse episode information from titles
        val episodeRegex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")
        val processedItems = kanallar.items.map { item ->
            val title = item.title.toString()
            val match = episodeRegex.find(title)
            val cleanTitle = if (match != null) {
                val (showName, _, _) = match.destructured
                showName.trim()
            } else {
                title.trim()
            }

            if (match != null) {
                val (_, season, episode) = match.destructured
                item.copy(
                    title = cleanTitle,
                    season = season.toInt(),
                    episode = episode.toInt(),
                    attributes = item.attributes.toMutableMap().apply {
                        if (!containsKey("tvg-country")) { put("tvg-country", "TR/Altyazılı") }
                        if (!containsKey("tvg-language")) { put("tvg-language", "TR;EN") }
                    }
                )
            } else {
                item.copy(
                    title = cleanTitle,
                    attributes = item.attributes.toMutableMap().apply {
                        if (!containsKey("tvg-country")) { put("tvg-country", "TR") }
                        if (!containsKey("tvg-language")) { put("tvg-language", "TR;EN") }
                    }
                )
            }
        }

        // Dizileri alfabetik olarak gruplandır
        val alphabeticGroups = processedItems.groupBy { item ->
            val firstChar = item.title.toString().firstOrNull()?.uppercaseChar() ?: '#'
            when {
                firstChar.isLetter() -> firstChar.toString()
                firstChar.isDigit() -> "0-9"
                else -> "#"
            }
        }.toSortedMap()

        val homePageLists = mutableListOf<HomePageList>()

        // Özel karakterle başlayanları en başa ekle
        alphabeticGroups["#"]?.let { shows ->
            val searchResponses = shows.distinctBy { it.title }.map { kanal ->
                val streamurl = kanal.url.toString()
                val channelname = kanal.title.toString()
                val posterurl = kanal.attributes["tvg-logo"].toString()
                val nation = kanal.attributes["tvg-country"].toString()

                newLiveSearchResponse(
                    channelname,
                    LoadData(streamurl, channelname, posterurl, "#", nation, kanal.season, kanal.episode).toJson(),
                    type = TvType.TvSeries
                ) {
                    this.posterUrl = posterurl
                    this.lang = nation
                }
            }
            if (searchResponses.isNotEmpty()) {
                homePageLists.add(HomePageList("# Özel Karakterle Başlayanlar", searchResponses, isHorizontalImages = true))
            }
        }

        // Sayıyla başlayanları ekle
        alphabeticGroups["0-9"]?.let { shows ->
            val searchResponses = shows.distinctBy { it.title }.map { kanal ->
                val streamurl = kanal.url.toString()
                val channelname = kanal.title.toString()
                val posterurl = kanal.attributes["tvg-logo"].toString()
                val nation = kanal.attributes["tvg-country"].toString()

                newLiveSearchResponse(
                    channelname,
                    LoadData(streamurl, channelname, posterurl, "0-9", nation, kanal.season, kanal.episode).toJson(),
                    type = TvType.TvSeries
                ) {
                    this.posterUrl = posterurl
                    this.lang = nation
                }
            }
            if (searchResponses.isNotEmpty()) {
                homePageLists.add(HomePageList("0-9 rakam olarak başlayan DİZİLER", searchResponses, isHorizontalImages = true))
            }
        }

        // Harfle başlayanları ekle
        alphabeticGroups.forEach { (letter, shows) ->
            if (letter != "#" && letter != "0-9") {
                val searchResponses = shows.distinctBy { it.title }.map { kanal ->
                    val streamurl = kanal.url.toString()
                    val channelname = kanal.title.toString()
                    val posterurl = kanal.attributes["tvg-logo"].toString()
                    val nation = kanal.attributes["tvg-country"].toString()

                    newLiveSearchResponse(
                        channelname,
                        LoadData(streamurl, channelname, posterurl, letter, nation, kanal.season, kanal.episode).toJson(),
                        type = TvType.TvSeries
                    ) {
                        this.posterUrl = posterurl
                        this.lang = nation
                    }
                }
                if (searchResponses.isNotEmpty()) {
                    homePageLists.add(HomePageList("$letter ile başlayanlar DİZİLER", searchResponses, isHorizontalImages = true))
                }
            }
        }

        return newHomePageResponse(
            homePageLists,
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val episodeRegex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")

        return kanallar.items.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { kanal ->
            val streamurl   = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl   = kanal.attributes["tvg-logo"].toString()
            val chGroup     = kanal.attributes["group-title"].toString()
            val nation      = kanal.attributes["tvg-country"].toString()

            newLiveSearchResponse(
                channelname,
                LoadData(streamurl, channelname, posterurl, chGroup, nation, 
                    episodeRegex.find(channelname)?.let { match ->
                        val (_, season, episode) = match.destructured
                        season.toInt() to episode.toInt()
                    }?.first ?: 1,
                    episodeRegex.find(channelname)?.let { match ->
                        val (_, season, episode) = match.destructured
                        season.toInt() to episode.toInt()
                    }?.second ?: 0
                ).toJson(),
                type = TvType.TvSeries
            ) {
                this.posterUrl = posterurl
                this.lang = nation
            }

        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private suspend fun fetchTMDBData(title: String, season: Int, episode: Int): Pair<JSONObject?, JSONObject?> {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.TMDB_SECRET_API.trim('"')
                if (apiKey.isEmpty()) {
                    Log.e("TMDB", "API key is empty")
                    return@withContext Pair(null, null)
                }

                // Dizi adını temizle ve hazırla
                val cleanedTitle = title
                    .replace(Regex("\\([^)]*\\)"), "") // Parantez içindeki metinleri kaldır
                    .trim()

                Log.d("TMDB", "Searching for TV show: $cleanedTitle")
                val encodedTitle = URLEncoder.encode(cleanedTitle, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encodedTitle&language=tr-TR"

                val response = withContext(Dispatchers.IO) {
                    URL(searchUrl).readText()
                }
                val jsonResponse = JSONObject(response)
                val results = jsonResponse.getJSONArray("results")

                Log.d("TMDB", "Search results count: ${results.length()}")

                if (results.length() > 0) {
                    // İlk sonucu al
                    val tvId = results.getJSONObject(0).getInt("id")
                    val foundTitle = results.getJSONObject(0).optString("name", "")
                    Log.d("TMDB", "Found TV show: $foundTitle with ID: $tvId")

                    // Dizi detaylarını getir
                    val seriesUrl = "https://api.themoviedb.org/3/tv/$tvId?api_key=$apiKey&append_to_response=credits,images&language=tr-TR"
                    val seriesResponse = withContext(Dispatchers.IO) {
                        URL(seriesUrl).readText()
                    }
                    val seriesData = JSONObject(seriesResponse)

                    // Bölüm detaylarını getir
                    try {
                        val episodeUrl = "https://api.themoviedb.org/3/tv/$tvId/season/$season/episode/$episode?api_key=$apiKey&append_to_response=credits,images&language=tr-TR"
                        val episodeResponse = withContext(Dispatchers.IO) {
                            URL(episodeUrl).readText()
                        }
                        val episodeData = JSONObject(episodeResponse)

                        return@withContext Pair(seriesData, episodeData)
                    } catch (e: Exception) {
                        Log.e("TMDB", "Error fetching episode data: ${e.message}")
                        // Bölüm bilgisi alınamazsa sadece dizi bilgisini döndür
                        return@withContext Pair(seriesData, null)
                    }
                } else {
                    Log.d("TMDB", "No results found for: $cleanedTitle")
                }
                Pair(null, null)
            } catch (e: Exception) {
                Log.e("TMDB", "Error fetching TMDB data: ${e.message}")
                Pair(null, null)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val watchKey = "watch_${url.hashCode()}"
        val progressKey = "progress_${url.hashCode()}"
        val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
        val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L
        val loadData = fetchDataFromUrlOrJson(url)

        // Dizi adını temizle - hem "Dizi-1.Sezon" hem de "Dizi 1. Sezon" formatlarını destekler
        val cleanTitle = loadData.title.replace(Regex("""[-\s]*\d+\.?\s*Sezon\s*\d+\.?\s*Bölüm.*"""), "").trim()
        val (seriesData, episodeData) = fetchTMDBData(cleanTitle, loadData.season, loadData.episode)

        val plot = buildString {
            // Her zaman önce dizi bilgilerini göster
            if (seriesData != null) {
                append("<b>📺<u> Dizi Bilgileri</u> (Genel)</b><br><br>")

                val overview = seriesData.optString("overview", "")
                val firstAirDate = seriesData.optString("first_air_date", "").split("-").firstOrNull() ?: ""
                val ratingValue = seriesData.optDouble("vote_average", -1.0)
                val rating = if (ratingValue >= 0) String.format("%.1f", ratingValue) else null
                val tagline = seriesData.optString("tagline", "")
                val originalName = seriesData.optString("original_name", "")
                val originalLanguage = seriesData.optString("original_language", "")
                val numberOfSeasons = seriesData.optInt("number_of_seasons", 1)
                val numberOfEpisodes = seriesData.optInt("number_of_episodes", 1)

                val genresArray = seriesData.optJSONArray("genres")
                val genreList = mutableListOf<String>()
                if (genresArray != null) {
                    for (i in 0 until genresArray.length()) {
                        genreList.add(genresArray.optJSONObject(i)?.optString("name") ?: "")
                    }
                }

                if (tagline.isNotEmpty()) append("💭 <b>Dizi Sloganı:</b><br><i>${tagline}</i><br><br>")
                if (overview.isNotEmpty()) append("📝 <b>Konu:</b><br>${overview}<br><br>")
                if (firstAirDate.isNotEmpty()) append("📅 <b>İlk Yayın Tarihi:</b> $firstAirDate<br>")
                if (rating != null) append("⭐ <b>TMDB Puanı:</b> $rating / 10<br>")
                if (originalName.isNotEmpty()) append("📜 <b>Orijinal Ad:</b> $originalName<br>")
                if (originalLanguage.isNotEmpty()) {
                    val langCode = originalLanguage.lowercase()
                    val turkishName = languageMap[langCode] ?: originalLanguage
                    append("🌐 <b>Orijinal Dil:</b> $turkishName<br>")
                }
                if (numberOfSeasons > 0 && numberOfEpisodes > 0) 
                    append("📅 <b>Toplam Sezon:</b> $numberOfSeasons ($numberOfEpisodes bölüm)<br>")

                if (genreList.isNotEmpty()) append("🎭 <b>Dizi Türü:</b> ${genreList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")

                // Dizi oyuncuları fotoğraflarıyla
                val creditsObject = seriesData.optJSONObject("credits")
                if (creditsObject != null) {
                    val castArray = creditsObject.optJSONArray("cast")
                    if (castArray != null && castArray.length() > 0) {
                        val castList = mutableListOf<String>()
                        for (i in 0 until minOf(castArray.length(), 25)) {
                            val actor = castArray.optJSONObject(i)
                            val actorName = actor?.optString("name", "") ?: ""
                            val character = actor?.optString("character", "") ?: ""
                            if (actorName.isNotEmpty()) {
                                castList.add(if (character.isNotEmpty()) "$actorName (${character})" else actorName)
                            }
                        }
                        if (castList.isNotEmpty()) {
                            append("👥 <b>Tüm Oyuncular:</b> ${castList.joinToString(", ")}<br>")
                        }
                    }
                }

            }

            // Bölüm bilgileri
            if (episodeData != null) {
                append("<hr><br>")
                append("<b>🎬<u> Bölüm Bilgileri</u></b><br><br>")

                val episodeTitle = episodeData.optString("name", "")
                val episodeOverview = episodeData.optString("overview", "")
                val episodeAirDate = episodeData.optString("air_date", "").split("-").firstOrNull() ?: ""
                val episodeRating = episodeData.optDouble("vote_average", -1.0)

                if (episodeTitle.isNotEmpty()) append("📽️ <b>Bölüm Adı:</b> ${episodeTitle}<br>")
                if (episodeOverview.isNotEmpty()) append("✍🏻 <b>Bölüm Konusu:</b><br><i>${episodeOverview}</i><br><br>")
                if (episodeAirDate.isNotEmpty()) append("📅 <b>Yayın Tarihi:</b> $episodeAirDate<br>")
                if (episodeRating >= 0) append("⭐ <b>Bölüm Puanı:</b> ${String.format("%.1f", episodeRating)} / 10<br>")

                // Bölüm oyuncuları
                val episodeCredits = episodeData.optJSONObject("credits")
                if (episodeCredits != null) {
                    val episodeCast = episodeCredits.optJSONArray("cast")
                    if (episodeCast != null && episodeCast.length() > 0) {
                        append("<br>👥 <b>Bu Bölümdeki Oyuncular:</b><br>")
                        append("<div style='display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:5px 0'>")
                        for (i in 0 until minOf(episodeCast.length(), 25)) {
                            val actor = episodeCast.optJSONObject(i)
                            val actorName = actor?.optString("name", "") ?: ""
                            val character = actor?.optString("character", "") ?: ""
                            val gender = actor?.optInt("gender", 0) ?: 0

                            if (actorName.isNotEmpty()) {
                                val genderIcon = when (gender) {
                                    1 -> "👱🏼‍♀" // Kadın
                                    2 -> "👱🏻" // Erkek
                                    else -> "👤" // Belirsiz
                                }
                                append("<div style='background:#f0f0f0;padding:5px 10px;border-radius:5px'>")
                                append("$genderIcon <b>$actorName</b>")
                                if (character.isNotEmpty()) append(" ($character rolünde)")
                                append("</div>")
                            }
                        }
                        append("</div><br>")
                    }
                }

            }

            // Eğer hiçbir TMDB verisi yoksa, en azından temel bilgileri göster
            if (seriesData == null && episodeData == null) {
                append("<b>📺 DİZİ BİLGİLERİ</b><br><br>")
                append("📝 <b>TMDB'den bilgi alınamadı.</b><br><br>")
            }

            val nation = if (listOf("adult", "erotic", "erotik", "porn", "porno").any { loadData.group.contains(it, ignoreCase = true) }) {
                "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
            } else {
                "» ${loadData.group} | ${loadData.nation} «"
            }
            append(nation)
        }

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val episodeRegex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")

        // Önce tüm dizileri grupla
        val allShows = kanallar.items.groupBy { item ->
            val title = item.title.toString()
            val match = episodeRegex.find(title)
            if (match != null) {
                val (showName, _, _) = match.destructured
                showName.trim()
            } else {
                title.trim()
            }
        }

        // Mevcut diziyi bul ve bölümlerini topla
        val currentShowTitle = loadData.title.replace(Regex("""\s*\d+\.\s*Sezon\s*\d+\.\s*Bölüm.*"""), "").trim()
        val currentShowEpisodes = allShows[currentShowTitle]?.mapNotNull { kanal ->
            val title = kanal.title.toString()
            val match = episodeRegex.find(title)
            if (match != null) {
                val (_, season, episode) = match.destructured
                Episode(
                    episode = episode.toInt(),
                    season = season.toInt(),
                    name = title,  // Bölüm başlığını ekle
                    data = LoadData(
                        kanal.url.toString(),
                        title,
                        kanal.attributes["tvg-logo"].toString(),
                        kanal.attributes["group-title"].toString(),
                        kanal.attributes["tvg-country"]?.toString() ?: "TR",
                        season.toInt(),
                        