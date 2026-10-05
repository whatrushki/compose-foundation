package app.what.foundation.scraper.cleaner

object TeacherNameCleaner {
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val ID_SUFFIX_REGEX = Regex("""\s*\[\d+\]""")

    /**
     * Срезает суффиксы ID вроде "Иванов И.И. [412]" -> "Иванов И.И."
     */
    fun stripIdSuffix(name: String): String =
        name.replace(ID_SUFFIX_REGEX, "").trim()

    /**
     * Извлекает фамилию и инициалы для нечеткого сравнения:
     * "Смолянинова В.А." -> "смоляниновава"
     * "Смолянинова Валентина Анатольевна" -> "смоляниновава"
     */
    fun extractSurnameAndInitials(name: String): String? {
        val clean = stripIdSuffix(name).replace('\u00A0', ' ').trim()
        val parts = clean.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val surname = HomoglyphCleaner.normalize(parts[0])
        if (parts.size == 1) return surname

        val initials = parts.drop(1).mapNotNull { part ->
            val p = part.replace(".", "").trim()
            if (p.isNotEmpty()) HomoglyphCleaner.normalize(p.first().toString()) else null
        }
        return surname + initials.joinToString("")
    }

    /**
     * Проверяет, является ли имя одним и тем же преподавателем
     */
    fun isSameTeacher(teacher1: String?, teacher2: String?): Boolean {
        if (teacher1.isNullOrBlank() || teacher2.isNullOrBlank()) return false
        val key1 = extractSurnameAndInitials(teacher1) ?: HomoglyphCleaner.normalize(teacher1)
        val key2 = extractSurnameAndInitials(teacher2) ?: HomoglyphCleaner.normalize(teacher2)
        return key1 == key2 || key1.startsWith(key2) || key2.startsWith(key1)
    }
}
