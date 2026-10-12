package com.xiao.bookkeeping.model

/**
 * 日历里的一格，代表一个自然日 / 月 / 年。
 *
 * [startMs] 是该周期起点，点格子跳转到别的页面时靠它定位。
 */
data class CalendarCell(
    val startMs: Long,
    val label: String,
    val income: Double,
    val expense: Double
) {
    /** 结余。收入为正、支出为负，格子里的数字和热力深浅都基于它。 */
    val balance: Double get() = income - expense

    /** 当天有没有记过账。没有记录的格子不填色，也要和「收支持平」区分开。 */
    val hasData: Boolean get() = income > 0.0 || expense > 0.0
}
