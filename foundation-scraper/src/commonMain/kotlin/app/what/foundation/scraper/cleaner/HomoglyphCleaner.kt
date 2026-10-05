package app.what.foundation.scraper.cleaner

object HomoglyphCleaner {
    private val LATIN_TO_CYRILLIC = mapOf(
        'a' to 'а', 'A' to 'А',
        'c' to 'с', 'C' to 'С',
        'e' to 'е', 'E' to 'Е',
        'o' to 'о', 'O' to 'О',
        'p' to 'р', 'P' to 'Р',
        'x' to 'х', 'X' to 'Х',
        'y' to 'у', 'Y' to 'У',
        'k' to 'к', 'K' to 'К',
        'm' to 'м', 'M' to 'М',
        't' to 'т', 'T' to 'Т',
        'b' to 'в', 'B' to 'В',
        'h' to 'н', 'H' to 'Н'
    )

    fun unifyToCyrillic(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(LATIN_TO_CYRILLIC[ch] ?: ch)
        }
        return sb.toString()
    }

    fun cleanPunctuationAndSpaces(text: String): String =
        text.replace('\u00A0', ' ')
            .replace(" ", "")
            .replace("-", "")
            .replace("—", "")
            .replace("–", "")
            .replace(".", "")
            .trim()

    fun normalize(text: String): String =
        cleanPunctuationAndSpaces(unifyToCyrillic(text)).lowercase()
}
