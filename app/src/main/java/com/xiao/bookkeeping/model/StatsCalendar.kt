package com.xiao.bookkeeping.model

import com.xiao.bookkeeping.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日历页的构建。
 *
 * 纯函数：记录由调用方按页的范围查好传进来，这里只管分格和汇总。
 * 传进来的记录超出本页范围不会影响结果——没有格子会去读它们。
 */
object StatsCalendar {

    /** 天档一页：[anchor] 所在自然月的每一天，首尾补空位凑成整周。 */
    fun month(anchor: Long, records: List<Record>, now: Long): StatsResult {
        val start = DateUtils.monthStart(anchor)
        val end = DateUtils.monthEndExclusive(anchor)
        val totals = tally(StatsScale.DAY, records)

        val cells = ArrayList<CalendarCell?>()
        // 1 号不是周一时前面留白，列才对得上星期
        repeat(DateUtils.mondayBasedWeekday(start)) { cells += null }

        var cursor = start
        while (cursor < end) {
            cells += cell(cursor, DateUtils.dayOf(cursor).toString(), totals)
            cursor = DateUtils.plusDays(cursor, 1)
        }
        while (cells.size % StatsScale.DAYS_PER_WEEK != 0) cells += null

        return result(
            scale = StatsScale.DAY,
            title = format(start, PATTERN_YEAR_MONTH),
            cells = cells,
            // 未来还没有发生，右箭头到今天所在的月份为止
            canGoNext = start < DateUtils.monthStart(now)
        )
    }

    /** 月档一页：[anchor] 所在自然年的 12 个月。 */
    fun year(anchor: Long, records: List<Record>, now: Long): StatsResult {
        val start = DateUtils.yearStart(anchor)
        val end = DateUtils.yearEndExclusive(anchor)
        val totals = tally(StatsScale.MONTH, records)

        val cells = ArrayList<CalendarCell?>(StatsScale.MONTHS_PER_YEAR)
        var cursor = start
        while (cursor < end) {
            cells += cell(cursor, format(cursor, PATTERN_MONTH_ONLY), totals)
            cursor = DateUtils.plusMonths(cursor, 1)
        }

        return result(
            scale = StatsScale.MONTH,
            title = format(start, PATTERN_YEAR_ONLY),
            cells = cells,
            canGoNext = start < DateUtils.yearStart(now)
        )
    }

    /**
     * 年档一页：从 [firstYearStart] 到 [now] 所在年份，每年一格。
     *
     * 起点只到最早一条记录那年——再往前都是空年，翻下去没有意义。
     */
    fun years(firstYearStart: Long, records: List<Record>, now: Long): StatsResult {
        val lastYear = DateUtils.yearStart(now)
        val totals = tally(StatsScale.YEAR, records)

        val cells = ArrayList<CalendarCell?>()
        var cursor = minOf(firstYearStart, lastYear)
        while (cursor <= lastYear) {
            cells += cell(cursor, DateUtils.yearOf(cursor).toString(), totals)
            cursor = DateUtils.plusYears(cursor, 1)
        }
        while (cells.size % StatsScale.MONTH_YEAR_COLUMNS != 0) cells += null

        return result(
            scale = StatsScale.YEAR,
            // 年份跨度比「历年」两个字信息量大：一眼看得出账记了多久
            title = yearSpan(cells),
            cells = cells,
            // 一页就装下了全部年份，没有翻页
            canGoPrev = false,
            canGoNext = false
        )
    }

    /** 汇总一律由格子加出来，这样底部卡片永远不会和网格对不上。 */
    private fun result(
        scale: StatsScale,
        title: String,
        cells: List<CalendarCell?>,
        canGoPrev: Boolean = true,
        canGoNext: Boolean
    ): StatsResult {
        var income = 0.0
        var expense = 0.0
        for (cell in cells) {
            if (cell == null) continue
            income += cell.income
            expense += cell.expense
        }
        return StatsResult(scale, title, cells, income, expense, canGoPrev, canGoNext)
    }

    private fun cell(startMs: Long, label: String, totals: Totals): CalendarCell =
        CalendarCell(
            startMs = startMs,
            label = label,
            income = totals.income[startMs] ?: 0.0,
            expense = totals.expense[startMs] ?: 0.0
        )

    /** 按周期起点把记录累加成「起点 → 收入/支出」。 */
    private fun tally(scale: StatsScale, records: List<Record>): Totals {
        val totals = Totals()
        for (record in records) {
            val bucket = if (record.type == RecordType.INCOME) totals.income else totals.expense
            // 记录的时间先对齐到周期起点，同一个周期的才落到同一格
            val key = scale.startOf(record.time)
            bucket[key] = (bucket[key] ?: 0.0) + record.amount
        }
        return totals
    }

    /** 首尾两个真实年份，空格子不算。 */
    private fun yearSpan(cells: List<CalendarCell?>): String {
        val first = cells.firstOrNull { it != null }?.label.orEmpty()
        val last = cells.lastOrNull { it != null }?.label.orEmpty()
        return if (first == last || last.isEmpty()) first else "$first ~ $last"
    }

    private fun format(time: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))

    private const val PATTERN_YEAR_MONTH = "yyyy-MM"
    private const val PATTERN_YEAR_ONLY = "yyyy"

    /** 月份标签 `1月`。`'月'` 是 SimpleDateFormat 的字面量转义。 */
    private const val PATTERN_MONTH_ONLY = "M'月'"

    private class Totals {
        val income = HashMap<Long, Double>()
        val expense = HashMap<Long, Double>()
    }
}
