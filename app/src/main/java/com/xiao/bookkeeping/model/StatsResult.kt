package com.xiao.bookkeeping.model

/**
 * 统计结果：与 [buckets] 一一对应的收入/支出两条曲线，外加整个窗口的汇总。
 *
 * 曲线数组用 Float 是为了直接喂给图表库，汇总用 Double 避免累加精度损失。
 */
data class StatsResult(
    val labels: List<String>,
    val income: List<Float>,
    val expense: List<Float>,
    val totalIncome: Double,
    val totalExpense: Double
) {
    val balance: Double get() = totalIncome - totalExpense

    companion object {
        /** 把 [records] 按 [buckets] 分桶求和；没有记录的桶留 0。 */
        fun of(buckets: List<Bucket>, records: List<Record>): StatsResult {
            val income = DoubleArray(buckets.size)
            val expense = DoubleArray(buckets.size)

            for (record in records) {
                val index = buckets.indexOfBucket(record.time)
                if (index < 0) continue
                if (record.type == RecordType.INCOME) {
                    income[index] += record.amount
                } else {
                    expense[index] += record.amount
                }
            }

            return StatsResult(
                labels = buckets.map { it.label },
                income = income.map { it.toFloat() },
                expense = expense.map { it.toFloat() },
                totalIncome = income.sum(),
                totalExpense = expense.sum()
            )
        }

        /** 桶区间连续且有序，二分定位，避免对每条记录逐桶扫描。 */
        private fun List<Bucket>.indexOfBucket(time: Long): Int {
            var lo = 0
            var hi = size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val bucket = this[mid]
                when {
                    time < bucket.startMs -> hi = mid - 1
                    time >= bucket.endMs -> lo = mid + 1
                    else -> return mid
                }
            }
            return -1
        }
    }
}
