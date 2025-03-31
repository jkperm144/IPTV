package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName

open class Gcam : ExtractorApi() {
    override val name = "Gcam"
    override val mainUrl = "https://gdrive.cam"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer).text
        val kaken = "kaken\\s*=\\s*\"(.*)\"".toRegex().find(response)?.groupValues?.get(1)
        val json = app.get("https://cdn1.gdrive.cam/api/?${kaken ?: return}=&_=${APIHolder.unixTimeMS}").parsedSafe<Response>()

        json?.sources?.map {
            val quality = getQualityFromName(it.label)
            callback.invoke(
                ExtractorLink(
                    name,
                    "$name ${if(quality != Qualities.Unknown.value) "" else quality}",
                    it.file?: return@map,
                    "",
                    getQualityFromName(it.label)
                )
            )
        }

        json?.tracks?.map {
            subtitleCallback.invoke(
                SubtitleFile(
                    it.label ?: return@map,
                    it.file ?: return@map
                )
            )
        }

    }

    data class Tracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class Sources(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class Response(
        @JsonProperty("sources") val sources: ArrayList<Sources>? = arrayListOf(),
        @JsonProperty("tracks") val tracks: ArrayList<Tracks>? = arrayListOf(),
    )

}

class Vanfem : XStreamCdn() {
    override val name: String = "Vanfem"
    override val mainUrl: String = "https://vanfem.com"
}

class Filelions : Filesim() {
    override val name = "Filelions"
    override var mainUrl = "https://filelions.live"
}