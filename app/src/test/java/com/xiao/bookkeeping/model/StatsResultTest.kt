package com.xiao.bookkeeping.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsResultTest {

    private val buckets = listOf(
        Bucket("d1", 0L, 100L),
        Bucket("d2", 100L, 200L),
        Bucket("d3", 200L, 300L)
    )

    @Test
    fun `records land in the bucket that covers their timestamp`() {
        val result = StatsResult.of(
            buckets,
            listOf(
                income(amount = 10.0, time = 0L),    // 左闭，落在 d1
                expense(amount = 5.0, time = 99L),   // d1 末尾
                expense(amount = 7.0, time = 100L),  // 右开，落在 d2
                income(amount = 3.0, time = 299L)    // d3 末尾
            )
        )

        assertEquals(listOf(10f, 0f, 3f), result.income)
        assertEquals(listOf(5f, 7f, 0f), result.expense)
    }

    @Test
    fun `empty buckets stay zero so the x axis keeps its shape`() {
        val result = StatsResult.of(buckets, emptyList())

        assertEquals(3, result.income.size)
        assertEquals(listOf(0f, 0f, 0f), result.income)
        assertEquals(listOf(0f, 0f, 0f), result.expense)
        assertEquals(0.0, result.totalIncome, 0.0)
        assertEquals(0.0, result.totalExpense, 0.0)
    }

    @Test
    fun `same type in one bucket is summed`() {
        val result = StatsResult.of(
            buckets,
            listOf(
                expense(amount = 12.5, time = 10L),
                expense(amount = 7.5, time = 50L),
                income(amount = 100.0, time = 60L)
            )
        )

        assertEquals(listOf(20f, 0f, 0f), result.expense)
        assertEquals(listOf(100f, 0f, 0f), result.income)
        assertEquals(20.0, result.totalExpense, 0.0001)
        assertEquals(100.0, result.totalIncome, 0.0001)
    }

    @Test
    fun `records outside the window are ignored`() {
        val result = StatsResult.of(
            buckets,
            listOf(
                income(amount = 999.0, time = -1L),
                income(amount = 888.0, time = 300L),
                income(amount = 1.0, time = 150L)
            )
        )

        assertEquals(listOf(0f, 1f, 0f), result.income)
        assertEquals(1.0, result.totalIncome, 0.0001)
    }

    @Test
    fun `balance is income minus expense`() {
        val result = StatsResult.of(
            buckets,
            listOf(income(amount = 100.0, time = 10L), expense(amount = 250.0, time = 20L))
        )

        assertEquals(-150.0, result.balance, 0.0001)
    }

    @Test
    fun `labels follow bucket order`() {
        val result = StatsResult.of(buckets, emptyList())
        assertEquals(listOf("d1", "d2", "d3"), result.labels)
    }

    private fun income(amount: Double, time: Long) =
        Record(type = RecordType.INCOME, amount = amount, time = time)

    private fun expense(amount: Double, time: Long) =
        Record(type = RecordType.EXPENSE, amount = amount, time = time)
}
