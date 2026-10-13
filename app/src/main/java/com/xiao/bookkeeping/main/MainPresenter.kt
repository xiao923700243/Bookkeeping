package com.xiao.bookkeeping.main

import android.content.Context
import android.net.Uri
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.base.BasePresenter
import com.xiao.bookkeeping.model.RecordDao
import com.xiao.bookkeeping.model.RecordType
import com.xiao.bookkeeping.util.Backup
import com.xiao.bookkeeping.util.BackupCodec
import com.xiao.bookkeeping.util.BackupFormatException
import com.xiao.bookkeeping.util.BackupIo
import com.xiao.bookkeeping.util.BackupProblem
import com.xiao.bookkeeping.util.DateUtils
import java.io.IOException

class MainPresenter(context: Context) :
    BasePresenter<MainContract.View>(), MainContract.Presenter {

    private val appContext = context.applicationContext
    private val dao = RecordDao(appContext)

    /**
     * 当前查看的自然日，由 [loadRecords] 记下。
     *
     * 删除后要按同一天重查——删的是哪天的记录，视图就该留在哪天。
     */
    private var selectedDayMs: Long = DateUtils.dayStart(System.currentTimeMillis())

    /** 已校验通过、等用户点头的备份。null 表示没有待导入的备份。 */
    private var pendingImport: Backup? = null

    /** 导入进行中。写库是异步的，回调回来之前用户完全可能再选一份文件。 */
    private var importing = false

    override fun loadRecords(dayMs: Long) {
        selectedDayMs = dayMs
        val dayStart = DateUtils.dayStart(dayMs)
        val dayEndExclusive = DateUtils.dayEndExclusive(dayMs)

        runAsync(
            task = { dao.queryBetween(dayStart, dayEndExclusive, descending = true) },
            onSuccess = { records ->
                var income = 0.0
                var expense = 0.0
                for (record in records) {
                    if (record.type == RecordType.INCOME) income += record.amount
                    else expense += record.amount
                }
                view?.let {
                    it.showRecords(records)
                    it.showSummary(income, expense)
                    it.showEmpty(records.isEmpty())
                }
            }
        )
    }

    override fun deleteRecord(id: Long) {
        runAsync(
            task = { dao.delete(id) },
            onSuccess = {
                // 删除后整日重查：汇总和空态都要跟着变，局部移除反而容易漏更新
                view?.showMessage(appContext.getString(R.string.deleted))
                loadRecords(selectedDayMs)
            }
        )
    }

    override fun exportBackup(uri: Uri) {
        runAsync(
            task = {
                val records = dao.queryAll()
                val text = BackupCodec.encode(records, System.currentTimeMillis())
                try {
                    BackupIo.writeText(appContext.contentResolver, uri, text)
                    ExportResult.Exported(records.size)
                } catch (e: IOException) {
                    // 用户挑的位置写不进去（只读、空间不够、provider 不支持）是能预期的失败，
                    // 不该掉进 onError 的「操作失败」里让人猜是哪个操作
                    ExportResult.IoFailed
                }
            },
            onSuccess = { result ->
                when (result) {
                    is ExportResult.Exported -> view?.showMessage(
                        appContext.getString(R.string.backup_exported, result.count)
                    )

                    ExportResult.IoFailed -> view?.showMessage(
                        appContext.getString(R.string.backup_error_io)
                    )
                }
            }
        )
    }

    override fun prepareImport(uri: Uri) {
        runAsync(
            task = {
                val resolver = appContext.contentResolver
                // 文件名只用来在确认弹窗里给用户看一眼，取不到不该让整次导入失败
                val fileName = BackupIo.displayName(resolver, uri)
                try {
                    val backup = BackupCodec.decode(BackupIo.readText(resolver, uri))
                    ImportReadResult.Ready(fileName, backup)
                } catch (e: BackupIo.TooLargeException) {
                    ImportReadResult.TooLarge
                } catch (e: BackupFormatException) {
                    ImportReadResult.Rejected(e.problem, e.index)
                } catch (e: IOException) {
                    ImportReadResult.IoFailed
                }
            },
            onSuccess = { result ->
                when (result) {
                    is ImportReadResult.Ready -> {
                        // 整份文件到这里已经校验干净了，写库要等用户在弹窗里点头
                        pendingImport = result.backup
                        view?.confirmImport(
                            result.fileName,
                            result.backup.records.size,
                            result.backup.exportedAt
                        )
                    }

                    is ImportReadResult.Rejected -> view?.showMessage(
                        importProblemMessage(result.problem, result.index)
                    )

                    ImportReadResult.TooLarge -> view?.showMessage(
                        appContext.getString(R.string.backup_error_too_large)
                    )

                    ImportReadResult.IoFailed -> view?.showMessage(
                        appContext.getString(R.string.backup_error_io)
                    )
                }
            }
        )
    }

    override fun commitImport() {
        // 配置变更会把 Presenter 连同待导入的备份一起丢掉，那时确认弹窗也没了；
        // 万一还是走到这里，就当没这回事——绝不能让一份空备份把账本清掉
        val backup = pendingImport ?: return
        if (importing) return
        importing = true

        runAsync(
            task = { dao.replaceAll(backup.records) },
            onSuccess = { count ->
                importing = false
                pendingImport = null
                view?.showMessage(appContext.getString(R.string.backup_imported, count))
                // 导入后整日重查，跟 deleteRecord 一个路子：汇总和空态都要跟着变
                loadRecords(selectedDayMs)
            }
        )
    }

    override fun onError(t: Throwable) {
        super.onError(t)
        // 写库失败也要把闸门放开，否则用户重新选一份文件后就再也确认不了了
        importing = false
        // 查询和删除共用这条兜底提示，不臆断具体是哪个操作失败了
        view?.showMessage(appContext.getString(R.string.error_operation_failed))
    }

    /** 把校验失败的原因翻成一句给用户看的话。 */
    private fun importProblemMessage(problem: BackupProblem, index: Int): String = when (problem) {
        BackupProblem.MALFORMED -> appContext.getString(R.string.backup_error_malformed)
        BackupProblem.NOT_BACKUP -> appContext.getString(R.string.backup_error_not_backup)
        BackupProblem.NEWER_VERSION -> appContext.getString(R.string.backup_error_newer_version)
        // 下标从 0 起，说给用户听得转成第几条
        BackupProblem.BAD_RECORD -> appContext.getString(R.string.backup_error_bad_record, index + 1)
    }

    /**
     * 导出任务的结果。
     *
     * 能预期的失败都在这里表达清楚，别让它们掉进 [onError] 那句通用的「操作失败」——
     * 用户拿着一句「操作失败」不知道该重试还是该换个位置存。
     */
    private sealed interface ExportResult {
        data class Exported(val count: Int) : ExportResult

        data object IoFailed : ExportResult
    }

    /** 导入读取阶段的结果。走到 [Ready] 为止，数据库一个字节都没动过。 */
    private sealed interface ImportReadResult {
        data class Ready(val fileName: String, val backup: Backup) : ImportReadResult

        data class Rejected(val problem: BackupProblem, val index: Int) : ImportReadResult

        data object TooLarge : ImportReadResult

        data object IoFailed : ImportReadResult
    }
}
