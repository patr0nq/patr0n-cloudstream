package com.nikyokki

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.patr0n.DizilabProvider

@CloudstreamPlugin
class DizilabProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DizilabProvider())
    }
}