package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class Qiwi : ExtractorApi() {
    override val name = "Qiwi"
    override val mainUrl = "https://qiwi.gg"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val title = document.select("title").text()
        val source = document.select("video source").attr("src")

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                source,
                "$mainUrl/",
                getIndexQuality(title),
                headers = mapOf(
                    "Range" to "bytes=0-",
                )
            )
        )

    }

    private fun getIndexQuality(str: String): Int {
        return Regex("(\\d{3,4})[pP]").find(str)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

}

open class Filedon : ExtractorApi() {
    override val name = "Filedon"
    override val mainUrl = "https://filedon.co"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).document
        val token = res.select("meta[name=csrf-token]").attr("content")
        val slug = url.substringAfterLast("/")

        val video = app.post("$mainUrl/get-url", data = mapOf(
            "_token" to token,
            "slug" to slug,
        ), referer = url).parsedSafe<Response>()?.data?.url

        callback.invoke(
            ExtractorLink(
                name,
                name,
                video ?: return,
                "",
                Qualities.Unknown.value,
            )
        )

    }

    data class Data(
        @JsonProperty("url") val url: String,
    )

    data class Response(
        @JsonProperty("data") val data: Data
    )

}