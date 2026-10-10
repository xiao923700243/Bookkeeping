package com.xiao.bookkeeping.model

/**
 * 记账类型。支出与收入是对等的两个类型，只有方向不同。
 *
 * [code] 是写入数据库的稳定值，不要依赖 enum 的 ordinal。
 */
enum class RecordType(val code: Int) {
    EXPENSE(0),
    INCOME(1);

    companion object {
        fun fromCode(code: Int): RecordType =
            values().firstOrNull { it.code == code } ?: EXPENSE
    }
}

/**
 * 一条记账记录。
 *
 * [amount] 恒为正数，收支方向由 [type] 决定，这样聚合求和时不必再处理正负号。
 * [remark] 是可选备注，空串表示没有填写。
 */
data class Record(
    val id: Long = 0L,
    val type: RecordType,
    val amount: Double,
    val remark: String = "",
    val time: Long
)
