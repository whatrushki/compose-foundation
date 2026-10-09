package app.what.foundation.scraper.dsl

import app.what.foundation.scraper.model.ScrapedContentBlock
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

/**
 * Declarative configuration for parsing article body elements into [ScrapedContentBlock]s.
 */
class ContentBodyConfig {
    var subtitlesSelector: String = "h2, h3, h4"
    var paragraphsSelector: String = "p"
    var extractParagraphImages: Boolean = true
    var cleanParagraphNbsp: Boolean = true
    var unorderedListSelector: String = "ul"
    var orderedListSelector: String = "ol"
    var quotesSelector: String = "blockquote"
    var quotesAsInfo: Boolean = false
    var customBlockquoteExtractor: ((Element) -> ScrapedContentBlock.Quote)? = null
    var infoSelector: String? = null
    var videoSelector: String = "iframe, .video-container iframe"
    var videoSrcAttr: String = "src"

    var carouselsSelector: String? = ".gallery, .owl-carousel, .slider-news"
    var carouselImagesSelector: String = "img"

    var customBgImagesSelector: String? = null // e.g. ".img50"

    var skipUntilLeadDelimiter: String? = null // e.g. "hr"

    var groupTrailingImagesToCarousel: Boolean = false
    var deduplicateBannerAgainstFirstImage: Boolean = false
    var splitConsecutiveBr: Boolean = false
    var fallbackCarouselSelector: String? = null

    // Enhanced declarative options
    var smartListDetectionFromText: Boolean = false
    var mergeConsecutiveLists: Boolean = false
    var preserveHeaderLinksAsText: Boolean = false
    var stripDuplicateTitle: Boolean = false
    var title: String? = null

    fun subtitles(selector: String) {
        subtitlesSelector = selector
    }

    fun paragraphs(selector: String = "p", extractImages: Boolean = true, cleanNbsp: Boolean = true) {
        paragraphsSelector = selector
        extractParagraphImages = extractImages
        cleanParagraphNbsp = cleanNbsp
    }

    fun lists(unordered: String = "ul", ordered: String = "ol") {
        unorderedListSelector = unordered
        orderedListSelector = ordered
    }

    fun quotes(selector: String = "blockquote") {
        quotesSelector = selector
    }

    fun carousels(selector: String, imagesSelector: String = "img") {
        carouselsSelector = selector
        this.carouselImagesSelector = imagesSelector
    }

    fun videos(selector: String = "iframe", srcAttr: String = "src") {
        videoSelector = selector
        videoSrcAttr = srcAttr
    }
}

/**
 * Engine that transforms an article body [Element] into structured [ScrapedContentBlock]s
 * following the declarative rules of [ContentBodyConfig].
 */
class ContentBodyParser(
    private val config: ContentBodyConfig,
    private val baseUrl: String
) {
    private fun isTrackingPixelOrIcon(src: String): Boolean {
        val lower = src.lowercase().trim()
        if (lower.isEmpty()) return true
        if (lower.startsWith("data:image")) return false
        if (lower.contains("mc.yandex.ru") ||
            lower.contains("google-analytics.com") ||
            lower.contains("vk.com/rtrg") ||
            lower.contains("top100.rambler.ru") ||
            lower.contains("counter.yadro.ru") ||
            lower.contains("stat.gif") ||
            lower.contains("pixel.gif") ||
            lower.contains("hit.gif") ||
            lower.contains("1x1") ||
            (lower.endsWith(".svg") && (lower.contains("logo") || lower.contains("icon") || lower.contains("banner-fluid")))
        ) {
            return true
        }
        return false
    }

    private fun formatUrl(url: String): String {
        val trimmed = url.trim()
        val formatted = when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("mailto:") || trimmed.startsWith("tel:") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("www.") || trimmed.startsWith("vk.com") || trimmed.startsWith("t.me") -> "https://$trimmed"
            trimmed.startsWith("/") -> if (baseUrl.endsWith("/")) baseUrl.dropLast(1) + trimmed else "$baseUrl$trimmed"
            else -> if (baseUrl.endsWith("/")) "$baseUrl$trimmed" else "$baseUrl/$trimmed"
        }
        return formatted.replace(Regex("(?<!:)//+"), "/")
    }

    private fun extractContentElements(container: Element): List<Element> {
        val contentSelectors = listOf(
            config.subtitlesSelector,
            config.paragraphsSelector,
            config.unorderedListSelector,
            config.orderedListSelector,
            config.quotesSelector,
            config.carouselsSelector,
            config.videoSelector,
            config.infoSelector,
            config.customBgImagesSelector,
            "img"
        ).filterNotNull().filter { it.isNotBlank() }

        val combinedSelector = contentSelectors.joinToString(", ")
        val allMatches = try {
            container.select(combinedSelector)
        } catch (_: Exception) {
            emptyList()
        }

        if (allMatches.isEmpty()) {
            return container.children()
        }

        val compositeSelectors = listOfNotNull(
            config.carouselsSelector,
            config.customBgImagesSelector,
            config.infoSelector
        ).filter { it.isNotBlank() }

        val leafMatches = allMatches.filter { el ->
            val isDivOrContainer = el.tagName().equals("div", ignoreCase = true) ||
                    el.tagName().equals("section", ignoreCase = true) ||
                    el.tagName().equals("article", ignoreCase = true)

            if (isDivOrContainer) {
                val isCompositeBlock = compositeSelectors.any { matchesAnySelector(el, it) }
                if (isCompositeBlock) {
                    true
                } else {
                    // If this div contains other matched content elements, don't treat the div itself as a leaf
                    val hasNestedContent = allMatches.any { other -> other != el && other.parents().contains(el) }
                    !hasNestedContent
                }
            } else {
                true
            }
        }

        val matchSet = leafMatches.toSet()
        val topLevel = leafMatches.filter { el ->
            el.parents().none { parent -> parent != container && parent in matchSet }
        }
        return if (topLevel.isNotEmpty()) topLevel else container.children()
    }

    fun parse(container: Element): Pair<List<ScrapedContentBlock>, List<String>> {
        val blocks = mutableListOf<ScrapedContentBlock>()
        val allImages = mutableListOf<String>()

        val subtitleTags = config.subtitlesSelector.split(",").map { it.trim().lowercase() }.toSet()
        val children = extractContentElements(container)

        var skippedLead = config.skipUntilLeadDelimiter == null

        for (child in children) {
            val tag = child.tagName().lowercase()

            if (!skippedLead) {
                if (config.skipUntilLeadDelimiter != null && (tag == config.skipUntilLeadDelimiter || child.selectFirst(config.skipUntilLeadDelimiter!!) != null)) {
                    skippedLead = true
                    continue
                }
                // Skip lead paragraph if requested
                if (tag == "p" && (child.getElementsByTag("b").isNotEmpty() || child.getElementsByTag("strong").isNotEmpty())) {
                    skippedLead = true
                    continue
                }
            }

            when {
                tag in subtitleTags -> {
                    val text = child.text().replace("\u00A0", " ").trim()
                    if (text.isNotBlank()) {
                        if (config.stripDuplicateTitle && config.title != null && text.equals(config.title!!.trim(), ignoreCase = true)) {
                            // Skip duplicated title in header
                        } else {
                            val links = child.select("a")
                            if (links.isNotEmpty() && config.preserveHeaderLinksAsText) {
                                // Subtitle contains links: convert relative links to absolute
                                links.forEach { a ->
                                    val href = a.attr("href").trim()
                                    if (href.isNotEmpty()) a.attr("href", formatUrl(href))
                                }
                                blocks.add(ScrapedContentBlock.Text("<strong>${child.html().trim()}</strong>"))
                            } else {
                                blocks.add(ScrapedContentBlock.Subtitle(text))
                            }
                        }
                    }
                }

                config.customBgImagesSelector != null && (child.hasClass(config.customBgImagesSelector!!.removePrefix(".")) || child.selectFirst(config.customBgImagesSelector!!) != null) -> {
                    val targetElem = if (child.hasClass(config.customBgImagesSelector!!.removePrefix("."))) child else child.selectFirst(config.customBgImagesSelector!!)!!
                    val imgList = mutableListOf<String>()
                    targetElem.getElementsByTag("p").forEach { p ->
                        val style = p.attr("style")
                        val bgUrl = if ("background-image" in style) {
                            Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)?.groupValues?.get(1)
                        } else null
                        val src = bgUrl ?: p.getElementsByTag("img").firstOrNull()?.attr("src")
                        if (!src.isNullOrBlank()) {
                            val formatted = formatUrl(src)
                            imgList.add(formatted)
                            allImages.add(formatted)
                        }
                    }
                    targetElem.children().filter { it.tagName() == "img" }.forEach { img ->
                        val src = img.attr("src")
                        if (src.isNotBlank()) {
                            val formatted = formatUrl(src)
                            imgList.add(formatted)
                            allImages.add(formatted)
                        }
                    }
                    for (imgUrl in imgList) {
                        blocks.add(ScrapedContentBlock.Image(imgUrl))
                    }
                }

                config.carouselsSelector != null && (matchesAnySelector(child, config.carouselsSelector!!) || child.selectFirst(config.carouselsSelector!!) != null) -> {
                    val imgs = extractCarouselImages(child, config.carouselImagesSelector)
                    if (imgs.isNotEmpty()) {
                        blocks.add(ScrapedContentBlock.ImageCarousel(imgs))
                        allImages.addAll(imgs)
                    }
                }

                tag == "img" -> {
                    val src = child.attr("src").ifBlank { child.attr("data-src") }.trim()
                    if (src.isNotEmpty() && !isTrackingPixelOrIcon(src)) {
                        val formatted = formatUrl(src)
                        if (formatted.isNotEmpty()) {
                            blocks.add(ScrapedContentBlock.Image(formatted))
                            allImages.add(formatted)
                        }
                    }
                }

                tag == "ul" -> {
                    val items = child.select("li").map { it.text().replace("\u00A0", " ").trim() }.filter { it.isNotBlank() }
                    if (items.isNotEmpty()) blocks.add(ScrapedContentBlock.UnsortedList(items))
                }

                tag == "ol" -> {
                    val items = child.select("li").map { it.text().replace("\u00A0", " ").trim() }.filter { it.isNotBlank() }
                    if (items.isNotEmpty()) blocks.add(ScrapedContentBlock.SortedList(items))
                }

                tag == "blockquote" -> {
                    val custom = config.customBlockquoteExtractor?.invoke(child)
                    if (custom != null) {
                        blocks.add(custom)
                    } else {
                        val q = child.text().replace("\u00A0", " ").trim()
                        if (q.isNotBlank()) {
                            if (config.quotesAsInfo) {
                                blocks.add(ScrapedContentBlock.Info(q))
                            } else {
                                blocks.add(ScrapedContentBlock.Quote(q))
                            }
                        }
                    }
                }

                config.infoSelector != null && matchesAnySelector(child, config.infoSelector!!) -> {
                    val text = child.getElementsByClass("highlight__content").firstOrNull()?.getElementsByTag("p")?.firstOrNull()?.text()
                        ?: child.text().trim()
                    if (text.isNotBlank()) blocks.add(ScrapedContentBlock.Info(text))
                }

                child.selectFirst(config.videoSelector) != null || tag == "iframe" -> {
                    val iframe = if (tag == "iframe") child else child.selectFirst(config.videoSelector)
                    val src = iframe?.attr(config.videoSrcAttr)?.trim().orEmpty()
                    if (src.isNotEmpty()) {
                        blocks.add(ScrapedContentBlock.VideoVK(src))
                    }
                }

                matchesAnySelector(child, config.paragraphsSelector) -> {
                    // Make links absolute inside paragraph
                    child.select("a").forEach { a ->
                        val href = a.attr("href").trim()
                        if (href.isNotEmpty()) a.attr("href", formatUrl(href))
                    }

                    if (config.extractParagraphImages && child.getElementsByTag("img").isNotEmpty()) {
                        child.getElementsByTag("img").forEach { img ->
                            val src = img.attr("src").ifBlank { img.attr("data-src") }.trim()
                            if (src.isNotEmpty() && !isTrackingPixelOrIcon(src)) {
                                val formatted = formatUrl(src)
                                if (formatted.isNotEmpty()) {
                                    blocks.add(ScrapedContentBlock.Image(formatted))
                                    allImages.add(formatted)
                                }
                            }
                        }
                        val textOnly = child.clone()
                        textOnly.getElementsByTag("img").remove()
                        val textHtml = textOnly.html().trim()
                        if (textHtml.isNotBlank() && textOnly.text().trim().isNotBlank()) {
                            if (config.smartListDetectionFromText) {
                                blocks.addAll(smartParseTextToBlocks(textHtml))
                            } else if (config.splitConsecutiveBr) {
                                val paragraphs = textHtml.split(Regex("""(?:<br\s*/?>\s*){2,}"""))
                                for (p in paragraphs) {
                                    var clean = p.trim()
                                    if (config.cleanParagraphNbsp) {
                                        clean = clean.replace(Regex("""^(&nbsp;|\s|\u00A0)+"""), "")
                                            .replace(Regex("""^<p>(&nbsp;|\s|\u00A0)+"""), "<p>")
                                    }
                                    if (clean.isNotBlank()) {
                                        blocks.add(ScrapedContentBlock.Text(clean))
                                    }
                                }
                            } else {
                                var html = textHtml
                                if (config.cleanParagraphNbsp) {
                                    html = html.replace(Regex("""^(&nbsp;|\s|\u00A0)+"""), "")
                                        .replace(Regex("""^<p>(&nbsp;|\s|\u00A0)+"""), "<p>")
                                }
                                blocks.add(ScrapedContentBlock.Text(html))
                            }
                        }
                    } else {
                        val rawHtml = child.html().trim()
                        if (rawHtml.isNotBlank() && child.text().trim().isNotBlank()) {
                            if (config.smartListDetectionFromText) {
                                blocks.addAll(smartParseTextToBlocks(rawHtml))
                            } else if (config.splitConsecutiveBr) {
                                val paragraphs = rawHtml.split(Regex("""(?:<br\s*/?>\s*){2,}"""))
                                for (p in paragraphs) {
                                    var clean = p.trim()
                                    if (config.cleanParagraphNbsp) {
                                        clean = clean.replace(Regex("""^(&nbsp;|\s|\u00A0)+"""), "")
                                            .replace(Regex("""^<p>(&nbsp;|\s|\u00A0)+"""), "<p>")
                                    }
                                    if (clean.isNotBlank()) {
                                        blocks.add(ScrapedContentBlock.Text(clean))
                                    }
                                }
                            } else {
                                var html = rawHtml
                                if (config.cleanParagraphNbsp) {
                                    html = html.replace(Regex("""^(&nbsp;|\s|\u00A0)+"""), "")
                                        .replace(Regex("""^<p>(&nbsp;|\s|\u00A0)+"""), "<p>")
                                }
                                blocks.add(ScrapedContentBlock.Text(html))
                            }
                        }
                    }
                }
            }
        }

        // Merge consecutive lists if enabled
        if (config.mergeConsecutiveLists) {
            val mergedListBlocks = mutableListOf<ScrapedContentBlock>()
            for (block in blocks) {
                val last = mergedListBlocks.lastOrNull()
                if (last is ScrapedContentBlock.SortedList && block is ScrapedContentBlock.SortedList) {
                    mergedListBlocks[mergedListBlocks.size - 1] = ScrapedContentBlock.SortedList(last.items + block.items)
                } else if (last is ScrapedContentBlock.UnsortedList && block is ScrapedContentBlock.UnsortedList) {
                    mergedListBlocks[mergedListBlocks.size - 1] = ScrapedContentBlock.UnsortedList(last.items + block.items)
                } else {
                    mergedListBlocks.add(block)
                }
            }
            blocks.clear()
            blocks.addAll(mergedListBlocks)
        }

        if (blocks.none { it is ScrapedContentBlock.ImageCarousel } && config.fallbackCarouselSelector != null) {
            val slider = container.parent()?.selectFirst(config.fallbackCarouselSelector!!)
                ?: container.selectFirst(config.fallbackCarouselSelector!!)
            if (slider != null) {
                val imgs = extractCarouselImages(slider, config.carouselImagesSelector)
                if (imgs.isNotEmpty()) {
                    blocks.add(ScrapedContentBlock.ImageCarousel(imgs))
                    allImages.addAll(imgs)
                }
            }
        }

        if (config.groupTrailingImagesToCarousel) {
            // Group trailing images into carousel while preserving trailing text links
            var endIdx = blocks.size - 1
            val trailingLinks = mutableListOf<ScrapedContentBlock>()
            while (endIdx >= 0) {
                val b = blocks[endIdx]
                if (b is ScrapedContentBlock.Text && (b.html.contains("<a", ignoreCase = true) || b.html.contains("http", ignoreCase = true))) {
                    trailingLinks.add(0, b)
                    endIdx--
                } else {
                    break
                }
            }

            val trailingImages = mutableListOf<String>()
            var imgIdx = endIdx
            while (imgIdx >= 0 && blocks[imgIdx] is ScrapedContentBlock.Image) {
                trailingImages.add(0, (blocks[imgIdx] as ScrapedContentBlock.Image).url)
                imgIdx--
            }

            if (trailingImages.size >= 2) {
                while (blocks.size > imgIdx + 1) {
                    blocks.removeAt(blocks.size - 1)
                }
                blocks.add(ScrapedContentBlock.ImageCarousel(trailingImages))
                blocks.addAll(trailingLinks)
            }
        }

        if (config.deduplicateBannerAgainstFirstImage) {
            val banner = allImages.firstOrNull()
            if (banner != null) {
                val firstImgIdx = blocks.indexOfFirst { it is ScrapedContentBlock.Image }
                if (firstImgIdx != -1) {
                    val firstImg = blocks[firstImgIdx] as ScrapedContentBlock.Image
                    fun normalizeUrl(u: String) = u.trim().lowercase().substringAfterLast('/').substringBeforeLast('.')
                    if (firstImg.url == banner || normalizeUrl(firstImg.url) == normalizeUrl(banner)) {
                        blocks.removeAt(firstImgIdx)
                    }
                }
            }
        }

        // Strip duplicate title if requested
        val effectiveTitle = config.title?.trim().orEmpty()
        if (config.stripDuplicateTitle && effectiveTitle.isNotBlank() && blocks.isNotEmpty()) {
            fun normalizeForComparison(s: String) = s.lowercase()
                .replace(Regex("""[«»"“”',.!?:;\-\s]+"""), "")
                .trim()

            val cleanTitle = normalizeForComparison(effectiveTitle)
            if (cleanTitle.isNotEmpty()) {
                val firstTextIdx = blocks.indexOfFirst { it is ScrapedContentBlock.Text || it is ScrapedContentBlock.Subtitle }
                if (firstTextIdx != -1) {
                    val block = blocks[firstTextIdx]
                    val blockText = when (block) {
                        is ScrapedContentBlock.Text -> Ksoup.parseBodyFragment(block.html).text().trim()
                        is ScrapedContentBlock.Subtitle -> block.text.trim()
                        else -> ""
                    }
                    val cleanBlock = normalizeForComparison(blockText)
                    if (cleanBlock.isNotEmpty()) {
                        if (cleanTitle == cleanBlock || 
                            (cleanBlock.startsWith(cleanTitle) && cleanBlock.length <= cleanTitle.length + 15) ||
                            (cleanTitle.startsWith(cleanBlock) && cleanTitle.length <= cleanBlock.length + 15)) {
                            blocks.removeAt(firstTextIdx)
                        } else if (cleanBlock.startsWith(cleanTitle)) {
                            val rawClean = Ksoup.parseBodyFragment(blockText).text().trim()
                            val afterTitle = rawClean.removePrefix(effectiveTitle).trim()
                                .removePrefix(".").removePrefix(":").removePrefix("-").removePrefix("—").trim()
                            if (afterTitle.isNotEmpty()) {
                                blocks[firstTextIdx] = ScrapedContentBlock.Text(afterTitle)
                            } else {
                                blocks.removeAt(firstTextIdx)
                            }
                        }
                    }
                }
            }
        }

        return Pair(blocks, allImages)
    }

    private fun smartParseTextToBlocks(rawHtml: String): List<ScrapedContentBlock> {
        val cleanHtml = rawHtml.trim()
        if (cleanHtml.isEmpty()) return emptyList()

        val textCheck = Ksoup.parseBodyFragment(cleanHtml).text().replace("\u00A0", " ").trim()
        if (textCheck.isEmpty() && !cleanHtml.contains("<img", ignoreCase = true) && !cleanHtml.contains("<iframe", ignoreCase = true)) {
            return emptyList()
        }

        val normalized = cleanHtml
            .replace(Regex("""(<br\s*/?>\s*){2,}""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val rawLines = normalized.split("\n").map { it.trim() }
        val result = mutableListOf<ScrapedContentBlock>()

        var currentParagraph = StringBuilder()
        val currentUnsortedList = mutableListOf<String>()
        val currentSortedList = mutableListOf<String>()

        fun flushList() {
            if (currentUnsortedList.isNotEmpty()) {
                result.add(ScrapedContentBlock.UnsortedList(currentUnsortedList.toList()))
                currentUnsortedList.clear()
            }
            if (currentSortedList.isNotEmpty()) {
                result.add(ScrapedContentBlock.SortedList(currentSortedList.toList()))
                currentSortedList.clear()
            }
        }

        fun flushParagraph() {
            val text = currentParagraph.toString().trim()
            if (text.isNotEmpty()) {
                val clean = Ksoup.parseBodyFragment(text).text().replace("\u00A0", " ").trim()
                if (clean.isNotEmpty()) {
                    val balanced = Ksoup.parseBodyFragment(text).body().html().trim()
                    if (balanced.isNotEmpty()) {
                        result.add(ScrapedContentBlock.Text(balanced))
                    }
                }
            }
            currentParagraph = StringBuilder()
        }

        val bulletRegex = Regex("""^(?:[•*]|[-—–](?![-—–]))\s+(.+)""")
        val numberRegex = Regex("""^(\d{1,3})[\.)]\s+(.+)""")

        for (rawLine in rawLines) {
            val line = if (rawLine.startsWith("--")) {
                "— " + rawLine.removePrefix("--").trimStart()
            } else {
                rawLine
            }
            val lineClean = Ksoup.parseBodyFragment(line).text().replace("\u00A0", " ").trim()
            if (lineClean.isEmpty()) {
                flushParagraph()
                flushList()
                continue
            }

            val hasLinksOrMedia = line.contains("<a ", ignoreCase = true) || 
                                  line.contains("<iframe", ignoreCase = true) || 
                                  line.contains("<video", ignoreCase = true)

            val isDateAtStart = Regex("""^\d{1,2}\.\d{1,2}(\.\d{2,4})?(\s*г\.?|\s*года)?\b""").containsMatchIn(lineClean)

            val bulletMatch = if (!hasLinksOrMedia) bulletRegex.find(line) else null
            val numberMatch = if (!hasLinksOrMedia && !isDateAtStart) numberRegex.find(line) else null

            if (bulletMatch != null) {
                flushParagraph()
                if (currentSortedList.isNotEmpty()) flushList()
                val itemText = bulletMatch.groups[1]?.value?.trim().orEmpty()
                val cleanItem = Ksoup.parseBodyFragment(itemText).text().replace("\u00A0", " ").trim()
                if (cleanItem.isNotEmpty()) {
                    currentUnsortedList.add(cleanItem)
                }
                continue
            }

            if (numberMatch != null) {
                flushParagraph()
                if (currentUnsortedList.isNotEmpty()) flushList()
                val itemText = numberMatch.groups[2]?.value?.trim().orEmpty()
                val cleanItem = Ksoup.parseBodyFragment(itemText).text().replace("\u00A0", " ").trim()
                if (cleanItem.isNotEmpty()) {
                    currentSortedList.add(cleanItem)
                }
                continue
            }

            // Normal text line
            flushList()

            if (currentParagraph.isEmpty()) {
                currentParagraph.append(line)
            } else {
                val prevText = currentParagraph.toString().trim()
                val prevClean = Ksoup.parseBodyFragment(prevText).body().text().trim()
                val currClean = Ksoup.parseBodyFragment(line).body().text().trim()

                val prevEndsWithSentencePunct = prevClean.isNotEmpty() &&
                    (prevClean.endsWith(".") || prevClean.endsWith("!") || prevClean.endsWith("?") ||
                     prevClean.endsWith(":") || prevClean.endsWith("»") || prevClean.endsWith("\"") ||
                     prevClean.endsWith(";"))

                val currStartsWithUpper = currClean.isNotEmpty() &&
                    (currClean[0].isUpperCase() || currClean.startsWith("«") || currClean.startsWith("\""))

                val currStartsWithLower = currClean.isNotEmpty() && currClean[0].isLowerCase()

                if (prevEndsWithSentencePunct && currStartsWithUpper) {
                    flushParagraph()
                    currentParagraph.append(line)
                } else if (currStartsWithLower) {
                    currentParagraph.append(" ").append(line)
                } else if (prevClean.endsWith(":")) {
                    flushParagraph()
                    currentParagraph.append(line)
                } else if (currStartsWithUpper && prevClean.length > 50) {
                    flushParagraph()
                    currentParagraph.append(line)
                } else {
                    currentParagraph.append(" ").append(line)
                }
            }
        }

        flushParagraph()
        flushList()

        return result
    }

    private fun extractCarouselImages(container: Element, selectorList: String): List<String> {
        val selectors = selectorList.split(",").map { it.trim() }
        for (sel in selectors) {
            val matches = container.select(sel)
            val extracted = matches.mapNotNull { img ->
                val src = img.attr("src").ifBlank { img.attr("data-src") }
                    .ifBlank { img.attr("data-lazy") }.trim()
                if (src.isNotEmpty() && !isTrackingPixelOrIcon(src)) {
                    val formatted = formatUrl(src)
                    if (formatted.isNotEmpty()) formatted else null
                } else null
            }
            if (extracted.isNotEmpty()) return extracted
        }
        return emptyList()
    }

    private fun matchesAnySelector(el: Element, selectorList: String): Boolean {
        val selectors = selectorList.split(",").map { it.trim() }
        return selectors.any { sel ->
            when {
                sel.startsWith(".") -> el.hasClass(sel.removePrefix("."))
                sel.startsWith("#") -> el.id() == sel.removePrefix("#")
                else -> el.tagName().equals(sel, ignoreCase = true)
            }
        }
    }
}
