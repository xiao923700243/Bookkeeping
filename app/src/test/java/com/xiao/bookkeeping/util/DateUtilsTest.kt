package com.xiao.bookkeeping.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateUtilsTest {

    @Test
    fun `day start truncates to midnight`() {
        val start = DateUtils.dayStart(at(2026, Calendar.OCTOBER, 10, 15, 30))
        val c = calendar(start)

        assertEquals(2026, c.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, c.get(Calendar.MONTH))
        assertEquals(10, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals(0, c.get(Calendar.SECOND))
        assertEquals(0, c.get(Calendar.MILLISECOND))
    }

    @Test
    fun `day end is the next day's start`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)
        val end = calendar(DateUtils.dayEndExclusive(now))

        assertEquals(11, end.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, end.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `today window covers the whole day and nothing else`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)
        val start = DateUtils.dayStart(now)
        val end = DateUtils.dayEndExclusive(now)

        // 当天首尾都在窗口内（左闭右开）
        assertTrue(DateUtils.of(2026, Calendar.OCTOBER, 10, 0, 0) in start until end)
        assertTrue(DateUtils.of(2026, Calendar.OCTOBER, 10, 23, 59) in start until end)

        // 昨天最后一分钟、明天第一分钟都不算今天——这是汇总数字对不对的关键
        assertFalse(DateUtils.of(2026, Calendar.OCTOBER, 9, 23, 59) in start until end)
        assertFalse(DateUtils.of(2026, Calendar.OCTOBER, 11, 0, 0) in start until end)
    }

    @Test
    fun `day window spans midnight across month boundary`() {
        val now = at(2026, Calendar.OCTOBER, 31, 20, 0)
        val end = calendar(DateUtils.dayEndExclusive(now))

        assertEquals(Calendar.NOVEMBER, end.get(Calendar.MONTH))
        assertEquals(1, end.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `plus days keeps the time of day`() {
        val target = calendar(DateUtils.plusDays(at(2026, Calendar.OCTOBER, 10, 18, 30), -2))

        assertEquals(8, target.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, target.get(Calendar.MINUTE))
    }

    @Test
    fun `plus days crosses month and year boundaries`() {
        val backOneDay = calendar(DateUtils.plusDays(at(2026, Calendar.NOVEMBER, 1, 9, 0), -1))
        assertEquals(Calendar.OCTOBER, backOneDay.get(Calendar.MONTH))
        assertEquals(31, backOneDay.get(Calendar.DAY_OF_MONTH))

        val backAcrossYear = calendar(DateUtils.plusDays(at(2027, Calendar.JANUARY, 1, 9, 0), -1))
        assertEquals(2026, backAcrossYear.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, backAcrossYear.get(Calendar.MONTH))
        assertEquals(31, backAcrossYear.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `day start of two days ago differs from today and yesterday`() {
        // 快捷按钮靠这个判断该高亮哪一个，三者一旦重合就会同时选中
        val today = DateUtils.dayStart(at(2026, Calendar.OCTOBER, 10, 23, 59))
        val yesterday = DateUtils.dayStart(DateUtils.plusDays(today, -1))
        val beforeYesterday = DateUtils.dayStart(DateUtils.plusDays(today, -2))

        assertEquals(3, setOf(today, yesterday, beforeYesterday).size)
    }

    @Test
    fun `month start aligns to the first day`() {
        val start = calendar(DateUtils.monthStart(at(2026, Calendar.OCTOBER, 10, 15, 30)))

        assertEquals(1, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, start.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `month end rolls over the year`() {
        val end = calendar(DateUtils.monthEndExclusive(at(2026, Calendar.DECEMBER, 15, 9, 0)))

        assertEquals(2027, end.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, end.get(Calendar.MONTH))
        assertEquals(1, end.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `month end handles february in a leap year`() {
        val end = calendar(DateUtils.monthEndExclusive(at(2028, Calendar.FEBRUARY, 10, 9, 0)))

        assertEquals(Calendar.MARCH, end.get(Calendar.MONTH))
        assertEquals(1, end.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `days ago counts today yesterday and the day before`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)

        assertEquals(0, DateUtils.daysAgo(at(2026, Calendar.OCTOBER, 10, 9, 0), now))
        assertEquals(1, DateUtils.daysAgo(at(2026, Calendar.OCTOBER, 9, 9, 0), now))
        assertEquals(2, DateUtils.daysAgo(at(2026, Calendar.OCTOBER, 8, 9, 0), now))
    }

    @Test
    fun `days ago ignores the time of day`() {
        // 同一天的 00:00 和 23:59 都算「今天」，否则日期栏会在半夜跳成「昨天」
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)

        assertEquals(0, DateUtils.daysAgo(DateUtils.of(2026, Calendar.OCTOBER, 10, 0, 0), now))
        assertEquals(0, DateUtils.daysAgo(DateUtils.of(2026, Calendar.OCTOBER, 10, 23, 59), now))
    }

    @Test
    fun `days ago returns null beyond the limit`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)

        assertNull(DateUtils.daysAgo(at(2026, Calendar.OCTOBER, 7, 9, 0), now))
        assertNull(DateUtils.daysAgo(at(2026, Calendar.JANUARY, 1, 9, 0), now))
    }

    @Test
    fun `days ago returns null for a future day`() {
        // 日期栏靠右箭头禁用挡住未来，但它仍要能回答「明天是几天前」= 不是
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)

        assertNull(DateUtils.daysAgo(at(2026, Calendar.OCTOBER, 11, 9, 0), now))
        assertNull(DateUtils.daysAgo(at(2026, Calendar.OCTOBER, 13, 9, 0), now))
    }

    @Test
    fun `days ago crosses month and year boundaries`() {
        assertEquals(
            1,
            DateUtils.daysAgo(
                at(2026, Calendar.OCTOBER, 31, 20, 0),
                at(2026, Calendar.NOVEMBER, 1, 9, 0)
            )
        )
        assertEquals(
            1,
            DateUtils.daysAgo(
                at(2026, Calendar.DECEMBER, 31, 20, 0),
                at(2027, Calendar.JANUARY, 1, 9, 0)
            )
        )
    }

    @Test
    fun `days ago survives days that are not 24 hours long`() {
        // 夏令时切换那天只有 23（或 25）小时。实现用 plusDays 逐日比对而不是毫秒相除，
        // 这里扫一整年，把默认时区的切换点都盖进去
        val now = at(2026, Calendar.JANUARY, 1, 12, 0)

        for (offset in 0 until 365) {
            val day = DateUtils.plusDays(now, offset)
            for (back in 0..2) {
                assertEquals(
                    "第 $offset 天往前 $back 天",
                    back,
                    DateUtils.daysAgo(DateUtils.plusDays(day, -back), day)
                )
            }
        }
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun calendar(time: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = time }
}
