package com.xiao.bookkeeping.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatLevelTest {

    @Test
    fun `a day with no record is not filled`() {
        assertEquals(listOf(HeatLevel.NONE), HeatLevel.levelsOf(listOf(cell())).toList())
    }

    @Test
    fun `breaking even is not filled either`() {
        // 收支持平时正负两条臂都套不上，由 Activity 画成中性灰
        assertEquals(
            listOf(HeatLevel.NONE),
            HeatLevel.levelsOf(listOf(cell(income = 50.0, expense = 50.0))).toList()
        )
    }

    @Test
    fun `the biggest balance on the page takes the darkest step`() {
        val levels = HeatLevel.levelsOf(
            listOf(cell(income = 100.0), cell(income = 25.0), cell(income = 200.0))
        )

        assertEquals(4, levels[2])
        assertEquals(2, levels[0])
        assertEquals(1, levels[1])
    }

    @Test
    fun `the smallest balance still shows`() {
        // 1 块和 200 块在色阶两端，否则「有点收入」和「没收入」就分不出来了
        val levels = HeatLevel.levelsOf(listOf(cell(income = 200.0), cell(income = 1.0)))

        assertEquals(4, levels[0])
        assertEquals(1, levels[1])
    }

    @Test
    fun `sign does not matter - only the size`() {
        // 走绿臂还是红臂由 Activity 按正负决定，这里只管深浅
        val spend = HeatLevel.levelsOf(listOf(cell(income = 10.0, expense = 100.0)))

        assertEquals(4, spend[0])
    }

    @Test
    fun `a spend day is weighed by its own size`() {
        val levels = HeatLevel.levelsOf(
            listOf(cell(income = 90.0), cell(expense = 180.0))
        )

        // |−180| 是这页最大的，满档；90 是它的一半，两档
        assertEquals(4, levels[1])
        assertEquals(2, levels[0])
    }

    @Test
    fun `placeholder cells are not filled and do not set the scale`() {
        val levels = HeatLevel.levelsOf(listOf(null, null, cell(income = 10.0)))

        assertEquals(listOf(0, 0, 4), levels.toList())
    }

    @Test
    fun `a leftover from float summing does not count as a balance`() {
        // 0.1 + 0.2 − 0.3 在浮点里是 5.5e-17，量化到分就是 0
        val levels = HeatLevel.levelsOf(listOf(cell(income = 0.1 + 0.2, expense = 0.3)))

        assertEquals(HeatLevel.NONE, levels[0])
    }

    @Test
    fun `a page that is all zeros yields all unfilled`() {
        val levels = HeatLevel.levelsOf(listOf(cell(), null, cell(income = 5.0, expense = 5.0)))

        assertEquals(listOf(0, 0, 0), levels.toList())
    }

    private fun cell(income: Double = 0.0, expense: Double = 0.0) =
        CalendarCell(startMs = 0L, label = "1", income = income, expense = expense)
}
