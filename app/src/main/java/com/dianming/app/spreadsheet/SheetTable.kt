package com.dianming.app.spreadsheet

/**
 * 解析后的表格数据。rows 是二维字符串数组，缺失单元格用空串占位。
 */
data class SheetTable(
    val rows: List<List<String>>,
    val sheetName: String? = null,
    val sourceName: String = ""
) {
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
    val rowCount: Int get() = rows.size

    fun cell(row: Int, col: Int): String =
        rows.getOrNull(row)?.getOrNull(col)?.trim().orEmpty()

    /** 表头文本，为空时回退成"第N列" */
    fun headerOf(col: Int): String {
        val h = cell(0, col)
        return h.ifBlank { "第${col + 1}列" }
    }

    /** 第 col 列的全部数据（不含表头） */
    fun columnValues(col: Int): List<String> =
        (1 until rowCount).map { cell(it, col) }.filter { it.isNotBlank() }
}

/**
 * 列名/列内容的启发式判断，用于自动识别"名字列"和"电话列"。
 */
object ColumnHeuristics {

    private val NAME_HEADERS = listOf(
        "姓名", "名字", "学生姓名", "学生名字", "学生", "人员姓名", "成员姓名",
        "人员", "成员", "学生名单", "名单", "name", "fullname", "full name", "student", "student name"
    )

    private val PHONE_HEADERS = listOf(
        "电话", "手机", "手机号", "手机号码", "联系电话", "联系方式", "联系号码",
        "号码", "座机", "家庭电话", "手机电话", "phone", "mobile", "tel", "telephone", "phone number"
    )

    private val ID_HEADERS = listOf(
        "学号", "编号", "序号", "工号", "id", "no", "number", "学号/编号"
    )

    private val NAME_PATTERN = Regex("^(姓名|名字|学生姓名|学生名字|人员姓名|名字列表|名单|学生|成员|人员)")

    /** 表头是否像"名字"列 */
    fun isNameHeader(header: String): Boolean {
        val h = header.trim().lowercase().replace(" ", "")
        if (h.isEmpty()) return false
        return NAME_HEADERS.any { h == it } || NAME_PATTERN.matches(h)
    }

    /** 表头是否像"电话"列 */
    fun isPhoneHeader(header: String): Boolean {
        val h = header.trim().lowercase().replace(" ", "")
        if (h.isEmpty()) return false
        return PHONE_HEADERS.any { h == it || h.startsWith(it) || h.contains(it) }
    }

    fun isIdHeader(header: String): Boolean {
        val h = header.trim().lowercase()
        return ID_HEADERS.any { h == it }
    }

    /** 内容是否像电话号码 */
    fun looksLikePhone(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return false
        val digits = v.filter { it.isDigit() }
        if (digits.length < 7 || digits.length > 15) return false
        if (!v.matches(Regex("^[0-9+\\-\\s()（）转]+$"))) return false
        // 纯数字且长度合理，或带国际区号
        return digits.length >= 7
    }

    /** 内容是否像"姓名"：2~20 个字符，非纯数字 */
    fun looksLikeName(value: String): Boolean {
        val v = value.trim()
        if (v.length < 2 || v.length > 20) return false
        if (v.all { it.isDigit() || it == '.' || it == '-' }) return false
        return v.none { it.isDigit() && v.length <= 2 }
    }

    /**
     * 自动识别名字列。优先按表头匹配，其次按内容特征（短文本、无数字、唯一性高）。
     */
    fun detectNameColumn(table: SheetTable): Int? {
        val cols = table.columnCount
        if (cols == 0) return null

        for (c in 0 until cols) {
            if (isNameHeader(table.headerOf(c))) return c
        }
        // 表头带"姓名"但顺序上更靠前的不特殊处理，取第一个匹配的即可
        for (c in 0 until cols) {
            val h = table.headerOf(c).lowercase()
            if (h.contains("姓名") || h.contains("名字")) return c
        }

        var best: Int? = null
        var bestScore = 0.0
        for (c in 0 until cols) {
            if (isPhoneHeader(table.headerOf(c)) || isIdHeader(table.headerOf(c))) continue
            val values = table.columnValues(c)
            if (values.isEmpty()) continue
            val nameRatio = values.count { looksLikeName(it) }.toDouble() / values.size
            if (nameRatio < 0.6) continue
            val uniqueRatio = values.map { it.trim() }.distinct().size.toDouble() / values.size
            // 数字比例越低越好，位置越靠前略加分
            val score = nameRatio * 2 + uniqueRatio - c * 0.01
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return best
    }

    /** 自动识别电话列 */
    fun detectPhoneColumn(table: SheetTable): Int? {
        val cols = table.columnCount
        if (cols == 0) return null
        for (c in 0 until cols) {
            if (isPhoneHeader(table.headerOf(c))) return c
        }
        for (c in 0 until cols) {
            val values = table.columnValues(c)
            if (values.isEmpty()) continue
            val ratio = values.count { looksLikePhone(it) }.toDouble() / values.size
            if (ratio >= 0.6) return c
        }
        return null
    }
}
