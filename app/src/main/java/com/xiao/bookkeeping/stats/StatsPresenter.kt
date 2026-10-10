package com.xiao.bookkeeping.stats

import android.content.Context
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.base.BasePresenter
import com.xiao.bookkeeping.model.RecordDao
import com.xiao.bookkeeping.model.StatsPeriod
import com.xiao.bookkeeping.model.StatsResult
import com.xiao.bookkeeping.util.DateUtils

class StatsPresenter(context: Context) :
    BasePresenter<StatsContract.View>(), StatsContract.Presenter {

    private val appContext = context.applicationContext
    private val dao = RecordDao(appContext)

    override fun loadStats(period: StatsPeriod) {
        runAsync(
            task = {
                val buckets = period.buckets()
                // 只按窗口范围取数，分桶在内存里做——见 StatsResult.of 的说明
                val records = dao.queryBetween(buckets.first().startMs, buckets.last().endMs)
                StatsResult.of(buckets, records) to
                    DateUtils.formatRange(buckets.first().startMs, buckets.last().endMs)
            },
            onSuccess = { (result, rangeText) ->
                view?.showStats(result, rangeText)
            }
        )
    }

    override fun onError(t: Throwable) {
        super.onError(t)
        view?.showMessage(appContext.getString(R.string.error_operation_failed))
    }
}
