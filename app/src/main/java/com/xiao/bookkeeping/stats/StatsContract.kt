package com.xiao.bookkeeping.stats

import com.xiao.bookkeeping.base.BaseView
import com.xiao.bookkeeping.model.StatsPeriod
import com.xiao.bookkeeping.model.StatsResult

/** 统计页的 MVP 契约。 */
interface StatsContract {

    interface View : BaseView {
        /** [rangeText] 是当前窗口的日期区间文案，如 `2026-09-11 ~ 2026-10-10`。 */
        fun showStats(result: StatsResult, rangeText: String)

        fun showMessage(message: String)
    }

    interface Presenter {
        fun attach(view: View)

        fun detach()

        fun loadStats(period: StatsPeriod)
    }
}
