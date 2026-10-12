package com.xiao.bookkeeping.stats

import com.xiao.bookkeeping.base.BaseView
import com.xiao.bookkeeping.model.StatsResult
import com.xiao.bookkeeping.model.StatsScale

/** 统计页的 MVP 契约。 */
interface StatsContract {

    interface View : BaseView {
        fun showStats(result: StatsResult)

        fun showMessage(message: String)
    }

    interface Presenter {
        fun attach(view: View)

        fun detach()

        /** 加载 [scale] 粒度下 [anchor] 所在的那一页日历。 */
        fun loadStats(scale: StatsScale, anchor: Long)
    }
}
