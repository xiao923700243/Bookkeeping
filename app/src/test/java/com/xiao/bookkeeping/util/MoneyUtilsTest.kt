package com.xiao.bookkeeping.util

import com.xiao.bookkeeping.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyUtilsTest {

    @Test
    fun `parses plain decimal`() {
        assertEquals(12.5, MoneyUtils.parse("12.5")!!, 0.0001)
        assertEquals(100.0, MoneyUtils.parse("100")!!, 0.0001)
    }

    @Test
    fun `parses input with thousand separators and surrounding spaces`() {
        assertEquals(1234.5, MoneyUtils.parse(" 1,234.50 ")!!, 0.0001)
    }

    @Test
    fun `rejects non positive input`() {
        assertNull(MoneyUtils.parse("0"))
        assertNull(MoneyUtils.parse("0.00"))
        assertNull(MoneyUtils.parse("-5"))
        assertNull(MoneyUtils.parse(""))
        assertNull(MoneyUtils.parse("abc"))
        assertNull(MoneyUtils.parse("1.2.3"))
    }

    @Test
    fun `signed formatting carries the direction`() {
        assertEquals("+1,234.50", MoneyUtils.formatSigned(1234.5, RecordType.INCOME))
        assertEquals("-1,234.50", MoneyUtils.formatSigned(1234.5, RecordType.EXPENSE))
    }

    @Test
    fun `plain formatting drops trailing zeros for input fields`() {
        assertEquals("100", MoneyUtils.formatPlain(100.0))
        assertEquals("1234", MoneyUtils.formatPlain(1234.0))
        assertEquals("100.50", MoneyUtils.formatPlain(100.5))
    }

    @Test
    fun `parse quantizes to cents so display matches what is stored`() {
        // 不量化的话，1.999 存进去、回填成 2.00，用户不动任何东西保存就把原值改了
        assertEquals(2.0, MoneyUtils.parse("1.999")!!, 0.0001)
        assertEquals(1.23, MoneyUtils.parse("1.234")!!, 0.0001)
        assertEquals(1.24, MoneyUtils.parse("1.235")!!, 0.0001)
    }

    @Test
    fun `input that rounds down to zero is rejected`() {
        assertNull(MoneyUtils.parse("0.001"))
        assertNull(MoneyUtils.parse("0.004"))
    }

    @Test
    fun `plain formatting round trips through parse`() {
        // 回填输入框后再保存，金额不能变形
        for (amount in listOf(0.01, 9.99, 100.0, 1234.56)) {
            val roundTripped = MoneyUtils.parse(MoneyUtils.formatPlain(amount))!!
            assertEquals(amount, roundTripped, 0.0001)
        }
    }

    @Test
    fun `compact formatting rounds to whole yuan below ten thousand`() {
        assertEquals("0", MoneyUtils.formatCompact(0.0))
        assertEquals("150", MoneyUtils.formatCompact(150.0))
        assertEquals("150", MoneyUtils.formatCompact(150.4))
        assertEquals("151", MoneyUtils.formatCompact(150.5))
        // 四位数不换算，格子里「1234」比「0.1万」好认
        assertEquals("1234", MoneyUtils.formatCompact(1234.0))
        assertEquals("9999", MoneyUtils.formatCompact(9999.4))
    }

    @Test
    fun `compact formatting switches to ten thousands at the boundary`() {
        assertEquals("1.0万", MoneyUtils.formatCompact(10_000.0))
        assertEquals("1.5万", MoneyUtils.formatCompact(15_000.0))
        assertEquals("12.3万", MoneyUtils.formatCompact(123_456.0))
    }
}
