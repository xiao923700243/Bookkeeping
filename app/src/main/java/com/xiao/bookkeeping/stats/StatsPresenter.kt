package com.xiao.bookkeeping.stats

import android.content.Context
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.base.BasePresenter
import com.xiao.bookkeeping.model.RecordDao
import com.xiao.bookkeeping.model.StatsCalendar
import com.xiao.bookkeeping.model.StatsResult
import com.xiao.bookkeeping.model.StatsScale
import com.xiao.bookkeeping.util.DateUtils

class StatsPresenter(context: Context) :
    BasePresenter<StatsContract.View>(), StatsContract.Presenter {

    private val appContext = context.applicationContext
    private val dao = RecordDao(appContext)

    override fun loadStats(scale: StatsScale, anchor: Long) {
        runAsync(
            // 只查这一页要用的区间；分格在内存里做，见 StatsCalendar 的说明
            task = { build(scale, anchor, System.currentTimeMillis()) },
            onSuccess = { view?.showStats(it) }
        )
    }

    private fun build(scale: StatsScale, anchor: Long, now: Long): StatsResult = when (scale) {
        StatsScale.DAY -> {
            val start = DateUtils.monthStart(anchor)
            StatsCalendar.month(anchor, dao.queryBetween(start, DateUtils.monthEndExclusive(start)), now)
        }

        StatsScale.MONTH -> {
            val start = DateUtils.yearStart(anchor)
            StatsCalendar.year(anchor, dao.queryBetween(start, DateUtils.yearEndExclusive(start)), now)
        }

        StatsScale.YEAR -> {
            // 年档得知道账最早记到哪年，翻到没有数据的年份没有意义
            val firstYear = DateUtils.yearStart(dao.queryEarliestTime() ?: now)
            StatsCalendar.years(
                firstYearStart = firstYear,
                records = dao.queryBetween(firstYear, DateUtils.yearEndExclusive(now)),
                now = now
            )
        }
    }

    override fun onError(t: Throwable) {
        super.onError(t)
        view?.showMessage(appContext.getString(R.string.error_operation_failed))
    }
}
