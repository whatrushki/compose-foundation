package app.what.foundation.scraper.engine

import app.what.foundation.scraper.cleaner.RoomCleaner
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser

data class XlsxCell(
    val rowIndex: Int,
    val colIndex: Int,
    val value: String
)

data class XlsxRow(
    val index: Int,
    val cells: List<String>
) {
    fun getOrNull(index: Int): String? = cells.getOrNull(index)?.trim()?.ifEmpty { null }
    val isBlank: Boolean get() = cells.isEmpty() || cells.all { it.isBlank() }
}

data class XlsxSheet(
    val name: String,
    val rows: List<XlsxRow>
) {
    fun forEachRowChunk(
        step: Int,
        startRow: Int = 1,
        maxEmptyRows: Int = 5,
        action: (row: XlsxRow, chunkOffset: Int) -> Unit
    ) {
        var emptyCount = 0
        for (i in startRow until rows.size) {
            val r = rows[i]
            if (r.isBlank) {
                emptyCount++
                if (emptyCount >= maxEmptyRows) break
                continue
            }
            emptyCount = 0
            val chunks = (r.cells.size + step - 1) / step
            for (c in 0 until chunks) {
                val startIdx = c * step
                val chunkCells = r.cells.drop(startIdx).take(step)
                if (chunkCells.any { it.isNotBlank() }) {
                    action(XlsxRow(r.index, chunkCells), c)
                }
            }
        }
    }
}

object XlsxExtractor {
    fun parseSheetsFromXmlMap(entries: Map<String, ByteArray>): List<XlsxSheet> {
        val sharedStrings = mutableListOf<String>()
        val sharedStringsBytes = entries["xl/sharedStrings.xml"]
        if (sharedStringsBytes != null) {
            val xml = sharedStringsBytes.decodeToString()
            val doc = Ksoup.parse(html = xml, parser = Parser.xmlParser())
            doc.select("si").forEach { si ->
                val text = si.select("t").joinToString("") { it.text() }
                sharedStrings.add(text)
            }
        }

        val sheetNameMap = mutableMapOf<String, String>()
        val workbookBytes = entries["xl/workbook.xml"]
        if (workbookBytes != null) {
            val xml = workbookBytes.decodeToString()
            val doc = Ksoup.parse(html = xml, parser = Parser.xmlParser())
            var index = 1
            doc.select("sheet").forEach { sheetElem ->
                val name = sheetElem.attr("name")
                val sheetId = sheetElem.attr("sheetId").ifEmpty { index.toString() }
                sheetNameMap["sheet$sheetId.xml"] = name
                sheetNameMap["sheet$index.xml"] = name
                index++
            }
        }

        val resultSheets = mutableListOf<XlsxSheet>()

        entries.filter { (key, _) -> key.startsWith("xl/worksheets/sheet") && key.endsWith(".xml") }
            .forEach { (key, bytes) ->
                val fileName = key.substringAfterLast("/")
                val sheetName = sheetNameMap[fileName] ?: fileName.removeSuffix(".xml")
                val xml = bytes.decodeToString()
                val doc = Ksoup.parse(html = xml, parser = Parser.xmlParser())
                val rowsList = mutableListOf<XlsxRow>()

                doc.select("row").forEachIndexed { rIdx, rowElem ->
                    val cellsMap = mutableMapOf<Int, String>()
                    rowElem.select("c").forEach { cElem ->
                        val r = cElem.attr("r")
                        val colLetters = r.takeWhile { it.isLetter() }.uppercase()
                        val colIndex = if (colLetters.isNotEmpty()) {
                            colLetters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
                        } else -1
                        val type = cElem.attr("t")
                        val rawVal = cElem.selectFirst("v")?.text()?.trim().orEmpty()
                        val cellVal = when (type) {
                            "s" -> {
                                val sIdx = rawVal.toIntOrNull()
                                if (sIdx != null && sIdx in sharedStrings.indices) sharedStrings[sIdx] else rawVal
                            }
                            "inlineStr" -> cElem.select("t").joinToString("") { it.text() }
                            else -> RoomCleaner.cleanFloatRoom(rawVal)
                        }
                        if (colIndex >= 0) {
                            cellsMap[colIndex] = cellVal
                        }
                    }
                    val maxCol = cellsMap.keys.maxOrNull() ?: -1
                    val rowCells = if (maxCol >= 0) {
                        (0..maxCol).map { cellsMap[it] ?: "" }
                    } else emptyList()
                    rowsList.add(XlsxRow(rIdx, rowCells))
                }
                resultSheets.add(XlsxSheet(sheetName, rowsList))
            }

        return resultSheets
    }
}
