package com.xiao.bookkeeping.main

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.model.Record
import com.xiao.bookkeeping.model.RecordDao
import com.xiao.bookkeeping.model.RecordType
import com.xiao.bookkeeping.util.BackupCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 导出/导入的端到端：Presenter + 真实的 ContentResolver + 真实的 SQLite。
 *
 * 中间这层只有真机跑得出来（JVM 单测里没有 ContentResolver），而它正好担着
 * 最要紧的一条：**文件不合法时一个字节都不能写库**。用户点的是「覆盖导入」，
 * 校验漏一步就是把他整本账删了。
 *
 * 方法名只能写下划线不能写空格：带空格的 backtick 名字是 JVM 单测的习惯，
 * 但 D8 只在 DEX 040（Android 15 起）才允许方法名带空格，本工程 minSdk 24，编译期就挂。
 *
 * 跑在应用真实的库上，所以前后各截一次快照把数据放回去。
 */
@RunWith(AndroidJUnit4::class)
class MainPresenterBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dao = RecordDao(context)
    private val view = RecordingView()
    private val presenter = MainPresenter(context)

    private lateinit var snapshot: List<Record>

    @Before
    fun setUp() {
        snapshot = dao.queryAll()
        presenter.attach(view)
    }

    @After
    fun tearDown() {
        presenter.detach()
        dao.replaceAll(snapshot)
    }

    @Test
    fun export_then_import_brings_the_ledger_back() {
        val original = listOf(
            record(RecordType.INCOME, 150.0, "外卖", AUGUST),
            record(RecordType.EXPENSE, 0.01, "", SEPTEMBER)
        )
        dao.replaceAll(original)
        val file = backupFile("round-trip.json")

        presenter.exportBackup(Uri.fromFile(file))
        awaitAnyMessage("导出结果")

        // 导出的文件本身就该是一份能读回来的备份
        assertEquals(original.size, BackupCodec.decode(file.readText(Charsets.UTF_8)).records.size)

        // 清空账本，再用刚导出的那份恢复
        dao.replaceAll(emptyList())
        presenter.prepareImport(Uri.fromFile(file))
        val confirm = awaitConfirm()

        assertEquals(original.size, confirm.count)

        presenter.commitImport()
        awaitAnyMessage("导入结果")

        val restored = dao.queryAll()
        assertEquals(original.map { it.remark }, restored.map { it.remark })
        assertEquals(original.map { it.type }, restored.map { it.type })
    }

    @Test
    fun a_foreign_file_leaves_the_ledger_untouched() {
        val kept = record(RecordType.INCOME, 88.0, "这条不能被弄丢", AUGUST)
        dao.replaceAll(listOf(kept))
        val file = backupFile("foreign.json").apply { writeText("{\"name\":\"别的应用的配置\"}") }

        presenter.prepareImport(Uri.fromFile(file))

        awaitMessage(context.getString(R.string.backup_error_not_backup))
        // 关键断言：被拒绝的文件既不该弹确认，也绝不能让账本少一条
        assertFalse(view.importConfirmRequested)
        assertEquals(1, dao.queryAll().size)
        assertEquals("这条不能被弄丢", dao.queryAll().single().remark)
    }

    @Test
    fun a_truncated_backup_leaves_the_ledger_untouched() {
        val kept = record(RecordType.INCOME, 88.0, "半份备份不能覆盖它", AUGUST)
        dao.replaceAll(listOf(kept))
        val full = BackupCodec.encode(
            listOf(
                record(RecordType.INCOME, 1.0, "第一条", AUGUST),
                record(RecordType.EXPENSE, 2.0, "第二条", SEPTEMBER)
            ),
            exportedAt = AUGUST
        )
        val file = backupFile("truncated.json").apply { writeText(full.take(full.length / 2)) }

        presenter.prepareImport(Uri.fromFile(file))

        awaitMessage(context.getString(R.string.backup_error_malformed))
        assertFalse(view.importConfirmRequested)
        assertEquals(1, dao.queryAll().size)
        assertEquals("半份备份不能覆盖它", dao.queryAll().single().remark)
    }

    @Test
    fun an_empty_backup_reaches_the_view_with_zero_records() {
        dao.replaceAll(listOf(record(RecordType.INCOME, 88.0, "先有一条", AUGUST)))
        val file = backupFile("empty.json")
            .apply { writeText(BackupCodec.encode(emptyList(), exportedAt = AUGUST)) }

        presenter.prepareImport(Uri.fromFile(file))

        // 条数要如实报到 View：界面靠 0 认出「这份备份会把账本清空」，单独给一句警告
        assertEquals(0, awaitConfirm().count)

        presenter.commitImport()
        awaitAnyMessage("导入结果")
        assertEquals(0, dao.queryAll().size)
    }

    // ---- 工具 ----

    /** 备份文件落在 cacheDir 里，用 file URI 喂给 ContentResolver，省掉整套选择器。 */
    private fun backupFile(name: String) = File(context.cacheDir, name)

    /** 等一条提示并返回正文。只关心「有结果回来了」时用它。 */
    private fun awaitAnyMessage(what: String): String = nextEvent(what).removePrefix("message:")

    /** 等一条提示，并断言正文正好是 [expected]——失败路径要认准是哪句提示。 */
    private fun awaitMessage(expected: String) {
        assertEquals(expected, awaitAnyMessage("提示「$expected」"))
    }

    /** 等导入确认被请求，返回它带的三个参数。 */
    private fun awaitConfirm(): ImportConfirmRequest {
        assertEquals("confirmImport", nextEvent("导入确认"))
        return assertNotNull("确认回调没记下参数", view.importConfirm).let { view.importConfirm!! }
    }

    /** 取一个 Presenter 回调；超时就判失败，不让异步没回来时测试假装通过。 */
    private fun nextEvent(what: String): String {
        val event = view.events.poll(5, TimeUnit.SECONDS)
        assertNotNull("等「$what」超时，回调没来", event)
        return event!!
    }

    private fun record(type: RecordType, amount: Double, remark: String, time: Long) =
        Record(type = type, amount = amount, remark = remark, time = time)

    private companion object {
        const val AUGUST = 1_754_000_000_000L
        const val SEPTEMBER = 1_756_000_000_000L
    }

    data class ImportConfirmRequest(val fileName: String, val count: Int, val exportedAt: Long)

    /** 把回调记进队列，测试线程按顺序取——异步结果用队列接比用锁存器好复位。 */
    private class RecordingView : MainContract.View {

        val events = LinkedBlockingQueue<String>()

        @Volatile
        var importConfirm: ImportConfirmRequest? = null

        @Volatile
        var importConfirmRequested = false

        override fun showRecords(records: List<Record>) = Unit

        override fun showSummary(income: Double, expense: Double) = Unit

        override fun showEmpty(empty: Boolean) = Unit

        override fun showMessage(message: String) {
            events.put("message:$message")
        }

        override fun confirmDelete(record: Record) = Unit

        override fun confirmImport(fileName: String, count: Int, exportedAt: Long) {
            importConfirmRequested = true
            importConfirm = ImportConfirmRequest(fileName, count, exportedAt)
            events.put("confirmImport")
        }
    }
}
