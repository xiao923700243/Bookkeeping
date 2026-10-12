package com.xiao.bookkeeping.model

import com.xiao.bookkeeping.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 页锚点的约定：它保留到「月」，并且切档时不被截断。
 *
 * 这两条合起来才保证「天档 → 年档 → 天档」回到原来那个月。以前切档会把锚点
 * 对齐到新粒度，年档一步就把 10 月截成了 1 月，回天档只能看到 1 月。
 */
class StatsScaleTest {

    /** 2026-10-01。锚点恒为某月 1 号，由 StatsActivity 维护。 */
    private val october = DateUtils.of(2026, Calendar.OCTOBER, 1, 0, 0)

    @Test
    fun `paging a year in month scale keeps the month of the anchor`() {
        // 保月份才谈得上「切回天档落回 10 月」；对齐成年初就丢了
        val previous = StatsScale.MONTH.shiftAnchor(october, -1)
        val next = StatsScale.MONTH.shiftAnchor(october, 1)

        assertEquals(DateUtils.of(2025, Calendar.OCTOBER, 1, 0, 0), previous)
        assertEquals(DateUtils.of(2027, Calendar.OCTOBER, 1, 0, 0), next)
    }

    @Test
    fun `paging a month in day scale moves whole months`() {
        assertEquals(
            DateUtils.of(2026, Calendar.SEPTEMBER, 1, 0, 0),
            StatsScale.DAY.shiftAnchor(october, -1)
        )
        // 跨年也不出岔子：12 月往后是次年 1 月
        val december = DateUtils.of(2026, Calendar.DECEMBER, 1, 0, 0)
        assertEquals(
            DateUtils.of(2027, Calendar.JANUARY, 1, 0, 0),
            StatsScale.DAY.shiftAnchor(december, 1)
        )
    }

    @Test
    fun `year scale has nothing to page`() {
        assertEquals(october, StatsScale.YEAR.shiftAnchor(october, -3))
        assertEquals(october, StatsScale.YEAR.shiftAnchor(october, 5))
    }

    @Test
    fun `paging never drifts the anchor off the first of a month`() {
        // 锚点一旦带上「几号」，天档翻页就会从月中起算，页和页之间错位
        for (scale in StatsScale.values()) {
            for (pages in -3..3) {
                val moved = scale.shiftAnchor(october, pages)
                assertEquals(
                    "$scale 翻 $pages 页后锚点不在月初：$moved",
                    moved,
                    DateUtils.monthStart(moved)
                )
            }
        }
    }
}
