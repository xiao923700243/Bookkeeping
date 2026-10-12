package com.xiao.bookkeeping.model

import com.xiao.bookkeeping.util.DateUtils

/**
 * 统计粒度。
 *
 * 每个粒度对应日历的一页：天档一页是一个自然月、月档一页是一个自然年、
 * 年档一页是历年。格子里的数字是那个周期内的结余。
 */
enum class StatsScale {

    /** 一页 = 一个月，每格一天。 */
    DAY,

    /** 一页 = 一年，每格一个月。 */
    MONTH,

    /** 一页 = 历年，每格一年。 */
    YEAR;

    /** 网格列数。天档按一周 7 列排，月/年档 3 列排 4 行。 */
    val columns: Int
        get() = if (this == DAY) DAYS_PER_WEEK else MONTH_YEAR_COLUMNS

    /** 把 [time] 对齐到本粒度所在周期的起点。 */
    fun startOf(time: Long): Long = when (this) {
        DAY -> DateUtils.dayStart(time)
        MONTH -> DateUtils.monthStart(time)
        YEAR -> DateUtils.yearStart(time)
    }

    /**
     * 把页锚点挪 [pages] 页，负数往前翻。
     *
     * 锚点保留到「月」，所以月档翻年时月份跟着走（2026-10 往前翻一年是 2025-10，
     * 不是 2025-01），切回天档就落在这个月。
     *
     * 年档一页就装下了全部年份，没有翻页一说，锚点原样返回。
     */
    fun shiftAnchor(anchor: Long, pages: Int): Long = when (this) {
        DAY -> DateUtils.plusMonths(anchor, pages)
        MONTH -> DateUtils.plusYears(anchor, pages)
        YEAR -> anchor
    }

    companion object {
        const val DAYS_PER_WEEK = 7
        const val MONTH_YEAR_COLUMNS = 3
        const val MONTHS_PER_YEAR = 12
    }
}
