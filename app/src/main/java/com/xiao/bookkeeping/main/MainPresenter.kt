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

    override fun loadRecords() {
        val now = System.currentTimeMillis()
        val todayStart = DateUtils.dayStart(now)
        val todayEndExclusive = DateUtils.dayEndExclusive(now)

        runAsync(
            task = {
                val all = dao.queryAll()
                val today = dao.queryBetween(todayStart, todayEndExclusive)
                all to today
            },
            onSuccess = { (all, today) ->
                var income = 0.0
                var expense = 0.0
                for (record in today) {
                    if (record.type == RecordType.INCOME) income += record.amount
                    else expense += record.amount
                }
                view?.let {
                    it.showRecords(all)
                    it.showSummary(income, expense)
                    it.showEmpty(all.isEmpty())
                }
            }
        )
    }

    override fun deleteRecord(id: Long) {
        runAsync(
            task = { dao.delete(id) },
            onSuccess = {
                // 删除后整表重查：汇总和空态都要跟着变，局部移除反而容易漏更新
                view?.showMessage(appContext.getString(R.string.deleted))
                loadRecords()
            }
        )
    }

    override fun onError(t: Throwable) {
        super.onError(t)
        // 查询和删除共用这条兜底提示，不臆断具体是哪个操作失败了
        view?.showMessage(appContext.getString(R.string.error_operation_failed))
    }
}
