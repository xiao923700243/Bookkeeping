package com.xiao.bookkeeping.main

import android.content.Context
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.base.BasePresenter
import com.xiao.bookkeeping.model.RecordDao
import com.xiao.bookkeeping.model.RecordType
import com.xiao.bookkeeping.util.DateUtils

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

    override fun onError(t: Throwable) {
        super.onError(t)
        // 查询和删除共用这条兜底提示，不臆断具体是哪个操作失败了
        view?.showMessage(appContext.getString(R.string.error_operation_failed))
    }
}
