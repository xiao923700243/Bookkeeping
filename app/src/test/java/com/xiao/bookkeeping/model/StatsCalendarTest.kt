package com.xiao.bookkeeping.model

import com.xiao.bookkeeping.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsCalendarTest {

    /** 固定成 2026-10-12 15:00，让「今天」不随运行时间漂移。 */
    private val now = DateUtils.of(2026, Calendar.OCTOBER, 12, 15, 0)

    // ---- 天档 ----

    @Test
    fun `month page blanks the leading days so the first lands under its weekday`() {
        // 2026-10-01 是周四，周一起算就是第 4 列，前面该有 3 个空格
        val result = StatsCalendar.month(now, emptyList(), now)

        assertTrue(result.cells.take(3).all { it == null })
        assertEquals("1", result.cells[3]?.label)
    }

    @Test
    fun `month page pads the tail to whole weeks`() {
        val result = StatsCalendar.month(now, emptyList(), now)

        // 3 个空格 + 10 月 31 天 = 34，补到 35 即 5 整周
        assertEquals(0, result.cells.size % StatsScale.DAYS_PER_WEEK)
        assertEquals(5, result.cells.size / StatsScale.DAYS_PER_WEEK)
    }

    @Test
    fun `month page title is the year and month`() {
        assertEquals("2026-10", StatsCalendar.month(now, emptyList(), now).title)
    }

    @Test
    fun `records land on their own day`() {
        val result = StatsCalendar.month(
            now,
            listOf(
                income(amount = 100.0, day = 1),
                expense(amount = 30.0, day = 1),
                income(amount = 50.0, day = 31)
            ),
            now
        )

        val first = result.cellOn(day = 1)
        assertEquals(100.0, first.income, DELTA)
        assertEquals(30.0, first.expense, DELTA)
        assertEquals(70.0, first.balance, DELTA)

        // 月末那天没被左闭右开的边界切掉
        assertEquals(50.0, result.cellOn(day = 31).income, DELTA)
    }

    @Test
    fun `records outside the month are ignored`() {
        val result = StatsCalendar.month(
            now,
            listOf(
                income(amount = 999.0, day = 30, month = Calendar.SEPTEMBER),
                income(amount = 888.0, day = 1, month = Calendar.NOVEMBER),
                income(amount = 10.0, day = 15)
            ),
            now
        )

        assertEquals(10.0, result.totalIncome, DELTA)
    }

    @Test
    fun `totals are the sum of the cells`() {
        val result = StatsCalendar.month(
            now,
            listOf(
                income(amount = 10.0, day = 2),
                expense(amount = 4.0, day = 2),
                income(amount = 6.0, day = 9),
                expense(amount = 1.0, day = 20)
            ),
            now
        )

        val present = result.cells.filterNotNull()
        assertEquals(present.sumOf { it.income }, result.totalIncome, DELTA)
        assertEquals(present.sumOf { it.expense }, result.totalExpense, DELTA)
        assertEquals(11.0, result.balance, DELTA)
    }

    @Test
    fun `a month with no records is empty`() {
        val result = StatsCalendar.month(now, emptyList(), now)

        assertTrue(result.isEmpty)
        // 空格子照样有标签，日历的形状不依赖有没有数据
        assertEquals(31, result.cells.filterNotNull().size)
    }

    @Test
    fun `the next page is blocked once the page reaches the current month`() {
        assertFalse(StatsCalendar.month(now, emptyList(), now).canGoNext)
        assertTrue(StatsCalendar.month(now, emptyList(), now).canGoPrev)

        val august = DateUtils.of(2026, Calendar.AUGUST, 5, 9, 0)
        assertTrue(StatsCalendar.month(august, emptyList(), now).canGoNext)
    }

    // ---- 月档 ----

    @Test
    fun `year page has twelve months labelled in order`() {
        val result = StatsCalendar.year(now, emptyList(), now)

        assertEquals(StatsScale.MONTHS_PER_YEAR, result.cells.size)
        assertEquals((1..12).map { "${it}月" }, result.cells.map { it?.label })
        assertEquals("2026", result.title)
    }

    @Test
    fun `year page buckets records by month`() {
        val result = StatsCalendar.year(
            now,
            listOf(
                income(amount = 100.0, day = 5, month = Calendar.JANUARY),
                income(amount = 200.0, day = 20, month = Calendar.JANUARY),
                expense(amount = 50.0, day = 1, month = Calendar.DECEMBER)
            ),
            now
        )

        val january = requireNotNull(result.cells[0])
        assertEquals(300.0, january.income, DELTA)
        assertEquals(0.0, january.expense, DELTA)

        val december = requireNotNull(result.cells[11])
        assertEquals(50.0, december.expense, DELTA)
        assertEquals(-50.0, december.balance, DELTA)
    }

    @Test
    fun `the next year page is blocked at the current year`() {
        assertFalse(StatsCalendar.year(now, emptyList(), now).canGoNext)

        val twoYearsBack = DateUtils.of(2024, Calendar.MAY, 1, 9, 0)
        assertTrue(StatsCalendar.year(twoYearsBack, emptyList(), now).canGoNext)
    }

    // ---- 年档 ----

    @Test
    fun `years page covers every year from the first record up to now`() {
        val result = StatsCalendar.years(
            firstYearStart = DateUtils.yearStart(DateUtils.of(2023, Calendar.MAY, 1, 9, 0)),
            records = listOf(income(amount = 100.0, day = 1, month = Calendar.MAY, year = 2023)),
            now = now
        )

        assertEquals(
            listOf("2023", "2024", "2025", "2026"),
            result.cells.filterNotNull().map { it.label }
        )
        // 4 格补到 3 的整倍数
        assertEquals(6, result.cells.size)
        assertEquals("2023 ~ 2026", result.title)
        assertEquals(100.0, requireNotNull(result.cells[0]).income, DELTA)
    }

    @Test
    fun `years page cannot be paged either way`() {
        val result = StatsCalendar.years(DateUtils.yearStart(now), emptyList(), now)

        assertFalse(result.canGoPrev)
        assertFalse(result.canGoNext)
    }

    @Test
    fun `years page sums a whole year into one cell`() {
        val result = StatsCalendar.years(
            firstYearStart = DateUtils.yearStart(DateUtils.of(2025, Calendar.JANUARY, 1, 0, 0)),
            records = listOf(
                income(amount = 200.0, day = 1, month = Calendar.MARCH, year = 2025),
                expense(amount = 50.0, day = 2, month = Calendar.MARCH, year = 2025)
            ),
            now = now
        )

        val wholeYear = requireNotNull(result.cells[0])
        assertEquals("2025", wholeYear.label)
        assertEquals(200.0, wholeYear.income, DELTA)
        assertEquals(50.0, wholeYear.expense, DELTA)

        // 两个月的记录都落在同一格，不会被拆到相邻年份去
        assertEquals("2026", requireNotNull(result.cells[1]).label)
        assertEquals(0.0, requireNotNull(result.cells[1]).income, DELTA)
    }

    @Test
    fun `years page never starts after the current year`() {
        // 库里最早一条记录在未来（用户改过系统时间）时，别把日历画成空的
        val result = StatsCalendar.years(
            firstYearStart = DateUtils.yearStart(DateUtils.of(2030, Calendar.JANUARY, 1, 0, 0)),
            records = emptyList(),
            now = now
        )

        assertEquals(listOf("2026"), result.cells.filterNotNull().map { it.label })
        assertEquals("2026", result.title)
    }

    @Test
    fun `a year with no records is empty`() {
        assertTrue(StatsCalendar.year(now, emptyList(), now).isEmpty)
        assertFalse(
            StatsCalendar.year(
                now,
                listOf(income(amount = 1.0, day = 1, month = Calendar.JANUARY)),
                now
            ).isEmpty
        )
    }

    private fun StatsResult.cellOn(
        day: Int,
        month: Int = Calendar.OCTOBER,
        year: Int = 2026
    ): CalendarCell {
        val start = DateUtils.of(year, month, day, 0, 0)
        return requireNotNull(cells.filterNotNull().firstOrNull { it.startMs == start }) {
            "没有 $year-$month-$day 这一格"
        }
    }

    private fun income(
        amount: Double,
        day: Int,
        month: Int = Calendar.OCTOBER,
        year: Int = 2026
    ) = Record(
        type = RecordType.INCOME,
        amount = amount,
        time = DateUtils.of(year, month, day, 9, 0)
    )

    private fun expense(
        amount: Double,
        day: Int,
        month: Int = Calendar.OCTOBER,
        year: Int = 2026
    ) = Record(
        type = RecordType.EXPENSE,
        amount = amount,
        time = DateUtils.of(year, month, day, 9, 0)
    )

    private companion object {
        const val DELTA = 0.0001
    }
}
