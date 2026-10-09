package app.what.foundation.scraper.dsl

import app.what.foundation.scraper.model.ScrapedContentBlock
import app.what.foundation.scraper.model.ScrapedNewsDetail
import app.what.foundation.scraper.model.ScrapedNewsItem
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class NewsFeedItemBuilder(private val baseUrl: String) {
    var idExtractor: ScraperExtractor<String?> = StringExtractor { el, _ -> null }
    var titleExtractor: ScraperExtractor<String?> = StringExtractor { el, _ -> el.text() }
    var descriptionExtractor: ScraperExtractor<String?> = StringExtractor { _, _ -> "" }
    var dateExtractor: ScraperExtractor<LocalDate> = ScraperExtractor { _, _ ->
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var imageExtractor: ScraperExtractor<String?> = StringExtractor { _, _ -> null }
    var sourceUrlExtractor: ScraperExtractor<String?> = StringExtractor { el, base ->
        val href = el.selectFirst("a")?.attr("href")?.trim().orEmpty()
        if (href.startsWith("http")) href else "$base$href"
    }
    var tagsExtractor: ScraperExtractor<List<String>> = ScraperExtractor { _, _ -> emptyList() }

    // Declarative helper properties/setters
    var id: ScraperExtractor<String?>
        get() = idExtractor
        set(value) { idExtractor = value }

    var title: ScraperExtractor<String?>
        get() = titleExtractor
        set(value) { titleExtractor = value }

    var description: ScraperExtractor<String?>
        get() = descriptionExtractor
        set(value) { descriptionExtractor = value }

    var date: ScraperExtractor<LocalDate>
        get() = dateExtractor
        set(value) { dateExtractor = value }

    var image: ScraperExtractor<String?>
        get() = imageExtractor
        set(value) { imageExtractor = value }

    var sourceUrl: ScraperExtractor<String?>
        get() = sourceUrlExtractor
        set(value) { sourceUrlExtractor = value }

    var tags: ScraperExtractor<List<String>>
        get() = tagsExtractor
        set(value) { tagsExtractor = value }

    var filterPredicate: ((Element) -> Boolean)? = null
    var allowEmptyTitle: Boolean = false

    fun filter(predicate: (Element) -> Boolean) {
        filterPredicate = predicate
    }

    // Infix helper: `title from text("h4")`
    infix fun <T> ScraperExtractor<T>.from(other: ScraperExtractor<T>) {
        when (this) {
            idExtractor -> idExtractor = other as ScraperExtractor<String?>
            titleExtractor -> titleExtractor = other as ScraperExtractor<String?>
            descriptionExtractor -> descriptionExtractor = other as ScraperExtractor<String?>
            dateExtractor -> dateExtractor = other as ScraperExtractor<LocalDate>
            imageExtractor -> imageExtractor = other as ScraperExtractor<String?>
            sourceUrlExtractor -> sourceUrlExtractor = other as ScraperExtractor<String?>
            tagsExtractor -> tagsExtractor = other as ScraperExtractor<List<String>>
        }
    }
}

class NewsFeedBuilder(private val baseUrl: String) {
    var itemsSelector: String = ""
    private var customParseItems: ((Element) -> List<Element>)? = null
    private val itemBuilder = NewsFeedItemBuilder(baseUrl)

    fun items(selector: String, block: NewsFeedItemBuilder.() -> Unit = {}) {
        this.itemsSelector = selector
        itemBuilder.apply(block)
    }

    fun items(customSelector: (Element) -> List<Element>, block: NewsFeedItemBuilder.() -> Unit = {}) {
        this.customParseItems = customSelector
        itemBuilder.apply(block)
    }

    fun item(block: NewsFeedItemBuilder.() -> Unit) {
        itemBuilder.apply(block)
    }

    fun parseHtml(html: String): List<ScrapedNewsItem> {
        val doc = Ksoup.parse(html)
        val elements = if (customParseItems != null) {
            customParseItems!!.invoke(doc)
        } else if (itemsSelector.isNotBlank()) {
            doc.select(itemsSelector)
        } else emptyList()

        return elements.mapNotNull { el ->
            if (itemBuilder.filterPredicate != null && !itemBuilder.filterPredicate!!.invoke(el)) {
                return@mapNotNull null
            }
            val id = itemBuilder.idExtractor.extract(el, baseUrl)?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            val title = itemBuilder.titleExtractor.extract(el, baseUrl)?.trim().orEmpty()
            if (!itemBuilder.allowEmptyTitle && title.isBlank()) return@mapNotNull null
            val desc = itemBuilder.descriptionExtractor.extract(el, baseUrl)?.trim().orEmpty()
            val date = itemBuilder.dateExtractor.extract(el, baseUrl)
            val img = itemBuilder.imageExtractor.extract(el, baseUrl)?.trim()?.ifEmpty { null }
            val src = itemBuilder.sourceUrlExtractor.extract(el, baseUrl)?.trim().orEmpty()
            val tags = itemBuilder.tagsExtractor.extract(el, baseUrl)
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

class NewsDetailBuilder(private val baseUrl: String) {
    var titleSelector: String = "h1"
    var titleExtractor: ScraperExtractor<String?> = text("h1")

    var bodySelector: String = "article, main, .text"
    var bodyExtractor: ((Element) -> Element?)? = null

    var dateExtractor: ScraperExtractor<LocalDate> = ScraperExtractor { _, _ ->
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var bannerExtractor: ScraperExtractor<String?> = StringExtractor { _, _ -> null }
    var descriptionHtmlExtractor: ScraperExtractor<String?> = StringExtractor { _, _ -> null }
    var tagsExtractor: ScraperExtractor<List<String>> = ScraperExtractor { _, _ -> emptyList() }
    var fullTextExtractor: ScraperExtractor<String?> = StringExtractor { _, _ -> null }

    val contentConfig = ContentBodyConfig()
    private var customBlockExtractor: ((Element) -> List<ScrapedContentBlock>)? = null
    private var customGalleryExtractor: ((Element) -> List<String>)? = null

    // Declarative properties
    var title: ScraperExtractor<String?>
        get() = titleExtractor
        set(value) { titleExtractor = value }

    var date: ScraperExtractor<LocalDate>
        get() = dateExtractor
        set(value) { dateExtractor = value }

    var banner: ScraperExtractor<String?>
        get() = bannerExtractor
        set(value) { bannerExtractor = value }

    var descriptionHtml: ScraperExtractor<String?>
        get() = descriptionHtmlExtractor
        set(value) { descriptionHtmlExtractor = value }

    var tags: ScraperExtractor<List<String>>
        get() = tagsExtractor
        set(value) { tagsExtractor = value }

    fun content(selector: String = "article, main, .text", block: ContentBodyConfig.() -> Unit = {}) {
        this.bodySelector = selector
        this.contentConfig.apply(block)
    }

    fun blockExtractor(extractor: (Element) -> List<ScrapedContentBlock>) {
        this.customBlockExtractor = extractor
    }

    fun galleryExtractor(extractor: (Element) -> List<String>) {
        this.customGalleryExtractor = extractor
    }

    fun parseHtml(html: String, id: String, url: String): ScrapedNewsDetail {
        val doc = Ksoup.parse(html)
        val documentBody = (doc as? Document)?.body() ?: doc.ownerDocument()?.body()
        val bodyElem = bodyExtractor?.invoke(doc)
            ?: doc.selectFirst(bodySelector)
            ?: documentBody
            ?: doc

        val title = titleExtractor.extract(doc, baseUrl)?.trim().orEmpty()
        val date = dateExtractor.extract(doc, baseUrl)
        val descHtml = descriptionHtmlExtractor.extract(doc, baseUrl)?.trim()?.ifEmpty { null }
        val tags = tagsExtractor.extract(doc, baseUrl)

        if (contentConfig.title == null && title.isNotEmpty()) {
            contentConfig.title = title
        }

        val bodyParser = ContentBodyParser(contentConfig, baseUrl)
        val (parsedBlocks, parsedGallery) = if (bodyElem != null) {
            bodyParser.parse(bodyElem)
        } else Pair(emptyList(), emptyList())

        val blocks = if (customBlockExtractor != null && bodyElem != null) {
            customBlockExtractor!!.invoke(bodyElem)
        } else {
            parsedBlocks
        }

        val gallery = if (customGalleryExtractor != null && bodyElem != null) {
            customGalleryExtractor!!.invoke(bodyElem)
        } else {
            parsedGallery
        }

        var banner = bannerExtractor.extract(doc, baseUrl)?.trim()?.ifEmpty { null }
        if (banner != null && (banner.contains("mc.yandex") || banner.contains("pixel") || banner.contains("stat.gif") || (banner.endsWith(".svg") && banner.contains("logo")))) {
            banner = null
        }
        if (banner == null && gallery.isNotEmpty()) {
            banner = gallery.firstOrNull()
        }

        val fullText = fullTextExtractor.extract(doc, baseUrl)?.trim()
            ?: bodyElem?.text()?.trim().orEmpty()

        val finalGallery = if (banner != null && !gallery.contains(banner)) {
            listOf(banner) + gallery
        } else gallery

        return ScrapedNewsDetail(
            id = id,
            title = title,
            fullText = fullText,
            descriptionHtml = descHtml,
            date = date,
            bannerUrl = banner,
            sourceUrl = url,
            contentBlocks = blocks,
            galleryImages = finalGallery.distinct(),
            tags = tags
        )
    }

    fun parseElementContent(container: Element): Pair<List<ScrapedContentBlock>, List<String>> {
        val bodyParser = ContentBodyParser(contentConfig, baseUrl)
        return bodyParser.parse(container)
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
    fun parseContentBlocks(container: Element): List<ScrapedContentBlock> =
        detailBuilder.parseElementContent(container).first
}

class NewsPipelineBuilder {
    var baseUrl: String = ""
    private var feedBuilder: NewsFeedBuilder? = null
    private var detailBuilder: NewsDetailBuilder? = null

    fun feed(block: NewsFeedBuilder.() -> Unit) {
        val fb = NewsFeedBuilder(baseUrl).apply(block)
        feedBuilder = fb
    }

    fun detail(block: NewsDetailBuilder.() -> Unit) {
        val db = NewsDetailBuilder(baseUrl).apply(block)
        detailBuilder = db
    }

    fun build(): NewsPipeline = NewsPipeline(
        baseUrl = baseUrl,
        feedBuilder = feedBuilder ?: NewsFeedBuilder(baseUrl),
        detailBuilder = detailBuilder ?: NewsDetailBuilder(baseUrl)
    )
}

fun newsPipeline(block: NewsPipelineBuilder.() -> Unit): NewsPipeline =
    NewsPipelineBuilder().apply(block).build()
