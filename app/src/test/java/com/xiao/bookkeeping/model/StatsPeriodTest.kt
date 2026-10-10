package com.xiao.bookkeeping.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsPeriodTest {

    @Test
    fun `bucket count matches each period`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)
        assertEquals(30, StatsPeriod.DAY.buckets(now).size)
        assertEquals(12, StatsPeriod.WEEK.buckets(now).size)
        assertEquals(12, StatsPeriod.MONTH.buckets(now).size)
        assertEquals(5, StatsPeriod.YEAR.buckets(now).size)
    }

    @Test
    fun `buckets are contiguous and in ascending order`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)
        for (period in StatsPeriod.values()) {
            val buckets = period.buckets(now)
            for (i in 0 until buckets.size - 1) {
                assertEquals(
                    "${period.name} 第 $i 个桶与下一个桶之间有缝隙或重叠",
                    buckets[i].endMs,
                    buckets[i + 1].startMs
                )
                assertTrue(buckets[i].startMs < buckets[i].endMs)
            }
        }
    }

    @Test
    fun `last bucket always contains now`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)
        for (period in StatsPeriod.values()) {
            val last = period.buckets(now).last()
            assertTrue("${period.name} 的最后一个桶没有覆盖 now", now >= last.startMs && now < last.endMs)
        }
    }

    @Test
    fun `day buckets align to midnight`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)
        for (bucket in StatsPeriod.DAY.buckets(now)) {
            val c = calendar(bucket.startMs)
            assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, c.get(Calendar.MINUTE))
            assertEquals(0, c.get(Calendar.SECOND))
            assertEquals(0, c.get(Calendar.MILLISECOND))
        }
    }

    @Test
    fun `week buckets start on monday`() {
        // 2026-10-10 是周六，往回推应该落在周一
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)
        for (bucket in StatsPeriod.WEEK.buckets(now)) {
            val c = calendar(bucket.startMs).apply { firstDayOfWeek = Calendar.MONDAY }
            assertEquals("周桶没有对齐到周一", Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
            assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        }
    }

    @Test
    fun `month buckets align to first day`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)
        val buckets = StatsPeriod.MONTH.buckets(now)
        for (bucket in buckets) {
            val c = calendar(bucket.startMs)
            assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        }
        // 最近 12 个月，最早一个应落在去年 11 月
        val first = calendar(buckets.first().startMs)
        assertEquals(Calendar.NOVEMBER, first.get(Calendar.MONTH))
        assertEquals(2025, first.get(Calendar.YEAR))
    }

    @Test
    fun `month buckets span across year boundary`() {
        val now = at(2026, Calendar.JANUARY, 15, 9, 0)
        val first = calendar(StatsPeriod.MONTH.buckets(now).first().startMs)
        assertEquals(2025, first.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, first.get(Calendar.MONTH))
    }

    @Test
    fun `year buckets align to january first`() {
        val now = at(2026, Calendar.OCTOBER, 10, 15, 30)
        val buckets = StatsPeriod.YEAR.buckets(now)
        for (bucket in buckets) {
            val c = calendar(bucket.startMs)
            assertEquals(Calendar.JANUARY, c.get(Calendar.MONTH))
            assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        }
        assertEquals(2022, calendar(buckets.first().startMs).get(Calendar.YEAR))
        assertEquals(2026, calendar(buckets.last().startMs).get(Calendar.YEAR))
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
