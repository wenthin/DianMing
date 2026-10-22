package com.dianming.app.spreadsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsheetReaderTest {

    private fun resource(name: String): ByteArray =
        SpreadsheetReaderTest::class.java.classLoader!!.getResourceAsStream(name)!!.readBytes()

    // ------------------------------------------------------------ xlsx

    @Test
    fun `reads xlsx content`() {
        val table = SpreadsheetReader.read(resource("roster.xlsx"), "roster.xlsx")
        assertEquals(7, table.columnCount)
        assertEquals(6, table.rowCount)
        assertEquals("学号", table.headerOf(0))
        assertEquals("姓名", table.headerOf(1))
        assertEquals("手机", table.headerOf(3))
        assertEquals("张三", table.cell(1, 1))
        assertEquals("李四", table.cell(2, 1))
        assertEquals("孙七", table.cell(5, 1))
        assertEquals("13800138000", table.cell(1, 3))
        assertEquals("", table.cell(5, 3))
        assertEquals("88", table.cell(1, 5))
    }

    @Test
    fun `reads xlsx date column as formatted date`() {
        val table = SpreadsheetReader.read(resource("roster.xlsx"), "roster.xlsx")
        assertEquals("2023-09-01", table.cell(1, 4))
        assertEquals("2023-09-03", table.cell(5, 4))
    }

    @Test
    fun `reads xlsx with a title row above the header`() {
        val table = SpreadsheetReader.read(resource("roster_with_title.xlsx"), "roster_with_title.xlsx")
        assertEquals(7, table.columnCount)
        assertEquals("XX中学高一新生名单", table.cell(0, 0))
        assertEquals("姓名", table.cell(1, 1))
        assertEquals("张三", table.cell(2, 1))
    }

    // ------------------------------------------------------------ xls

    @Test
    fun `reads legacy xls content`() {
        val table = SpreadsheetReader.read(resource("roster.xls"), "roster.xls")
        assertEquals(7, table.columnCount)
        assertEquals(6, table.rowCount)
        assertEquals("学号", table.headerOf(0))
        assertEquals("姓名", table.headerOf(1))
        assertEquals("张三", table.cell(1, 1))
        assertEquals("王五", table.cell(3, 1))
        assertEquals("13800138000", table.cell(1, 3))
        assertEquals("95", table.cell(2, 5))
    }

    @Test
    fun `reads legacy xls date column`() {
        val table = SpreadsheetReader.read(resource("roster.xls"), "roster.xls")
        assertEquals("2023-09-01", table.cell(1, 4))
    }

    @Test
    fun `reads legacy xls number columns`() {
        val table = SpreadsheetReader.read(resource("roster.xls"), "roster.xls")
        assertEquals("88", table.cell(1, 5))
        assertEquals("92", table.cell(1, 6))
        assertEquals("76", table.cell(3, 5))
        assertEquals("73", table.cell(5, 6))
    }

    // ------------------------------------------------------------ RK 解码

    @Test
    fun `decodes rk integers`() {
        // fInt=1：高 30 位存放有符号整数
        assertEquals(95.0, XlsReader.decodeRk(382), 0.0)
        assertEquals(45170.0, XlsReader.decodeRk(180682), 0.0)
    }

    @Test
    fun `decodes negative rk integers`() {
        val rk = (-95 shl 2) or 0x02
        assertEquals(-95.0, XlsReader.decodeRk(rk), 0.0)
    }

    @Test
    fun `decodes rk values scaled by hundred`() {
        val rk = (123 shl 2) or 0x03 // fX100=1, fInt=1
        assertEquals(1.23, XlsReader.decodeRk(rk), 1e-9)
    }

    @Test
    fun `decodes rk floating point values`() {
        // fInt=0：RK 的 2-31 位存放 double 的高 30 位
        val rk = ((java.lang.Double.doubleToRawLongBits(-1.5) ushr 34).toInt() shl 2)
        assertEquals(-1.5, XlsReader.decodeRk(rk), 0.0)
    }

    // ------------------------------------------------------------ csv

    @Test
    fun `reads gbk encoded csv`() {
        val table = SpreadsheetReader.read(resource("roster.csv"), "roster.csv")
        assertEquals(7, table.columnCount)
        assertEquals("姓名", table.headerOf(1))
        assertEquals("张三", table.cell(1, 1))
        assertEquals("13800138000", table.cell(1, 3))
    }

    @Test
    fun `csv handles quotes and embedded separators`() {
        val csv = "姓名,备注\n张三,\"喜欢足球, 数学好\"\n李四,\"他说\"\"你好\"\"\"\n"
        val rows = CsvReader.parse(csv)
        assertEquals(3, rows.size)
        assertEquals("喜欢足球, 数学好", rows[1][1])
        assertEquals("他说\"你好\"", rows[2][1])
    }

    @Test
    fun `csv detects tab delimiter`() {
        val rows = CsvReader.parse("姓名\t学号\n张三\t001")
        assertEquals(2, rows.size)
        assertEquals("张三", rows[1][0])
    }

    // ------------------------------------------------------------ 启发式

    @Test
    fun `detects name column by header`() {
        val table = SpreadsheetReader.read(resource("roster.xlsx"), "roster.xlsx")
        assertEquals(1, ColumnHeuristics.detectNameColumn(table))
    }

    @Test
    fun `detects phone column`() {
        val table = SpreadsheetReader.read(resource("roster.xlsx"), "roster.xlsx")
        assertEquals(3, ColumnHeuristics.detectPhoneColumn(table))
    }

    @Test
    fun `detects name column by content when headers are unknown`() {
        val table = SheetTable(
            rows = listOf(
                listOf("编号", "成员"),
                listOf("1", "张三"),
                listOf("2", "李四")
            )
        )
        assertEquals(1, ColumnHeuristics.detectNameColumn(table))
    }

    @Test
    fun `recognizes phone like values`() {
        assertTrue(ColumnHeuristics.looksLikePhone("13800138000"))
        assertTrue(ColumnHeuristics.looksLikePhone("+86 138 0013 8000"))
        assertTrue(!ColumnHeuristics.looksLikePhone("语文"))
    }

    // ------------------------------------------------------------ 其它

    @Test
    fun `excel serial converts to date`() {
        assertEquals("2023-09-01", XlsxReader.formatSerial(45170.0))
        assertEquals("1900-01-01", XlsxReader.formatSerial(2.0))
    }

    @Test
    fun `column index of cell reference`() {
        assertEquals(0, XlsxReader.columnIndexOf("A1"))
        assertEquals(1, XlsxReader.columnIndexOf("B12"))
        assertEquals(26, XlsxReader.columnIndexOf("AA3"))
        assertEquals(52, XlsxReader.columnIndexOf("BA9"))
    }
}
