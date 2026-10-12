package com.xiao.bookkeeping.model

/**
 * 一页日历：网格里的格子、这一页的汇总，以及能不能继续翻页。
 *
 * [cells] 里的 null 是占位空格。天档一个月往往不是整周，首尾得补齐列才对得上星期。
 */
data class StatsResult(
    val scale: StatsScale,
    /** 页标题：天档 `2026-10`、月档 `2026`、年档 `2024 ~ 2026`。 */
    val title: String,
    val cells: List<CalendarCell?>,
    val totalIncome: Double,
    val totalExpense: Double,
    val canGoPrev: Boolean,
    val canGoNext: Boolean
) {
    val balance: Double get() = totalIncome - totalExpense

    /** 整页一条记录都没有。此时不画热力，给空态提示。 */
    val isEmpty: Boolean get() = totalIncome <= 0.0 && totalExpense <= 0.0
}
