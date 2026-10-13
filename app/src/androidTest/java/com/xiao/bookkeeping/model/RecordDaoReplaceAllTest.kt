package com.xiao.bookkeeping.model

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `replaceAll` 的仪器化测试。
 *
 * 它是导入功能的破坏性那一步——先清空再写入，一旦语义不对就是在删用户的账本，
 * 而这段逻辑要真实 SQLite 才测得出来（本工程的 JVM 单测里没有 Robolectric）。
 *
 * 这个测试跑在应用真实的库上，所以前后各截一次快照把数据放回去，
 * 免得谁在装了真账本的手机上跑一次 `connectedAndroidTest` 就把账目清没了。
 *
 * 方法名只能写下划线不能写空格：带空格的 backtick 名字是 JVM 单测的习惯，
 * 但 D8 只在 DEX 040（Android 15 起）才允许方法名带空格，本工程 minSdk 24，编译期就挂。
 */
@RunWith(AndroidJUnit4::class)
class RecordDaoReplaceAllTest {

    private val dao = RecordDao(ApplicationProvider.getApplicationContext())

    private lateinit var snapshot: List<Record>

    @Before
    fun snapshotExistingRecords() {
        snapshot = dao.queryAll()
    }

    @After
    fun restoreExistingRecords() {
        dao.replaceAll(snapshot)
    }

    @Test
    fun replaceAll_writes_exactly_the_given_records() {
        val records = listOf(
            record(RecordType.INCOME, 150.0, "外卖", 1_759_132_800_000L),
            record(RecordType.EXPENSE, 0.01, "", 1_759_219_200_000L),
            record(RecordType.INCOME, 9999.99, "带\"引号\"和\n换行", 1_759_305_600_000L)
        )

        assertEquals(records.size, dao.replaceAll(records))

        val written = dao.queryAll()
        assertEquals(records.size, written.size)
        records.zip(written).forEach { (expected, actual) ->
            assertEquals(expected.type, actual.type)
            assertEquals(expected.amount, actual.amount, DELTA)
            assertEquals(expected.remark, actual.remark)
            assertEquals(expected.time, actual.time)
        }
    }

    @Test
    fun replaceAll_drops_what_was_there_before() {
        dao.replaceAll(
            listOf(
                record(RecordType.INCOME, 1.0, "旧的", 1_759_132_800_000L),
                record(RecordType.INCOME, 2.0, "也是旧的", 1_759_219_200_000L)
            )
        )

        // 换成更少的一条：旧的两条必须一条不剩，否则「覆盖」就成了「追加」
        dao.replaceAll(listOf(record(RecordType.EXPENSE, 3.0, "新的", 1_759_305_600_000L)))

        val written = dao.queryAll()
        assertEquals(1, written.size)
        assertEquals("新的", written.single().remark)
    }

    @Test
    fun replaceAll_with_an_empty_list_empties_the_ledger() {
        dao.replaceAll(listOf(record(RecordType.INCOME, 1.0, "先有", 1_759_132_800_000L)))

        assertEquals(0, dao.replaceAll(emptyList()))

        assertTrue(dao.queryAll().isEmpty())
    }

    @Test
    fun the_ledger_is_usable_after_a_replaceAll() {
        // 覆盖导入之后用户还会继续记账，事务没收拾干净的话这里会露馅
        dao.replaceAll(listOf(record(RecordType.INCOME, 1.0, "导入的", 1_759_132_800_000L)))

        val id = dao.insert(record(RecordType.EXPENSE, 2.0, "导入后新记的", 1_759_219_200_000L))

        assertTrue("insert 返回 $id", id > 0)
        assertEquals(2, dao.queryAll().size)
    }

    private fun record(
        type: RecordType,
        amount: Double,
        remark: String,
        time: Long
    ) = Record(type = type, amount = amount, remark = remark, time = time)

    private companion object {
        const val DELTA = 0.0001
    }
}
