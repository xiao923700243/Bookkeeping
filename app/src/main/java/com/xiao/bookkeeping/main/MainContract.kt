package com.xiao.bookkeeping.main

import com.xiao.bookkeeping.base.BaseView
import com.xiao.bookkeeping.model.Record

/** 记录列表页的 MVP 契约。 */
interface MainContract {

    interface View : BaseView {
        /** 所选日期的记录，已按时间倒序。 */
        fun showRecords(records: List<Record>)

        /** 所选日期的汇总。 */
        fun showSummary(income: Double, expense: Double)

        fun showEmpty(empty: Boolean)

        fun showMessage(message: String)

        /** 删除确认由 View 负责弹窗，Presenter 只在用户确认后收到 [Presenter.deleteRecord]。 */
        fun confirmDelete(record: Record)
    }

    interface Presenter {
        fun attach(view: View)

        fun detach()

        /** 加载 [dayMs] 所在自然日的记录，卡片汇总和列表都跟着它走。 */
        fun loadRecords(dayMs: Long)

        fun deleteRecord(id: Long)
    }
}
