package app.what.foundation.services.auto_update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class ReleaseHighlight(
    val id: String = "",
    val tag: String? = null, // e.g. "NEW", "FEATURE", "FIX"
    val title: String,
    val description: String,
    @SerialName("setting_key") val settingKey: String? = null,
    val settings: List<String> = emptyList(),
    val route: String? = null,
    val icon: String? = null
) {
    fun allSettingKeys(): List<String> {
        if (settings.isNotEmpty()) return settings
        return settingKey?.let { listOf(it) } ?: emptyList()
    }
}

@Serializable
data class ReleaseCategory(
    val category: String = "general",
    @SerialName("category_title") val categoryTitle: String = "Изменения",
    val items: List<String> = emptyList()
)

@Serializable
data class ReleaseNotes(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val version: String,
    @SerialName("version_code") val versionCode: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val title: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    val highlights: List<ReleaseHighlight> = emptyList(),
    val changelog: List<String> = emptyList(),
    val categories: List<ReleaseCategory> = emptyList()
) {
    /**
     * Возвращает все пункты изменений для отображения простым списком (RuStore, fallback).
     */
    fun allChangelogItems(): List<String> {
        if (changelog.isNotEmpty()) return changelog
        return categories.flatMap { it.items }
    }

    fun toJson(): String = json.encodeToString(this)

    /**
     * Сливает текущие заметки о релизе с более старыми заметками.
     * Заголовки, версия и описание берутся из более свежей версии (this),
     * а highlights и changelog объединяются без дубликатов.
     */
    fun mergeWith(older: ReleaseNotes): ReleaseNotes {
        val mergedHighlights = (this.highlights + older.highlights)
            .distinctBy { if (it.id.isNotBlank()) it.id else it.title.trim().lowercase() }

        val mergedChangelog = (this.changelog + older.changelog)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val mergedCategories = if (this.categories.isEmpty() && older.categories.isEmpty()) {
            emptyList()
        } else {
            val allCats = (this.categories + older.categories)
            allCats.groupBy { it.category }.map { (cat, list) ->
                ReleaseCategory(
                    category = cat,
                    categoryTitle = list.first().categoryTitle,
                    items = list.flatMap { it.items }.distinct()
                )
            }
        }

        return this.copy(
            highlights = mergedHighlights,
            changelog = mergedChangelog,
            categories = mergedCategories
        )
    }

    companion object {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun fromJson(string: String): ReleaseNotes? = try {
            json.decodeFromString<ReleaseNotes>(string)
        } catch (_: Exception) {
            null
        }

        /**
         * Объединяет упорядоченный список релизов (от более новых к более старым) в единый ReleaseNotes.
         */
        fun merge(notesList: List<ReleaseNotes>): ReleaseNotes? {
            if (notesList.isEmpty()) return null
            return notesList.drop(1).fold(notesList.first()) { acc, notes ->
                acc.mergeWith(notes)
            }
        }

        /**
         * Умный парсер Markdown-описания релиза GitHub.
         * Удаляет заголовки (#), разделители (---), заголовки категорий и маркеры списков (- / * / •),
         * возвращая чистый список изменений без мусора.
         */
        fun parseMarkdownChangelog(markdown: String): List<String> {
            return markdown.lines()
                .map { it.trim() }
                .filter { line ->
                    if (line.isBlank()) return@filter false
                    // Пропускаем заголовки Markdown: #, ##, ###, ####
                    if (line.startsWith("#")) return@filter false
                    // Пропускаем горизонтальные линии: ---, ***, ___
                    if (line.matches(Regex("^[-*_]{3,}$"))) return@filter false
                    // Пропускаем строки заголовков категорий, заканчивающиеся двоеточием, например "**Что нового:**" или "- **Новости**:"
                    if (line.endsWith(":") && (line.startsWith("**") || line.startsWith("- **"))) return@filter false
                    true
                }
                .map { line ->
                    var cleaned = line
                    // Удаляем маркеры списка в начале: "-", "*", "+", "•"
                    cleaned = cleaned.replaceFirst(Regex("^[-*+•]\\s+"), "")
                    // Удаляем нумерованные списки: "1. ", "2) "
                    cleaned = cleaned.replaceFirst(Regex("^\\d+[.)]\\s+"), "")
                    cleaned.trim()
                }
                .filter { it.isNotBlank() }
        }
    }
}
