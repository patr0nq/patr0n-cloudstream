version = 0

cloudstream {
    authors     = listOf("nikypatr0nokki")
    language    = "tr"
    description = "Tlc programlarını buradan izleyebilirsiniz."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.tlctv.com.tr/&sz=%size%"
}