package com.dianming.app.spreadsheet

import java.io.InputStream
import java.nio.charset.Charset

/**
 * CSV / TSV 解析器。
 * 支持 UTF-8 / UTF-8-BOM / UTF-16 / GBK(中文 Excel 常见导出编码)。
 */
object CsvReader {

    private val DELIMITERS = charArrayOf(',', '\t', ';', '|')

    fun read(input: InputStream, sourceName: String = ""): SheetTable {
        val bytes = input.readBytes()
        val text = decode(bytes)
        val rows = parse(text)
        return SheetTable(rows, null, sourceName)
    }

    fun decode(bytes: ByteArray): String {
        // BOM 优先
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16BE"))
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        // UTF-8 解码出现替换字符说明不是 UTF-8，国内 Excel 常导出 GBK
        return if (utf8.contains('\uFFFD')) {
            gbkOrDefault(bytes, utf8)
        } else {
            utf8
        }
    }

    private fun gbkOrDefault(bytes: ByteArray, fallback: String): String = try {
        val gbk = Charset.forName("GBK")
        val s = String(bytes, gbk)
        if (s.count { it == '\uFFFD' } < fallback.count { it == '\uFFFD' }) s else fallback
    } catch (_: Exception) {
        fallback
    }

    /** 自动识别分隔符 */
    fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        var best = ','
        var bestCount = -1
        for (d in DELIMITERS) {
            val count = firstLine.count { it == d }
            if (count > bestCount) {
                bestCount = count
                best = d
            }
        }
        return if (bestCount <= 0) ',' else best
    }

    fun parse(text: String): List<List<String>> {
        val delimiter = detectDelimiter(text)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length

        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' -> {
                        if (i + 1 < n && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    }
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> {
                        row.add(field.toString())
                        field.setLength(0)
                    }
                    '\r' -> { /* 忽略，等 \n 收尾 */ }
                    '\n' -> {
                        row.add(field.toString())
                        field.setLength(0)
                        rows.add(row)
                        row = mutableListOf()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        // 去掉尾部全空行
        while (rows.isNotEmpty() && rows.last().all { it.isBlank() }) rows.removeAt(rows.size - 1)
        return rows
    }
}
