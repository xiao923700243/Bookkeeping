package com.xiao.bookkeeping.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * 格子的热力档位。
 *
 * 结余有正负，所以色阶是**双向**的：以中性灰为界，正数走绿色臂、负数走红色臂，
 * 两条臂的档数、明度、彩度都对齐——不然一边喊一边哑，看着就不像一把尺子。
 * 具体色值由 tools/heatmap_palette.py 算出并验证过（明度单调、相邻档差 ≥ 0.06、
 * 每档上文字对比度 ≥ 4.5:1），改色前先重跑那个脚本。
 */
object HeatLevel {

    /** 不填色。没有记录，或者记录之间正好收支持平。 */
    const val NONE = 0

    /** 档数。两臂各 4 档。 */
    const val MAX = 4

    /**
     * 按 |结余| 相对本页最大值分档，返回与 [cells] 等长的档位数组。
     *
     * 用相对值而不是写死阈值：兼职收入从几十到几百都有，绝对阈值要么全挤在一档、
     * 要么撑不满色阶。代价是跨页的深浅不可比——但格子里本来就印着数字，
     * 颜色只是扫读时的辅助。
     *
     * 空格子（天档首尾补位）返回 [NONE]。
     */
    fun levelsOf(cells: List<CalendarCell?>): IntArray {
        // 先量化到分：浮点累加出来的 0.1+0.2 不该被当成「有结余」
        val cents = LongArray(cells.size) { centsOf(cells[it]) }
        val max = cents.maxOfOrNull { abs(it) } ?: 0L
        if (max == 0L) return IntArray(cells.size) { NONE }

        return IntArray(cells.size) { index ->
            val value = abs(cents[index])
            if (value == 0L) NONE else ceil(value.toDouble() / max * MAX).toInt().coerceIn(1, MAX)
        }
    }

    private fun centsOf(cell: CalendarCell?): Long =
        cell?.balance?.let { (it * 100).roundToLong() } ?: 0L
}
