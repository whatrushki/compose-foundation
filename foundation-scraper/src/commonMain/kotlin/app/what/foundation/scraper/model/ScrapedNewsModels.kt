package app.what.foundation.scraper.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ScrapedNewsItem(
    val id: String,
    val title: String,
    val description: String,
    val date: LocalDate,
    val imageUrl: String? = null,
    val sourceUrl: String,
    val tags: List<String> = emptyList()
)

@Serializable
sealed interface ScrapedContentBlock {
    @Serializable
    data class Text(val html: String) : ScrapedContentBlock

    @Serializable
    data class Subtitle(val text: String) : ScrapedContentBlock

    @Serializable
    data class Image(val url: String, val caption: String? = null) : ScrapedContentBlock

    @Serializable
    data class Quote(val text: String, val author: String? = null) : ScrapedContentBlock
}

@Serializable
data class ScrapedNewsDetail(
    val id: String,
    val title: String,
    val fullText: String,
    val descriptionHtml: String? = null,
    val date: LocalDate,
    val bannerUrl: String? = null,
    val sourceUrl: String,
    val contentBlocks: List<ScrapedContentBlock> = emptyList(),
    val galleryImages: List<String> = emptyList()
)
