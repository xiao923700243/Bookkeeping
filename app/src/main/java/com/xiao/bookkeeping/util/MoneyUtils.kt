package com.xiao.bookkeeping.util

import com.xiao.bookkeeping.model.RecordType
import java.util.Locale

/** 金额的格式化与解析。 */
object MoneyUtils {

    /** 千分位、两位小数，如 `1,234.50`。 */
    fun format(amount: Double): String = String.format(Locale.CHINA, "%,.2f", amount)

    /**
     * 回填输入框用的纯数字形式，不带千分位。
     *
     * 带逗号回填后再交给 [parse] 虽然也能解析，但用户看到 `1,234.00` 会以为格式错了。
     * 整数去掉多余的 `.00`，非整数保留两位。
     */
    fun formatPlain(amount: Double): String =
        if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            String.format(Locale.CHINA, "%.2f", amount)
        }

    /**
     * 缩略金额，给日历格子这种窄地方用。
     *
     * 天档的格子只有屏幕宽度的 1/7，`1,234.00` 这种完整格式塞不下。整元就够看，
     * 上万换算成「万」——兼职一天几十到几百，偶尔过万，两档足够覆盖。
     */
    fun formatCompact(amount: Double): String =
        if (amount >= WAN) {
            String.format(Locale.CHINA, "%.1f万", amount / WAN)
        } else {
            Math.round(amount).toString()
        }

    /** 带方向的展示形式：收入前缀 `+`，支出前缀 `-`。 */
    fun formatSigned(amount: Double, type: RecordType): String =
        (if (type == RecordType.INCOME) "+" else "-") + format(amount)

    /**
     * 解析用户输入的金额，返回 null 表示输入不合法。
     *
     * 金额一律存正数、方向由类型决定，所以这里拒绝 0、负数和 NaN/Infinity。
     *
     * 统一量化到「分」：输入框允许输入任意位小数，若不量化，一条 1.999 的记录
     * 编辑时会回填成 `2.00`，用户不改任何东西点保存就把原值改掉了。
     * 量化后保证「界面显示多少，库里就存多少」。
     */
    fun parse(input: String): Double? =
        input.trim()
            .replace(",", "")
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0 }
            ?.let { roundToCent(it) }
            // 四舍五入到分后可能变成 0（例如输入 0.001）
            ?.takeIf { it > 0 }

    private fun roundToCent(value: Double): Double = Math.round(value * 100) / 100.0

    private const val WAN = 10_000.0
}
