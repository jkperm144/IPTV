package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class OploverzProvider : MainAPI() {
    override var mainUrl = "https://oploverz.media"
    override var name = "Oploverz"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA", true) || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie", true)) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "Finished Airing" -> ShowStatus.Completed
                "Completed" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }

    }

    override val mainPage = mainPageOf(
        "" to "Latest Update",
        "latest" to "Latest Added",
        "popular" to "Popular Anime",
        "rating" to "Top Rated",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url =
            if (request.data.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl/anime/?page=$page&status=&type=&order=${request.data}"
        val document = app.get(url).document
        val home = document.select("div.listupd > article, div.listupd.normal div.excstf > article")
            .mapNotNull {
                it.toSearchResult()
            }
        return newHomePageResponse(request.name, home)
    }

    private suspend fun getProperAnimeLink(uri: String): String? {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            app.get(uri).document.selectFirst("a[itemprop=item][href*=/anime/]")?.attr("href")
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")).toString()
        val title = this.select("div.tt").text().trim()
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        val epNum =
            this.selectFirst("div.eggepisode")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val anime = mutableListOf<SearchResponse>()
        (1..2).forEach { page ->
            val link = "$mainUrl/page/$page/?s=$query"
            val document = app.get(link).document
            val media = document.select("div.listupd > article").mapNotNull {
                it.toSearchResult()
            }
            if (media.isNotEmpty()) anime.addAll(media)
        }
        return anime
    }

    override suspend fun load(url: String): LoadResponse {
        val fixUrl = getProperAnimeLink(url) ?: throw ErrorLoadingException("Could not load")
        val document = app.get(fixUrl).document

        val title = document.selectFirst("h1.entry-title")?.text()
            ?.replace("Subtitle Indonesia", "")?.trim() ?: ""
        val type = getType(document.selectFirst("div.info-content span:contains(Type:)")?.text()?.substringAfter(":")?.trim() ?: "TV")
        val year =
            document.selectFirst("div.alternati a")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val episodes = document.select("div.eplister ul li").mapNotNull {
            val header = it.selectFirst("a") ?: return@mapNotNull null
            val episode = it.select("div.epl-num").text().toIntOrNull()
            val link = fixUrl(header.attr("href"))
            Episode(link, episode = episode)
        }.reversed()

        val tracker = APIHolder.getTracker(listOf(title), TrackerType.getTypes(type), year, true)

        return newAnimeLoadResponse(title, url, type) {
            posterUrl = tracker?.image ?: document.selectFirst("div.thumb > img")?.attr("src")
            backgroundPosterUrl = tracker?.cover
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus =
                getStatus(
                    document.selectFirst("div.alternati span:nth-child(2)")?.text()?.trim()
                )
            plot = document.selectFirst("div.entry-content > p")?.text()?.trim()
            this.tags =
                document.select("div.genre-info a").map { it.text() }
            addMalId(tracker?.malId)
            addAniListId(tracker?.aniId?.toIntOrNull())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        argamap(
            {
                document.select(".mobius > .mirror > option").apmap {
                    val iframe = fixUrl(Jsoup.parse(base64Decode(it.attr("value"))).select("iframe").attr("src"))
                    loadExtractor(fixUrl(iframe), "$mainUrl/", subtitleCallback, callback)
                }
            },
            {
                document.select("div.soraurlx").apmap { el ->
                    el.select("a").apmap {
                        loadFixedExtractor(
                            fixUrl(it.attr("href")),
                            "$mainUrl/",
                            el.select("strong").text(),
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
        )

        return true
    }

    private suspend fun loadFixedExtractor(
        url: String,
        referer: String? = null,
        quality: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            callback.invoke(
                ExtractorLink(
                    link.name,
                    link.name,
                    link.url,
                    link.referer,
                    if(link.type == ExtractorLinkType.M3U8) link.quality else quality.fixQuality(),
                    link.type,
                    link.headers,
                    link.extractorData
                )
            )
        }
    }

    private fun String.fixQuality(): Int {
        return when (this) {
            "MP4HD" -> Qualities.P720.value
            "FULLHD" -> Qualities.P1080.value
            else -> Regex("(\\d{3,4})p").find(this)?.groupValues?.get(1)?.toIntOrNull()
                ?: Qualities.Unknown.value
        }
    }

    private fun Element.getImageAttr(): String? {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

}
