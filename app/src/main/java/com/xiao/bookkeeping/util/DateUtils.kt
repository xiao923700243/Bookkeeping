package com.xiao.bookkeeping.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 时间戳的展示格式化，以及日期选择器需要的字段读写。 */
object DateUtils {

    private const val PATTERN_DATE_TIME = "yyyy-MM-dd HH:mm"
    private const val PATTERN_DATE = "yyyy-MM-dd"
    private const val PATTERN_MONTH_DAY = "MM-dd"
    private const val PATTERN_TIME = "HH:mm"

    fun formatDateTime(time: Long): String = format(time, PATTERN_DATE_TIME)

    fun formatDate(time: Long): String = format(time, PATTERN_DATE)

    /** 不含年份的月日，如 `10-09`。 */
    fun formatMonthDay(time: Long): String = format(time, PATTERN_MONTH_DAY)

    /**
     * 只到分钟，如 `19:05`。
     *
     * 列表页已按天筛过，日期由顶部卡片给出，子项再重复一遍日期就是冗余。
     */
    fun formatTime(time: Long): String = format(time, PATTERN_TIME)

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
    fun plusDays(time: Long, days: Int): Long = shift(time, Calendar.DAY_OF_YEAR, days)

    /** [time] 加减 [months] 个自然月。日期不存在时（如 1-31 加一个月）由 Calendar 自行收敛。 */
    fun plusMonths(time: Long, months: Int): Long = shift(time, Calendar.MONTH, months)

    fun plusYears(time: Long, years: Int): Long = shift(time, Calendar.YEAR, years)

    /**
     * [time] 在日历网格里的列号，周一为 0、周日为 6。
     *
     * Calendar 的 DAY_OF_WEEK 是周日为 1、周六为 7，平移成以周一打头的列号。
     */
    fun mondayBasedWeekday(time: Long): Int =
        (field(time, Calendar.DAY_OF_WEEK) + 5) % 7

    private fun shift(time: Long, field: Int, amount: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = time
            add(field, amount)
        }.timeInMillis

    /**
     * [time] 在 [now] 眼中是几天前：0=今天、1=昨天、2=前天；更早或更晚都返回 null。
     *
     * 逐日比对而不是把毫秒差除以一天的毫秒数——夏令时切换那天只有 23 小时，
     * 相除会算出 0.96 天，取整就错一天。
     */
    fun daysAgo(time: Long, now: Long, limit: Int = 2): Int? {
        val day = dayStart(time)
        val today = dayStart(now)
        for (days in 0..limit) {
            if (day == plusDays(today, -days)) return days
        }
        return null
    }

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

    /** [time] 所在自然年的起点。 */
    fun yearStart(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** [time] 所在自然年的终点（左闭右开，即次年起点）。 */
    fun yearEndExclusive(time: Long): Long =
        Calendar.getInstance().apply { timeInMillis = yearStart(time) }
            .also { it.add(Calendar.YEAR, 1) }
            .timeInMillis

    private fun field(time: Long, field: Int): Int =
        Calendar.getInstance().apply { timeInMillis = time }.get(field)

    private fun format(time: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}
