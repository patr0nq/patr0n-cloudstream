package com.patr0n

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DizifunPlugin : Plugin() {
    override fun load(context: Context) {
        // Plugin info
        registerMainAPI(Dizifun())
    }
    
    override fun getVersion(): Int {
        return 2
    }
    
    override fun getPluginName(): String {
        return "Dizifun"
    }
    
    override fun getPluginDescription(): String {
        return "Dizifun film ve dizi izleme sitesi için CloudStream eklentisi. Türkçe içerikler, popüler platformlardan (Netflix, Disney+, Exxen, BluTV, vb.) diziler ve filmler."
    }
}