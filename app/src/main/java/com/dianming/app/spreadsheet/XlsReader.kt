package com.dianming.app.spreadsheet

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 旧版 Excel(.xls, BIFF8/BIFF5) 解析器（纯 Kotlin 实现）。
 *
 * .xls 是复合二进制文档：文件头 + 一串"记录"(record)。
 * 关键记录：
 *  - BOUNDSHEET(0x0085)  工作表名 + 该表在文件中的偏移
 *  - SST(0x00FC)         共享字符串表（可跨多个 CONTINUE(0x003C) 记录）
 *  - XF(0x00E0)          单元格样式，含数字格式索引
 *  - LABELSST/LABEL/RK/NUMBER/MULRK/MULBLANK/FORMULA  单元格数据
 */
object XlsReader {

    private const val REC_EOF = 0x000A
    private const val REC_BOUNDSHEET = 0x0085
    private const val REC_SST = 0x00FC
    private const val REC_CONTINUE = 0x003C
    private const val REC_XF = 0x00E0
    private const val REC_FORMAT = 0x041E
    private const val REC_FILEPASS = 0x002F
    private const val REC_LABEL = 0x0204
    private const val REC_LABELSST = 0x00FD
    private const val REC_RK = 0x027E
    private const val REC_MULRK = 0x00BD
    private const val REC_NUMBER = 0x0203
    private const val REC_MULBLANK = 0x00BE
    private const val REC_FORMULA = 0x0006
    private const val REC_STRING = 0x0207
    private const val REC_BOOLERR = 0x0205

    private val BUILTIN_DATE_FORMATS = setOf(
        14, 15, 16, 17, 18, 19, 20, 21, 22,
        27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58
    )

    fun read(input: InputStream, sourceName: String = ""): SheetTable {
        val raw = input.readBytes()
        if (raw.size < 8) throw IllegalArgumentException("文件太小，不是有效的 xls")

        // .xls 通常是 OLE2 复合文档，真正的 BIFF 记录在 Workbook 流里
        val data = if (SpreadsheetReader.isOle2(raw)) {
            CompoundFile(raw).stream("Workbook", "Book")
                ?: throw IllegalArgumentException("xls 中找不到 Workbook 数据流，可能不是有效的 xls")
        } else {
            raw
        }

        var pos = 0
        val boundsheets = ArrayList<Pair<String, Int>>()
        val sstChunks = ArrayList<ByteArray>()
        val xfFormats = ArrayList<Int>()
        val formatCodes = HashMap<Int, String>()

        // ---------- 全局子流 ----------
        while (pos + 4 <= data.size) {
            val id = u16(data, pos)
            val len = u16(data, pos + 2)
            val bodyStart = pos + 4
            val bodyEnd = bodyStart + len
            if (bodyEnd > data.size) break
            when (id) {
                REC_BOUNDSHEET -> {
                    val lbPlyPos = readU32(data, bodyStart)
                    val name = readShortString(data, bodyStart + 8)
                    boundsheets.add(name to lbPlyPos.toInt())
                }
                REC_SST -> sstChunks.add(data.copyOfRange(bodyStart, bodyEnd))
                REC_CONTINUE -> if (sstChunks.isNotEmpty()) {
                    sstChunks.add(data.copyOfRange(bodyStart, bodyEnd))
                }
                REC_XF -> if (len >= 4) xfFormats.add(u16(data, bodyStart + 2))
                REC_FORMAT -> if (len >= 2) {
                    val ifmt = u16(data, bodyStart)
                    formatCodes[ifmt] = readSimpleUnicodeString(data, bodyStart + 2)
                }
                REC_FILEPASS -> throw IllegalArgumentException("该 xls 已加密，无法读取")
                REC_EOF -> {
                    pos = bodyEnd
                    break
                }
            }
            pos = bodyEnd
        }

        val sharedStrings = if (sstChunks.isNotEmpty()) parseSst(sstChunks) else emptyList()
        val dateStyles = BooleanArray(xfFormats.size) { index ->
            val ifmt = xfFormats[index]
            if (ifmt in BUILTIN_DATE_FORMATS) return@BooleanArray true
            val code = formatCodes[ifmt] ?: return@BooleanArray false
            XlsxReader.isDateFormatCode(code)
        }

        if (boundsheets.isEmpty()) throw IllegalArgumentException("xls 中找不到工作表")

        // ---------- 逐个解析工作表，取内容最多的 ----------
        var bestGrid: List<List<String>> = emptyList()
        var bestName: String? = null
        for ((name, offset) in boundsheets) {
            val grid = parseSheet(data, offset, sharedStrings, dateStyles)
            if (grid.size > bestGrid.size) {
                bestGrid = grid
                bestName = name
            }
        }
        return SheetTable(bestGrid, bestName, sourceName)
    }

    // ---------------------------------------------------------------- 工作表

    private fun parseSheet(
        data: ByteArray,
        startOffset: Int,
        sharedStrings: List<String>,
        dateStyles: BooleanArray
    ): List<List<String>> {
        val grid = HashMap<Int, HashMap<Int, String>>()
        var pos = startOffset
        var pendingFormula: Triple<Int, Int, Int>? = null // row, col, xf

        while (pos + 4 <= data.size) {
            val id = u16(data, pos)
            val len = u16(data, pos + 2)
            val bodyStart = pos + 4
            val bodyEnd = bodyStart + len
            if (bodyEnd > data.size) break

            when (id) {
                REC_LABELSST -> {
                    if (len >= 10) {
                        val row = u16(data, bodyStart)
                        val col = u16(data, bodyStart + 2)
                        val xf = u16(data, bodyStart + 4)
                        val isst = readU32(data, bodyStart + 6).toInt()
                        grid.getOrPut(row) { HashMap() }[col] = sharedStrings.getOrNull(isst) ?: ""
                    }
                }
                REC_LABEL -> {
                    if (len >= 7) {
                        val row = u16(data, bodyStart)
                        val col = u16(data, bodyStart + 2)
                        val xf = u16(data, bodyStart + 4)
                        val (text, _) = readUnicodeString(data, bodyStart + 6, data.size)
                        grid.getOrPut(row) { HashMap() }[col] = text
                    }
                }
                REC_RK -> {
                    if (len >= 10) {
                        val row = u16(data, bodyStart)
                        val col = u16(data, bodyStart + 2)
                        val xf = u16(data, bodyStart + 4)
                        val rk = readU32(data, bodyStart + 6).toInt()
                        grid.getOrPut(row) { HashMap() }[col] =
                            formatNumeric(decodeRk(rk), xf, dateStyles)
                    }
                }
                REC_MULRK -> {
                    if (len >= 6) {
                        val row = u16(data, bodyStart)
                        val colFirst = u16(data, bodyStart + 2)
                        val count = (len - 6) / 6
                        for (i in 0 until count) {
                            val off = bodyStart + 4 + i * 6
                            val xf = u16(data, off)
                            val rk = readU32(data, off + 2).toInt()
                            grid.getOrPut(row) { HashMap() }[colFirst + i] =
                                formatNumeric(decodeRk(rk), xf, dateStyles)
                        }
                    }
                }
                REC_NUMBER -> {
                    if (len >= 14) {
                        val row = u16(data, bodyStart)
                        val col = u16(data, bodyStart + 2)
                        val xf = u16(data, bodyStart + 4)
                        val d = ByteBuffer.wrap(data, bodyStart + 6, 8)
                            .order(ByteOrder.LITTLE_ENDIAN).getDouble()
                        grid.getOrPut(row) { HashMap() }[col] = formatNumeric(d, xf, dateStyles)
                    }
                }
                REC_MULBLANK -> {
                    // 空单元格，无需处理
                }
                REC_BOOLERR -> {
                    if (len >= 8) {
                        val row = u16(data, bodyStart)
                        val col = u16(data, bodyStart + 2)
                        val isError = data[bodyStart + 6].toInt() != 0
                        val value = data[bodyStart + 5].toInt() and 0xFF
                        val text = if (isError) "" else if (value == 0) "FALSE" else "TRUE"
                        grid.getOrPut(row) { HashMap() }[col] = text
                    }
                }
                REC_FORMULA -> {
                    if (len >= 14) {
                        val row = u16(data, bodyStart)
                        val col = u16(data, bodyStart + 2)
                        val xf = u16(data, bodyStart + 4)
                        val special = (data[bodyStart + 12].toInt() and 0xFF) == 0xFF &&
                            (data[bodyStart + 13].toInt() and 0xFF) == 0xFF
                        if (!special) {
                            val d = ByteBuffer.wrap(data, bodyStart + 6, 8)
                                .order(ByteOrder.LITTLE_ENDIAN).getDouble()
                            grid.getOrPut(row) { HashMap() }[col] = formatNumeric(d, xf, dateStyles)
                        } else {
                            pendingFormula = Triple(row, col, xf)
                        }
                    }
                }
                REC_STRING -> {
                    val pending = pendingFormula
                    if (pending != null) {
                        val (text, _) = readUnicodeString(data, bodyStart, data.size)
                        grid.getOrPut(pending.first) { HashMap() }[pending.second] = text
                        pendingFormula = null
                    }
                }
                REC_EOF -> {
                    pos = bodyEnd
                    break
                }
            }
            pos = bodyEnd
        }

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

    private fun formatNumeric(value: Double, xf: Int, dateStyles: BooleanArray): String {
        if (xf in dateStyles.indices && dateStyles[xf]) return XlsxReader.formatSerial(value)
        return XlsxReader.formatNumber(value)
    }

    /**
     * 解码 RK 数值（32 位）：
     *  bit 0 (0x01) = fX100  为 1 时，值需要除以 100
     *  bit 1 (0x02) = fInt   为 1 时，高 30 位是整数；为 0 时，高 30 位是 IEEE double 的高位
     */
    fun decodeRk(rk: Int): Double {
        val value = if (rk and 0x02 != 0) {
            // 高 30 位为有符号整数，算术右移即可得到原值
            (rk shr 2).toDouble()
        } else {
            // 高 30 位是 double 的高 30 位（bit31 为符号位），低位补 0
            java.lang.Double.longBitsToDouble((rk.toLong() and 0xFFFFFFFC) shl 32)
        }
        return if (rk and 0x01 != 0) value / 100.0 else value
    }

    // ---------------------------------------------------------------- 字符串

    /** 读取记录内部（不跨记录）的 BIFF8 Unicode 字符串 */
    private fun readSimpleUnicodeString(data: ByteArray, offset: Int): String {
        if (offset + 3 > data.size) return ""
        return try {
            readUnicodeString(data, offset, data.size).first
        } catch (_: Exception) {
            ""
        }
    }

    /** BOUNDSHEET 等记录里的"短字符串"：cch(1) + grbit(1) + 字符 */
    private fun readShortString(data: ByteArray, offset: Int): String {
        if (offset + 2 > data.size) return ""
        val cch = data[offset].toInt() and 0xFF
        val highByte = (data[offset + 1].toInt() and 0x01) != 0
        val sb = StringBuilder()
        var p = offset + 2
        for (i in 0 until cch) {
            if (highByte) {
                if (p + 2 > data.size) break
                sb.append(((data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8)).toChar())
                p += 2
            } else {
                if (p >= data.size) break
                sb.append((data[p].toInt() and 0xFF).toChar())
                p++
            }
        }
        return sb.toString()
    }

    /**
     * 读取 BIFF8 Unicode 字符串：cch(2) + grbit(1) + [cRun(2)] + [cbExt(4)] + 字符数据 + [富文本] + [扩展]
     * 返回 (文本, 结束位置)
     */
    private fun readUnicodeString(data: ByteArray, offset: Int, limit: Int): Pair<String, Int> {
        var pos = offset
        if (pos + 3 > limit) return "" to limit
        val cch = u16(data, pos); pos += 2
        val grbit = data[pos].toInt() and 0xFF; pos += 1
        var highByte = (grbit and 0x01) != 0
        val extSt = (grbit and 0x04) != 0
        val richSt = (grbit and 0x08) != 0
        var cRun = 0
        var cbExt = 0
        if (richSt) {
            if (pos + 2 > limit) return "" to limit
            cRun = u16(data, pos); pos += 2
        }
        if (extSt) {
            if (pos + 4 > limit) return "" to limit
            cbExt = readU32(data, pos).toInt(); pos += 4
        }
        val sb = StringBuilder()
        var remaining = cch
        while (remaining > 0 && pos < limit) {
            if (highByte) {
                if (pos + 2 > limit) break
                sb.append(((data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)).toChar())
                pos += 2
            } else {
                sb.append((data[pos].toInt() and 0xFF).toChar())
                pos += 1
            }
            remaining--
        }
        if (cRun > 0) pos += cRun * 4
        if (cbExt > 0) pos += cbExt
        return sb.toString() to pos
    }

    /**
     * 解析 SST（共享字符串表）。SST 可能被拆到多个 CONTINUE 记录里，
     * 字符串跨记录时，新记录开头会重复一个 option byte（只有 fHighByte 有效）。
     */
    private fun parseSst(chunks: List<ByteArray>): List<String> {
        if (chunks.isEmpty()) return emptyList()
        var total = 0
        val boundaries = ArrayList<Int>()
        var offset = 0
        for ((i, chunk) in chunks.withIndex()) {
            if (i > 0) boundaries.add(offset)
            offset += chunk.size
        }
        val data = ByteArray(offset)
        var p = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, data, p, chunk.size)
            p += chunk.size
        }
        if (data.size < 8) return emptyList()
        total = readU32(data, 0).toInt()
        val unique = readU32(data, 4).toInt()
        val strings = ArrayList<String>(unique.coerceIn(0, 100000))
        var pos = 8
        val sortedBoundaries = boundaries.sorted()
        var count = 0
        while (count < total && count < unique && pos + 3 <= data.size) {
            val cch = u16(data, pos); pos += 2
            var grbit = data[pos].toInt() and 0xFF; pos += 1
            var highByte = (grbit and 0x01) != 0
            val extSt = (grbit and 0x04) != 0
            val richSt = (grbit and 0x08) != 0
            var cRun = 0
            var cbExt = 0
            if (richSt) {
                if (pos + 2 > data.size) break
                cRun = u16(data, pos); pos += 2
            }
            if (extSt) {
                if (pos + 4 > data.size) break
                cbExt = readU32(data, pos).toInt(); pos += 4
            }
            // 读取字符数据（可能跨 CONTINUE 边界）
            val sb = StringBuilder()
            var remaining = cch
            var currentBoundary = sortedBoundaries.firstOrNull { it > pos }
            while (remaining > 0) {
                val chunkEnd = currentBoundary ?: data.size
                val bytesLeft = chunkEnd - pos
                if (bytesLeft <= 0) {
                    if (chunkEnd >= data.size) break
                    pos = chunkEnd
                    highByte = (data[pos].toInt() and 0x01) != 0
                    pos++
                    currentBoundary = sortedBoundaries.firstOrNull { it > pos }
                    continue
                }
                val charsHere = if (highByte) bytesLeft / 2 else bytesLeft
                if (charsHere <= 0) {
                    if (chunkEnd >= data.size) break
                    pos = chunkEnd
                    highByte = (data[pos].toInt() and 0x01) != 0
                    pos++
                    currentBoundary = sortedBoundaries.firstOrNull { it > pos }
                    continue
                }
                val take = minOf(remaining, charsHere)
                for (i in 0 until take) {
                    if (highByte) {
                        sb.append(
                            ((data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)).toChar()
                        )
                        pos += 2
                    } else {
                        sb.append((data[pos].toInt() and 0xFF).toChar())
                        pos += 1
                    }
                }
                remaining -= take
                if (remaining > 0) {
                    pos = chunkEnd
                    if (chunkEnd >= data.size) break
                    highByte = (data[pos].toInt() and 0x01) != 0
                    pos++
                    currentBoundary = sortedBoundaries.firstOrNull { it > pos }
                }
            }
            if (cRun > 0) pos += cRun * 4
            if (cbExt > 0) pos += cbExt
            strings.add(sb.toString())
            count++
        }
        return strings
    }

    // ---------------------------------------------------------------- 基础工具

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(data: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((data[offset + i].toLong() and 0xFF) shl (8 * i))
        return v
    }
}
