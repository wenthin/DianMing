package com.dianming.app.data

/**
 * 极简的键值对序列化（不依赖 org.json，方便 JVM 单元测试）。
 */
object TinyStore {

    private const val UNIT = '\u001F'
    private const val RECORD = '\u001E'

    fun encode(items: List<Pair<String, String>>): String {
        if (items.isEmpty()) return ""
        val sb = StringBuilder()
        for ((k, v) in items) {
            if (sb.isNotEmpty()) sb.append(RECORD)
            sb.append(escape(k)).append(UNIT).append(escape(v))
        }
        return sb.toString()
    }

    fun decode(text: String?): List<Pair<String, String>> {
        if (text.isNullOrEmpty()) return emptyList()
        val result = ArrayList<Pair<String, String>>()
        for (rec in text.split(RECORD)) {
            if (rec.isEmpty()) continue
            val idx = rec.indexOf(UNIT)
            if (idx < 0) continue
            result.add(unescape(rec.substring(0, idx)) to unescape(rec.substring(idx + 1)))
        }
        return result
    }

    fun encodeMap(map: Map<String, String>): String = encode(map.toList())

    fun decodeMap(text: String?): Map<String, String> = decode(text).toMap()

    fun encodeOwners(owners: List<Owner>): String =
        encode(owners.map { it.name to it.phone })

    fun decodeOwners(text: String?): List<Owner> =
        decode(text).map { Owner(it.first, it.second) }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 4)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                UNIT -> sb.append("\\u")
                RECORD -> sb.append("\\r")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    'u' -> { sb.append(UNIT); i += 2 }
                    'r' -> { sb.append(RECORD); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
