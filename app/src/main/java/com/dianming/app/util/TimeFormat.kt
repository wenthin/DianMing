package com.dianming.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormat {

    private fun fmt(pattern: String, ts: Long): String =
        SimpleDateFormat(pattern, Locale.CHINA).format(Date(ts))

    fun dateTime(ts: Long): String = fmt("yyyy-MM-dd HH:mm", ts)
    fun date(ts: Long): String = fmt("yyyy-MM-dd", ts)
    fun time(ts: Long): String = fmt("HH:mm", ts)

    /** 相对今天/昨天的友好显示 */
    fun friendly(ts: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ts }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDiff = daysBetween(then, now)
        val time = time(ts)
        return when {
            dayDiff == 0 -> "今天 $time"
            dayDiff == 1 -> "昨天 $time"
            dayDiff == 2 -> "前天 $time"
            sameYear -> "${then.get(Calendar.MONTH) + 1}月${then.get(Calendar.DAY_OF_MONTH)}日 $time"
            else -> dateTime(ts)
        }
    }

    /** 点名场次标题 */
    fun sessionTitle(ts: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ts }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        return if (sameYear) {
            "${then.get(Calendar.MONTH) + 1}月${then.get(Calendar.DAY_OF_MONTH)}日 ${time(ts)}"
        } else {
            fmt("yyyy/MM/dd HH:mm", ts)
        }
    }

    private fun daysBetween(from: Calendar, to: Calendar): Int {
        val a = from.clone() as Calendar
        val b = to.clone() as Calendar
        listOf(a, b).forEach {
            it.set(Calendar.HOUR_OF_DAY, 0)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }
        val diff = b.timeInMillis - a.timeInMillis
        return Math.round(diff / 86_400_000.0).toInt()
    }
}
