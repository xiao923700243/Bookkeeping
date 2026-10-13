package com.xiao.bookkeeping.util

import com.xiao.bookkeeping.model.Record
import com.xiao.bookkeeping.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 备份格式的读写。
 *
 * 这里测的是**对外契约**：备份文件会离开本机、经过各种转存，回来时什么都可能有，
 * 所以每一条拒绝规则都要挡住，并且挡它的理由要具体到能翻译成一句给用户看的话。
 */
class BackupCodecTest {

    // ---- 往返 ----

    @Test
    fun `round trip keeps every field`() {
        val original = listOf(
            record(type = RecordType.INCOME, amount = 150.0, remark = "外卖", time = noonOf12th),
            record(type = RecordType.EXPENSE, amount = 0.01, remark = "", time = morningOf1st)
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(original, EXPORTED_AT)).records

        // 不能直接比 data class：导出不带 id，解出来的 id 恒为 0，而原记录带库里的 id
        assertEquals(original.size, decoded.size)
        original.zip(decoded).forEach { (before, after) ->
            assertEquals(before.type, after.type)
            assertEquals(before.amount, after.amount, DELTA)
            assertEquals(before.remark, after.remark)
            assertEquals(before.time, after.time)
        }
    }

    @Test
    fun `round trip keeps remarks with quotes backslashes newlines and emoji`() {
        // 备注是自由文本，引号和换行都在用户能打出来的范围里，转义错了就会串行
        val remark = "他说\"午饭 30\"\n第二行\\结尾 🍜"
        val original = listOf(record(remark = remark))

        val decoded = BackupCodec.decode(BackupCodec.encode(original, EXPORTED_AT)).records

        assertEquals(remark, decoded.single().remark)
    }

    @Test
    fun `empty ledger round trips`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(emptyList(), EXPORTED_AT))

        assertTrue(decoded.records.isEmpty())
        assertEquals(EXPORTED_AT, decoded.exportedAt)
    }

    @Test
    fun `encoded file carries the format handshake and version`() {
        val text = BackupCodec.encode(listOf(record()), EXPORTED_AT)

        // 握手字段和版本是「将来能不能读」的钥匙，写死了才好在这条用例上报出来
        assertTrue(text, text.contains("\"${BackupCodec.FORMAT}\""))
        assertTrue(text, text.contains("\"version\": ${BackupCodec.VERSION}"))
        assertEquals(EXPORTED_AT, BackupCodec.decode(text).exportedAt)
    }

    @Test
    fun `unknown fields are ignored, including a stray id`() {
        // 多余的字段要放行：将来加分类、账户时，旧版本读新文件不该整份失败
        val text = fileWith(
            """{"id":42,"type":1,"amount":20.0,"remark":"地铁","time":$noonOf12th,""" +
                """
                "category":"交通"}"""
        )

        val decoded = BackupCodec.decode(text).records.single()

        assertEquals(RecordType.INCOME, decoded.type)
        assertEquals(20.0, decoded.amount, DELTA)
        assertEquals("地铁", decoded.remark)
    }

    @Test
    fun `a leading BOM is tolerated`() {
        // 记事本存 UTF-8 会带 BOM，用户打开备份看过一眼再存回去就会带上
        val text = "\uFEFF" + fileWith("""{"type":0,"amount":1.0,"time":$morningOf1st}""")

        assertEquals(1, BackupCodec.decode(text).records.size)
    }

    @Test
    fun `numeric strings are accepted`() {
        // Gson 默认做数字字符串的宽松转换（"150" → 150.0）。用户手改备份时容易写成这样，
        // 值本身没有歧义，收下比甩他一句「文件坏了」强
        val text = fileWith("""{"type":"1","amount":"150.5","time":"$morningOf1st"}""")

        val decoded = BackupCodec.decode(text).records.single()
        assertEquals(RecordType.INCOME, decoded.type)
        assertEquals(150.5, decoded.amount, DELTA)
    }

    @Test
    fun `a missing remark reads as empty`() {
        assertEquals("", BackupCodec.decode(fileWith("""{"type":0,"amount":1.0,"time":$morningOf1st}"""))
            .records.single().remark)
    }

    // ---- 整个文件不合法 ----

    @Test
    fun `empty text is malformed`() {
        assertEquals(BackupProblem.MALFORMED, problemOf(""))
    }

    @Test
    fun `blank text is malformed`() {
        assertEquals(BackupProblem.MALFORMED, problemOf("   \n\t "))
    }

    @Test
    fun `truncated json is malformed`() {
        // 拷到一半、存到一半都会得到这种文件
        assertEquals(BackupProblem.MALFORMED, problemOf("""{"format":"${BackupCodec.FORMAT}","ver"""))
    }

    @Test
    fun `a json array is not a backup`() {
        // 是合法 JSON，但不是我们的结构——对用户来说是「选错文件了」，不是「文件坏了」
        assertEquals(BackupProblem.NOT_BACKUP, problemOf("""[{"type":1}]"""))
    }

    @Test
    fun `a json string is not a backup`() {
        assertEquals(BackupProblem.NOT_BACKUP, problemOf("\"hello\""))
    }

    @Test
    fun `a foreign json object is not a backup`() {
        assertEquals(BackupProblem.NOT_BACKUP, problemOf("""{"name":"其他应用的设置"}"""))
    }

    @Test
    fun `a wrong format value is not a backup`() {
        assertEquals(
            BackupProblem.NOT_BACKUP,
            problemOf("""{"format":"something-else","version":1,"exportedAt":$EXPORTED_AT,"records":[]}""")
        )
    }

    @Test
    fun `a newer version is rejected`() {
        // 新版本可能带了本版本读不懂的字段，照读会读残，宁可拒绝
        val text = fileWith("", version = BackupCodec.VERSION + 1)

        assertEquals(BackupProblem.NEWER_VERSION, problemOf(text))
    }

    @Test
    fun `a non positive version is malformed`() {
        assertEquals(BackupProblem.MALFORMED, problemOf(fileWith("", version = 0)))
    }

    @Test
    fun `a missing version is malformed`() {
        assertEquals(
            BackupProblem.MALFORMED,
            problemOf("""{"format":"${BackupCodec.FORMAT}","exportedAt":$EXPORTED_AT,"records":[]}""")
        )
    }

    @Test
    fun `a non numeric version is malformed`() {
        assertEquals(
            BackupProblem.MALFORMED,
            problemOf("""{"format":"${BackupCodec.FORMAT}","version":"最新","exportedAt":$EXPORTED_AT,"records":[]}""")
        )
    }

    @Test
    fun `a missing records array is malformed`() {
        assertEquals(
            BackupProblem.MALFORMED,
            problemOf("""{"format":"${BackupCodec.FORMAT}","version":1,"exportedAt":$EXPORTED_AT}""")
        )
    }

    @Test
    fun `a records value that is not an array is malformed`() {
        assertEquals(
            BackupProblem.MALFORMED,
            problemOf("""{"format":"${BackupCodec.FORMAT}","version":1,"exportedAt":$EXPORTED_AT,"records":3}""")
        )
    }

    // ---- 单条记录不合法 ----

    @Test
    fun `an unknown type code is a bad record`() {
        // 不能兜底成支出：那等于把外面文件里的脏数据当成正经账目写进库
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"type":2,"amount":1.0,"time":$morningOf1st}""")))
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"type":-1,"amount":1.0,"time":$morningOf1st}""")))
    }

    @Test
    fun `a missing field is a bad record`() {
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"amount":1.0,"time":$morningOf1st}""")))
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"type":0,"time":$morningOf1st}""")))
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"type":0,"amount":1.0}""")))
    }

    @Test
    fun `a non positive amount is a bad record`() {
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"type":0,"amount":0,"time":$morningOf1st}""")))
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"type":0,"amount":-1.5,"time":$morningOf1st}""")))
    }

    @Test
    fun `a non finite amount is a bad record`() {
        // JSON 里没有 NaN/Infinity 字面量，但 1e400 能溢出成 Infinity，会把所有求和变成 NaN
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"type":0,"amount":1e400,"time":$morningOf1st}""")))
    }

    @Test
    fun `a non positive time is a bad record`() {
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"type":0,"amount":1.0,"time":0}""")))
        assertEquals(BackupProblem.BAD_RECORD, problemOf(fileWith("""{"type":0,"amount":1.0,"time":-1}""")))
    }

    @Test
    fun `a bad record reports which one it is`() {
        // 下标要能指到出问题的那条，用户才好在文件里定位
        val text = fileWith(
            """{"type":0,"amount":1.0,"time":$morningOf1st},""" +
                """{"type":0,"amount":1.0,"time":$noonOf12th},""" +
                """{"type":7,"amount":1.0,"time":$noonOf12th}"""
        )

        assertEquals(2, badRecordIndex(text))
    }

    // ---- 工具 ----

    private fun record(
        type: RecordType = RecordType.EXPENSE,
        amount: Double = 10.0,
        remark: String = "",
        time: Long = morningOf1st
    ) = Record(type = type, amount = amount, remark = remark, time = time)

    /** 拼一个带指定记录体的备份文件。 */
    private fun fileWith(recordsJson: String, version: Int = BackupCodec.VERSION) =
        """{"format":"${BackupCodec.FORMAT}","version":$version,"exportedAt":$EXPORTED_AT,""" +
            """"records":[$recordsJson]}"""

    private fun problemOf(text: String): BackupProblem =
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(text) }.problem

    private fun badRecordIndex(text: String): Int =
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(text) }.index

    private companion object {
        const val DELTA = 0.0001
        const val EXPORTED_AT = 1_760_000_000_000L
        val morningOf1st = DateUtils.of(2026, Calendar.OCTOBER, 1, 9, 0)
        val noonOf12th = DateUtils.of(2026, Calendar.OCTOBER, 12, 12, 0)
    }
}
