package app.what.foundation.scraper.dsl

import com.fleeksoft.ksoup.nodes.Element
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Functional extractor that extracts a value of type [T] from a Ksoup [Element] with respect to [baseUrl].
 */
fun interface ScraperExtractor<T> {
    fun extract(element: Element, baseUrl: String): T
}

/**
 * Common string extraction & transformation utilities for declarative scraping.
 */
open class StringExtractor(val fn: (Element, String) -> String?) : ScraperExtractor<String?> {
    override fun extract(element: Element, baseUrl: String): String? = fn(element, baseUrl)

    fun orElse(fallback: StringExtractor): StringExtractor = StringExtractor { el, base ->
        this.extract(el, base)?.takeIf { it.isNotBlank() } ?: fallback.extract(el, base)
    }

    fun orElse(defaultVal: String): StringExtractor = StringExtractor { el, base ->
        this.extract(el, base)?.takeIf { it.isNotBlank() } ?: defaultVal
    }

    fun trim(): StringExtractor = StringExtractor { el, base ->
        this.extract(el, base)?.trim()
    }

    fun asAbsoluteUrl(): StringExtractor = StringExtractor { el, base ->
        val raw = this.extract(el, base)?.trim().orEmpty()
        when {
            raw.isEmpty() -> ""
            raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("mailto:") || raw.startsWith("tel:") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("www.") || raw.startsWith("vk.com") || raw.startsWith("t.me") -> "https://$raw"
            raw.startsWith("/") -> if (base.endsWith("/")) base.dropLast(1) + raw else "$base$raw"
            else -> if (base.endsWith("/")) "$base$raw" else "$base/$raw"
        }
    }

    fun regex(pattern: String, groupIndex: Int = 1): StringExtractor = StringExtractor { el, base ->
        val raw = this.extract(el, base) ?: return@StringExtractor null
        Regex(pattern).find(raw)?.groups?.get(groupIndex)?.value?.trim()
    }

    fun pathSegment(index: Int = -1): StringExtractor = StringExtractor { el, base ->
        val raw = this.extract(el, base) ?: return@StringExtractor null
        val segments = raw.trim().trim('/').split("/").filter { it.isNotEmpty() }
        if (segments.isEmpty()) null
        else if (index < 0) segments.getOrNull(segments.size + index)
        else segments.getOrNull(index)
    }

    fun queryParam(name: String): StringExtractor = StringExtractor { el, base ->
        val raw = this.extract(el, base) ?: return@StringExtractor null
        val query = raw.substringAfter('?', "")
        if (query.isEmpty()) return@StringExtractor null
        query.split("&").map { it.split("=", limit = 2) }.firstOrNull { it.first() == name }?.getOrNull(1)
    }

    fun stripPrefix(prefixExtractor: StringExtractor): StringExtractor = StringExtractor { el, base ->
        val raw = this.extract(el, base) ?: return@StringExtractor null
        val prefix = prefixExtractor.extract(el, base)?.trim().orEmpty()
        if (prefix.isNotEmpty() && raw.startsWith(prefix)) {
            raw.removePrefix(prefix).trim()
        } else raw
    }

    fun format(template: (String) -> String): StringExtractor = StringExtractor { el, base ->
        val raw = this.extract(el, base) ?: return@StringExtractor null
        template(raw)
    }
}

// -------------------------------------------------------------
// DSL entrypoints for String extraction
// -------------------------------------------------------------

fun text(selector: String? = null): StringExtractor = StringExtractor { el, _ ->
    val target = if (selector.isNullOrBlank()) el else el.selectFirst(selector)
    target?.text()?.trim()
}

fun html(selector: String? = null): StringExtractor = StringExtractor { el, _ ->
    val target = if (selector.isNullOrBlank()) el else el.selectFirst(selector)
    target?.html()?.trim()
}

fun attr(attrName: String): StringExtractor = StringExtractor { el, _ ->
    el.attr(attrName).trim().takeIf { it.isNotEmpty() }
}

fun attr(selector: String, attrName: String): StringExtractor = StringExtractor { el, _ ->
    el.selectFirst(selector)?.attr(attrName)?.trim()?.takeIf { it.isNotEmpty() }
}

fun cssStyleUrl(selector: String? = null, propertyName: String = "background-image"): StringExtractor = StringExtractor { el, _ ->
    val target = if (selector.isNullOrBlank()) el else el.selectFirst(selector)
    val style = target?.attr("style").orEmpty()
    if (propertyName in style || "url(" in style) {
        Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)?.groupValues?.get(1)?.trim()
    } else null
}

fun texts(selector: String): ScraperExtractor<List<String>> = ScraperExtractor { el, _ ->
    el.select(selector).map { it.text().trim() }.filter { it.isNotEmpty() }.distinct()
}

// -------------------------------------------------------------
fun regex(pattern: String, groupIndex: Int = 0): StringExtractor = StringExtractor { el, _ ->
    Regex(pattern).find(el.text())?.groups?.get(groupIndex)?.value?.trim()
}

fun regex(selector: String, pattern: String, groupIndex: Int = 0): StringExtractor = StringExtractor { el, _ ->
    val target = el.selectFirst(selector)?.text() ?: return@StringExtractor null
    Regex(pattern).find(target)?.groups?.get(groupIndex)?.value?.trim()
}

// -------------------------------------------------------------
// Date Extractors
// -------------------------------------------------------------

enum class DatePatternType {
    DOT_DMY,       // "15.10.2026" or "1.9.2026"
    D_MONTH_NAME_Y, // "05 октября 2026" or "5 окт 2026"
    AUTO
}

fun dateFrom(
    stringExtractor: ScraperExtractor<String?>,
    pattern: DatePatternType = DatePatternType.DOT_DMY
): ScraperExtractor<LocalDate> = ScraperExtractor { el, base ->
    val raw = stringExtractor.extract(el, base)?.trim().orEmpty()
    val textToParse = if (raw.isNotBlank()) raw else el.text()
    parseDateString(textToParse, pattern)
}

fun dateFrom(
    selector: String? = null,
    attrName: String? = null,
    pattern: DatePatternType = DatePatternType.DOT_DMY
): ScraperExtractor<LocalDate> = ScraperExtractor { el, base ->
    val raw = when {
        selector == null -> el.text()
        attrName != null -> attr(selector, attrName).extract(el, base)
        else -> text(selector).extract(el, base)
    }?.trim().orEmpty()
    val textToParse = if (raw.isNotBlank()) raw else el.text()
    parseDateString(textToParse, pattern)
}

private fun parseDateString(raw: String, pattern: DatePatternType): LocalDate {
    if (raw.isBlank()) return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return try {
        when (pattern) {
            DatePatternType.DOT_DMY -> {
                val match = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})\b""").find(raw)
                if (match != null) {
                    val d = match.groupValues[1].toInt()
                    val m = match.groupValues[2].toInt()
                    val rawYear = match.groupValues[3].toInt()
                    val y = if (rawYear < 100) 2000 + rawYear else rawYear
                    LocalDate(y, m, d)
                } else Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
            DatePatternType.D_MONTH_NAME_Y -> {
                val match = Regex("""\b(\d{1,2})\s+([а-яА-ЯёЁa-zA-Z]+)\s+(\d{2,4})\b""").find(raw)
                if (match != null) {
                    val d = match.groupValues[1].toInt()
                    val m = parseRussianMonth(match.groupValues[2])
                    val rawYear = match.groupValues[3].toInt()
                    val y = if (rawYear < 100) 2000 + rawYear else rawYear
                    LocalDate(y, m, d)
                } else Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
            DatePatternType.AUTO -> {
                val dotMatch = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})\b""").find(raw)
                if (dotMatch != null) {
                    val d = dotMatch.groupValues[1].toInt()
                    val m = dotMatch.groupValues[2].toInt()
                    val rawYear = dotMatch.groupValues[3].toInt()
                    val y = if (rawYear < 100) 2000 + rawYear else rawYear
                    LocalDate(y, m, d)
                } else {
                    val wordMatch = Regex("""\b(\d{1,2})\s+([а-яА-ЯёЁa-zA-Z]+)\s+(\d{2,4})\b""").find(raw)
                    if (wordMatch != null) {
                        val d = wordMatch.groupValues[1].toInt()
                        val m = parseRussianMonth(wordMatch.groupValues[2])
                        val rawYear = wordMatch.groupValues[3].toInt()
                        val y = if (rawYear < 100) 2000 + rawYear else rawYear
                        LocalDate(y, m, d)
                    } else Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                }
            }
        }
    } catch (_: Exception) {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
}

private fun parseRussianMonth(raw: String): Int {
    val m = raw.lowercase().trimEnd('.')
    return when {
        m.startsWith("янв") -> 1
        m.startsWith("фев") -> 2
        m.startsWith("мар") -> 3
        m.startsWith("апр") -> 4
        m.startsWith("май") || m.startsWith("мае") || m.startsWith("мая") -> 5
        m.startsWith("июн") -> 6
        m.startsWith("июл") -> 7
        m.startsWith("авг") -> 8
        m.startsWith("сен") -> 9
        m.startsWith("окт") -> 10
        m.startsWith("ноя") -> 11
        m.startsWith("дек") -> 12
        else -> 1
    }
}
