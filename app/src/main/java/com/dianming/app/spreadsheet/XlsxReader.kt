package com.dianming.app.spreadsheet

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * XLSX 解析器（纯 Kotlin 实现，不依赖 POI，避免包体积膨胀）。
 *
 * xlsx 本质是一个 zip，里面是若干 XML：
 *  - xl/sharedStrings.xml  共享字符串表
 *  - xl/styles.xml         数字格式（用于识别日期）
 *  - xl/workbook.xml       工作表清单
 *  - xl/worksheets/sheetN.xml 单元格数据
 */
object XlsxReader {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** 内置日期型 numFmtId */
    private val BUILTIN_DATE_FORMATS = setOf(
        14, 15, 16, 17, 18, 19, 20, 21, 22,
        27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58
    )

    fun read(input: InputStream, sourceName: String = ""): SheetTable {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) {
                    val out = java.io.ByteArrayOutputStream()
                    zip.copyTo(out)
                    entries[e.name] = out.toByteArray()
                }
                zip.closeEntry()
            }
        }
        if (entries.isEmpty()) throw IllegalArgumentException("文件不是有效的 xlsx（压缩包为空）")

        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val dateStyles = parseDateStyles(entries["xl/styles.xml"])
        val (sheetName, sheetPath) = pickSheet(entries)
        val sheetXml = entries[sheetPath]
            ?: throw IllegalArgumentException("xlsx 中找不到工作表数据")
        val grid = parseSheet(sheetXml, sharedStrings, dateStyles)
        return SheetTable(grid, sheetName, sourceName)
    }

    // ---------------------------------------------------------------- 工作表定位

    private fun pickSheet(entries: Map<String, ByteArray>): Pair<String?, String> {
        val workbook = entries["xl/workbook.xml"]
        if (workbook != null) {
            val rels = parseRels(entries["xl/_rels/workbook.xml.rels"])
            val sheetEntries = ArrayList<Pair<String, String>>()
            parseSax(workbook) { sheetName, rid -> sheetEntries.add(sheetName to rid) }
            for ((name, rid) in sheetEntries) {
                val target = rels[rid] ?: continue
                val path = normalizePath(target)
                if (entries.containsKey(path)) return name to path
            }
        }
        // 兜底：按文件名排序取第一个 sheet
        val candidates = entries.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.sorted()
        if (candidates.isNotEmpty()) return null to candidates.first()
        throw IllegalArgumentException("xlsx 中找不到工作表")
    }

    private fun normalizePath(target: String): String {
        val t = target.trim()
        return when {
            t.startsWith("/") -> t.removePrefix("/")
            t.startsWith("xl/") -> t
            else -> "xl/$t"
        }
    }

    private fun parseRels(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val result = HashMap<String, String>()
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                if (qName == "Relationship" || localName == "Relationship") {
                    val id = attrs?.getValue("Id")
                    val tgt = attrs?.getValue("Target")
                    if (id != null && tgt != null) result[id] = tgt
                }
            }
        }
        runSax(bytes, handler)
        return result
    }

    // ---------------------------------------------------------------- sharedStrings

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val strings = ArrayList<String>()
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var skipPhonetic = false
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                when (qName) {
                    "si" -> {
                        inSi = true
                        sb.setLength(0)
                        skipPhonetic = false
                    }
                    "rPh" -> skipPhonetic = true
                    "t" -> if (inSi && !skipPhonetic) inT = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT) sb.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (qName) {
                    "t" -> inT = false
                    "rPh" -> skipPhonetic = false
                    "si" -> {
                        if (inSi) strings.add(sb.toString())
                        inSi = false
                    }
                }
            }
        }
        runSax(bytes, handler)
        return strings
    }

    // ---------------------------------------------------------------- styles

    /** 返回 xfIndex -> 是否日期格式 */
    private fun parseDateStyles(bytes: ByteArray?): BooleanArray {
        if (bytes == null) return BooleanArray(0)
        val customFormats = HashMap<Int, String>()
        val cellXfIds = ArrayList<Int>()
        var inCellXfs = false
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                when (qName) {
                    "numFmt" -> {
                        val id = attrs?.getValue("numFmtId")?.toIntOrNull()
                        val code = attrs?.getValue("formatCode")
                        if (id != null && code != null) customFormats[id] = code
                    }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) {
                        cellXfIds.add(attrs?.getValue("numFmtId")?.toIntOrNull() ?: 0)
                    }
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (qName == "cellXfs") inCellXfs = false
            }
        }
        runSax(bytes, handler)
        return BooleanArray(cellXfIds.size) { index ->
            val fmtId = cellXfIds[index]
            if (fmtId in BUILTIN_DATE_FORMATS) return@BooleanArray true
            val code = customFormats[fmtId] ?: return@BooleanArray false
            isDateFormatCode(code)
        }
    }

    /** 通过格式代码判断是否为日期/时间格式 */
    fun isDateFormatCode(code: String): Boolean {
        val cleaned = StringBuilder()
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '"' -> {
                    // 跳过引号里的字面量
                    i++
                    while (i < code.length && code[i] != '"') i++
                }
                c == '[' -> {
                    while (i < code.length && code[i] != ']') i++
                }
                c == '\\' -> i++
                else -> cleaned.append(c.lowercaseChar())
            }
            i++
        }
        val s = cleaned.toString()
        if (s.contains('y') || s.contains('d')) return true
        if (s.contains('h')) return true
        // 只有 m 时需排除 "mm:ss" 这种纯时间格式之外的情况：m 表示月
        if (s.contains('m') && !s.contains("mm:ss")) return true
        return false
    }

    // ---------------------------------------------------------------- sheet

    private fun parseSheet(
        bytes: ByteArray,
        sharedStrings: List<String>,
        dateStyles: BooleanArray
    ): List<List<String>> {
        val grid = HashMap<Int, HashMap<Int, String>>()
        var currentRow = 0
        var currentCol = 0
        var cellType = ""
        var styleIndex = 0
        var inValue = false
        var inInlineText = false
        val value = StringBuilder()

        fun flushCell() {
            val raw = value.toString()
            val text = when (cellType) {
                "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                "inlineStr", "str" -> raw
                "b" -> if (raw == "1") "TRUE" else "FALSE"
                "e" -> ""
                "d" -> raw
                else -> {
                    val num = raw.toDoubleOrNull()
                    if (num == null) raw
                    else if (styleIndex < dateStyles.size && dateStyles[styleIndex]) formatSerial(num)
                    else formatNumber(num)
                }
            }
            grid.getOrPut(currentRow) { HashMap() }[currentCol] = text
            value.setLength(0)
        }

        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                when (qName) {
                    "row" -> {
                        // OOXML 的行号从 1 开始，这里统一转成从 0 开始
                        val r = attrs?.getValue("r")?.toIntOrNull()
                        currentRow = if (r != null) (r - 1).coerceAtLeast(0) else currentRow + 1
                    }
                    "c" -> {
                        cellType = attrs?.getValue("t") ?: "n"
                        styleIndex = attrs?.getValue("s")?.toIntOrNull() ?: 0
                        val ref = attrs?.getValue("r")
                        currentCol = if (ref != null) columnIndexOf(ref) else currentCol + 1
                        value.setLength(0)
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr") inInlineText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue || inInlineText) value.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (qName) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> flushCell()
                }
            }
        }
        runSax(bytes, handler)

        if (grid.isEmpty()) return emptyList()
        val maxRow = grid.keys.max()
        val maxCol = grid.values.maxOf { it.keys.max() }
        val result = ArrayList<List<String>>(maxRow + 1)
        for (r in 0..maxRow) {
            val rowMap = grid[r]
            val row = ArrayList<String>(maxCol + 1)
            for (c in 0..maxCol) row.add(rowMap?.get(c) ?: "")
            result.add(row)
        }
        return result
    }

    /** "BC12" -> 54 (0-based) */
    fun columnIndexOf(ref: String): Int {
        var index = 0
        for (ch in ref) {
            if (ch.isLetter()) {
                index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else {
                break
            }
        }
        return (index - 1).coerceAtLeast(0)
    }

    fun formatNumber(value: Double): String {
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
            return value.toLong().toString()
        }
        return try {
            BigDecimal(value, MathContext(15)).stripTrailingZeros().toPlainString()
        } catch (_: Exception) {
            value.toString()
        }
    }

    fun formatSerial(serial: Double): String {
        val base = LocalDateTime.of(1899, 12, 30, 0, 0, 0)
        val days = kotlin.math.floor(serial).toLong()
        var millis = ((serial - kotlin.math.floor(serial)) * 86_400_000.0).toLong()
        if (millis >= 86_400_000L) millis = 86_399_999L
        val dt = base.plusDays(days).plusNanos(millis * 1_000_000L)
        return when {
            serial < 1.0 -> TIME_FORMATTER.format(dt)
            dt.hour == 0 && dt.minute == 0 && dt.second == 0 -> DATE_FORMATTER.format(dt)
            else -> DATETIME_FORMATTER.format(dt)
        }
    }

    // ---------------------------------------------------------------- sax 工具

    private fun runSax(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        try {
            factory.newSAXParser().parse(InputSource(ByteArrayInputStream(bytes)), handler)
        } catch (e: Exception) {
            throw IllegalArgumentException("解析 XML 失败: ${e.message}", e)
        }
    }

    private fun parseSax(bytes: ByteArray, onSheet: (name: String, rid: String) -> Unit) {
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                if (qName == "sheet") {
                    val name = attrs?.getValue("name") ?: ""
                    val rid = attrs?.getValue("r:id")
                        ?: attrs?.getValue("id")
                        ?: ""
                    onSheet(name, rid)
                }
            }
        }
        runSax(bytes, handler)
    }
}
