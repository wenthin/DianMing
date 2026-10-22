package com.dianming.app.spreadsheet

import java.io.ByteArrayInputStream

/**
 * 表格文件统一入口：自动识别 xlsx / xls / csv 格式并解析。
 */
object SpreadsheetReader {

    fun read(bytes: ByteArray, sourceName: String = ""): SheetTable {
        if (bytes.isEmpty()) throw IllegalArgumentException("文件是空的")
        val ext = sourceName.substringAfterLast('.', "").lowercase()

        // 先按扩展名，再按文件头魔数兜底
        return when {
            ext == "xlsx" || ext == "xlsm" || ext == "xltx" -> XlsxReader.read(ByteArrayInputStream(bytes), sourceName)
            ext == "xls" || ext == "xlt" -> XlsReader.read(ByteArrayInputStream(bytes), sourceName)
            ext == "csv" || ext == "txt" || ext == "tsv" -> CsvReader.read(ByteArrayInputStream(bytes), sourceName)
            isZip(bytes) -> XlsxReader.read(ByteArrayInputStream(bytes), sourceName)
            isOle2(bytes) -> XlsReader.read(ByteArrayInputStream(bytes), sourceName)
            else -> CsvReader.read(ByteArrayInputStream(bytes), sourceName)
        }
    }

    /** 是否是 zip（xlsx） */
    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())

    /** 是否是 OLE2 复合文档（xls） */
    fun isOle2(bytes: ByteArray): Boolean =
        bytes.size >= 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
}
