package com.xiao.bookkeeping.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 时间戳的展示格式化，以及日期选择器需要的字段读写。 */
object DateUtils {

    private const val PATTERN_DATE_TIME = "yyyy-MM-dd HH:mm"
    private const val PATTERN_DATE = "yyyy-MM-dd"

    fun formatDateTime(time: Long): String = format(time, PATTERN_DATE_TIME)

    fun formatDate(time: Long): String = format(time, PATTERN_DATE)

    /**
     * 统计窗口的区间文案，如 `2026-09-11 ~ 2026-10-10`。
     *
     * [endMsExclusive] 是左闭右开区间的右端，展示时回退一天才是用户眼中的最后一天。
     */
    fun formatRange(startMs: Long, endMsExclusive: Long): String {
        val lastDay = Calendar.getInstance().apply {
            timeInMillis = endMsExclusive
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return "${formatDate(startMs)} ~ ${formatDate(lastDay.timeInMillis)}"
    }

    fun yearOf(time: Long): Int = field(time, Calendar.YEAR)
    fun monthOf(time: Long): Int = field(time, Calendar.MONTH)
    fun dayOf(time: Long): Int = field(time, Calendar.DAY_OF_MONTH)
    fun hourOf(time: Long): Int = field(time, Calendar.HOUR_OF_DAY)
    fun minuteOf(time: Long): Int = field(time, Calendar.MINUTE)

    /** 用年月日时分装回时间戳，秒和毫秒归零。 */
    fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** [time] 所在自然日的起点。 */
    fun dayStart(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** [time] 所在自然日的终点（左闭右开，即次日起点）。 */
    fun dayEndExclusive(time: Long): Long =
        Calendar.getInstance().apply { timeInMillis = dayStart(time) }
            .also { it.add(Calendar.DAY_OF_YEAR, 1) }
            .timeInMillis

    /** [time] 加减 [days] 个自然日。用 Calendar 而非固定毫秒数，避开夏令时的进位误差。 */
    fun plusDays(time: Long, days: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = time
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis

    /** [time] 所在自然月的起点。 */
    fun monthStart(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** [time] 所在自然月的终点（左闭右开，即下月起点）。 */
    fun monthEndExclusive(time: Long): Long =
        Calendar.getInstance().apply { timeInMillis = monthStart(time) }
            .also { it.add(Calendar.MONTH, 1) }
            .timeInMillis

    private fun field(time: Long, field: Int): Int =
        Calendar.getInstance().apply { timeInMillis = time }.get(field)

    private fun format(time: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}
