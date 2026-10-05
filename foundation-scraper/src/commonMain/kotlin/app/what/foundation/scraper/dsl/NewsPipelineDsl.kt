package app.what.foundation.scraper.dsl

import app.what.foundation.scraper.model.ScrapedContentBlock
import app.what.foundation.scraper.model.ScrapedNewsDetail
import app.what.foundation.scraper.model.ScrapedNewsItem
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class NewsFeedItemBuilder {
    var idExtractor: (Element) -> String? = { null }
    var titleExtractor: (Element) -> String = { it.text() }
    var descriptionExtractor: (Element) -> String = { "" }
    var dateExtractor: (Element) -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var imageExtractor: (Element) -> String? = { null }
    var sourceUrlExtractor: (Element) -> String = { "" }
    var tagsExtractor: (Element) -> List<String> = { emptyList() }
}

class NewsFeedBuilder {
    var itemsSelector: String = ""
    private val itemBuilder = NewsFeedItemBuilder()

    fun item(block: NewsFeedItemBuilder.() -> Unit) {
        itemBuilder.apply(block)
    }

    fun parseHtml(html: String): List<ScrapedNewsItem> {
        val doc = Ksoup.parse(html)
        val elements = if (itemsSelector.isNotBlank()) doc.select(itemsSelector) else emptyList()
        return elements.mapNotNull { el ->
            val id = itemBuilder.idExtractor(el) ?: return@mapNotNull null
            val title = itemBuilder.titleExtractor(el)
            val desc = itemBuilder.descriptionExtractor(el)
            val date = itemBuilder.dateExtractor(el)
            val img = itemBuilder.imageExtractor(el)
            val src = itemBuilder.sourceUrlExtractor(el)
            val tags = itemBuilder.tagsExtractor(el)
            ScrapedNewsItem(
                id = id,
                title = title,
                description = desc,
                date = date,
                imageUrl = img,
                sourceUrl = src,
                tags = tags
            )
        }
    }
}

class NewsDetailBuilder {
    var titleSelector: String = "h1"
    var bodySelector: String = "article, main, .text"
    var dateExtractor: (Element) -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var bannerExtractor: (Element) -> String? = { null }

    fun parseHtml(html: String, id: String, url: String): ScrapedNewsDetail {
        val doc = Ksoup.parse(html)
        val title = doc.selectFirst(titleSelector)?.text()?.trim().orEmpty()
        val bodyElem = doc.selectFirst(bodySelector) ?: doc.body()
        val date = dateExtractor(doc)
        val banner = bannerExtractor(doc)

        val blocks = mutableListOf<ScrapedContentBlock>()
        val gallery = mutableListOf<String>()

        bodyElem?.children()?.forEach { child ->
            when (child.tagName()) {
                "h2", "h3", "h4" -> {
                    val t = child.text().trim()
                    if (t.isNotBlank()) blocks.add(ScrapedContentBlock.Subtitle(t))
                }
                "p" -> {
                    val imgs = child.select("img")
                    if (imgs.isNotEmpty()) {
                        imgs.forEach { img ->
                            val src = img.attr("src").trim()
                            if (src.isNotEmpty()) {
                                blocks.add(ScrapedContentBlock.Image(src))
                                gallery.add(src)
                            }
                        }
                    } else {
                        val text = child.text().trim()
                        if (text.isNotBlank()) blocks.add(ScrapedContentBlock.Text(child.html()))
                    }
                }
                "blockquote" -> {
                    val q = child.text().trim()
                    if (q.isNotBlank()) blocks.add(ScrapedContentBlock.Quote(q))
                }
                "img" -> {
                    val src = child.attr("src").trim()
                    if (src.isNotEmpty()) {
                        blocks.add(ScrapedContentBlock.Image(src))
                        gallery.add(src)
                    }
                }
            }
        }

        return ScrapedNewsDetail(
            id = id,
            title = title,
            fullText = bodyElem?.text()?.trim().orEmpty(),
            date = date,
            bannerUrl = banner,
            sourceUrl = url,
            contentBlocks = blocks,
            galleryImages = gallery
        )
    }
}

class NewsPipeline(
    val baseUrl: String,
    val feedBuilder: NewsFeedBuilder,
    val detailBuilder: NewsDetailBuilder
) {
    fun parseFeed(html: String): List<ScrapedNewsItem> = feedBuilder.parseHtml(html)
    fun parseDetail(html: String, id: String, url: String): ScrapedNewsDetail =
        detailBuilder.parseHtml(html, id, url)
}

class NewsPipelineBuilder {
    var baseUrl: String = ""
    private val feedBuilder = NewsFeedBuilder()
    private val detailBuilder = NewsDetailBuilder()

    fun feed(block: NewsFeedBuilder.() -> Unit) {
        feedBuilder.apply(block)
    }

    fun detail(block: NewsDetailBuilder.() -> Unit) {
        detailBuilder.apply(block)
    }

    fun build(): NewsPipeline = NewsPipeline(baseUrl, feedBuilder, detailBuilder)
}

fun newsPipeline(block: NewsPipelineBuilder.() -> Unit): NewsPipeline =
    NewsPipelineBuilder().apply(block).build()
