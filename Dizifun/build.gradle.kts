version = 2

cloudstream {
    language = "tr"
    description = "Dizifun film ve dizi izleme sitesi için CloudStream eklentisi. Türkçe içerikler, popüler platformlardan (Netflix, Disney+, Exxen, BluTV, vb.) diziler ve filmler."
    authors = listOf("patr0n")
    
    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    
    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "TvSeries",
        "Movie",
    )
    iconUrl = "https://www.google.com/s2/favicons?domain=www.dizifun2.com&sz=%size%"
}