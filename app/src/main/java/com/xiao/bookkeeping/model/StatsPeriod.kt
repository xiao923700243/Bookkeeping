package com.xiao.bookkeeping.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 一个统计周期。区间左闭右开 `[startMs, endMs)`。 */
data class Bucket(val label: String, val startMs: Long, val endMs: Long)

/**
 * 统计粒度。
 *
 * 每种粒度对应「最近 N 个周期」的滚动窗口：天→最近 30 天、周→最近 12 周、
 * 月→最近 12 个月、年→最近 5 年。横轴从左到右就是时间推移。
 */
enum class StatsPeriod(val bucketCount: Int) {
    DAY(30),
    WEEK(12),
    MONTH(12),
    YEAR(5);

    /**
     * 生成以 [now] 所在周期结尾、连续 [bucketCount] 个桶（时间正序）。
     *
     * 窗口内没有任何记录的周期也会生成空桶，由 [StatsResult.of] 补 0，
     * 这样横轴刻度才不会因为跳过空周期而错位。
     */
    fun buckets(now: Long = System.currentTimeMillis()): List<Bucket> {
        val formatter = SimpleDateFormat(labelPattern, Locale.getDefault())
        val cursor = startOfPeriod(now)

        // 从当前周期开始往前推，最后反转成正序
        val starts = ArrayList<Calendar>(bucketCount)
        repeat(bucketCount) {
            starts += cursor.clone() as Calendar
            cursor.add(field, -1)
        }
        starts.reverse()

        return starts.map { start ->
            val end = (start.clone() as Calendar).apply { add(field, 1) }
            Bucket(
                label = formatter.format(start.time),
                startMs = start.timeInMillis,
                endMs = end.timeInMillis
            )
        }
    }

    private val field: Int
        get() = when (this) {
            DAY -> Calendar.DAY_OF_YEAR
            WEEK -> Calendar.WEEK_OF_YEAR
            MONTH -> Calendar.MONTH
            YEAR -> Calendar.YEAR
        }

    private val labelPattern: String
        get() = when (this) {
            DAY, WEEK -> "MM-dd"   // 周标签用该周的起始日
            MONTH -> "yyyy-MM"
            YEAR -> "yyyy"
        }

    /** 把 [time] 对齐到所在周期的起点 00:00:00.000。 */
    private fun startOfPeriod(time: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        when (this@StatsPeriod) {
            DAY -> Unit
            WEEK -> {
                // 一周从周一起算；必须先定 firstDayOfWeek，再设 DAY_OF_WEEK 才落在本周的周一
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            MONTH -> set(Calendar.DAY_OF_MONTH, 1)
            YEAR -> set(Calendar.DAY_OF_YEAR, 1)
        }
    }
}
