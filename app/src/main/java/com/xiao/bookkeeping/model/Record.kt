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
        /** 未知 code 一律当支出——只有库里的脏数据会走到这里，读出来总比读不出来强。 */
        fun fromCode(code: Int): RecordType = fromCodeOrNull(code) ?: EXPENSE

        /**
         * 严格版：未知 code 返回 null。
         *
         * 备份导入必须用这个版本：外面来的文件不该被静默兜底成支出，
         * 那样坏数据会当成正经账目写进库。
         */
        fun fromCodeOrNull(code: Int): RecordType? = values().firstOrNull { it.code == code }
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
