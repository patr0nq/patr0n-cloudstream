version = 1

cloudstream {
    authors     = listOf("yunus60", "GitLatte", "patr0nq")
    language    = "tr"
    description = "Ddizi, dizi izle, dizi seyret, ddizi.im - Güncel ve eski dizileri en iyi görüntü kalitesiyle bulabileceğiniz dizi izleme sitesi."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=ddizi.im&sz=%size%"
}

android {
    buildFeatures {
        buildConfig = true
    }
}