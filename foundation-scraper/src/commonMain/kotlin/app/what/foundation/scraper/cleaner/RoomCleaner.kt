package app.what.foundation.scraper.cleaner

data class ParsedRoomInfo(
    val room: String,
    val building: String = "",
    val onlineUrl: String? = null
)

object RoomCleaner {
    /**
     * Очищает артефакты форматирования чисел из Excel ("204.0" -> "204")
     */
    fun cleanFloatRoom(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.endsWith(".0")) {
            return trimmed.removeSuffix(".0")
        }
        return try {
            trimmed.toFloat().toInt().toString()
        } catch (_: Exception) {
            trimmed
        }
    }

    /**
     * Парсит аудиторию и корпус по формату "204/1" (комната 204, корпус 1)
     */
    fun parseRoomSlashBuilding(raw: String, defaultBuilding: String = "1"): ParsedRoomInfo {
        val clean = cleanFloatRoom(raw)
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            return ParsedRoomInfo(room = "Дистант", building = "", onlineUrl = clean)
        }
        val parts = clean.split("/")
        return if (parts.size >= 2) {
            ParsedRoomInfo(room = parts[0].trim(), building = parts[1].trim())
        } else {
            ParsedRoomInfo(room = clean, building = defaultBuilding)
        }
    }

    /**
     * Парсит аудиторию по формату "8-301" (корпус 8, комната 301)
     */
    fun parseBuildingDashRoom(raw: String, defaultBuilding: String = "Главный"): ParsedRoomInfo {
        val clean = cleanFloatRoom(raw)
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            return ParsedRoomInfo(room = "Дистант", building = "", onlineUrl = clean)
        }
        val parts = clean.split("-")
        return if (parts.size >= 2) {
            ParsedRoomInfo(building = parts[0].trim(), room = parts[1].trim())
        } else {
            ParsedRoomInfo(building = defaultBuilding, room = clean)
        }
    }

    /**
     * Парсит спецсимволы в начале аудитории (как в РГЭУ РИНХ: '*' -> корпус 2, '#' -> корпус 3)
     */
    fun parsePrefixBuilding(
        raw: String,
        prefixMap: Map<Char, String> = mapOf('*' to "2", '#' to "3", '&' to "4", 'д' to "Д"),
        defaultBuilding: String = "1"
    ): ParsedRoomInfo {
        val clean = raw.trim()
        val firstChar = clean.firstOrNull() ?: return ParsedRoomInfo(room = "-")
        val building = prefixMap[firstChar] ?: defaultBuilding
        val room = if (prefixMap.containsKey(firstChar)) clean.drop(1).trim() else clean
        return ParsedRoomInfo(room = room, building = building)
    }
}
