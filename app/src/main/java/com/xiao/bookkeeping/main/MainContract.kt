package com.xiao.bookkeeping.main

import com.xiao.bookkeeping.base.BaseView
import com.xiao.bookkeeping.model.Record

/** 记录列表页的 MVP 契约。 */
interface MainContract {

    interface View : BaseView {
        /** 全量记录，已按时间倒序。 */
        fun showRecords(records: List<Record>)

        /** 本月汇总。 */
        fun showSummary(income: Double, expense: Double)

        fun showEmpty(empty: Boolean)

        fun showMessage(message: String)

        /** 删除确认由 View 负责弹窗，Presenter 只在用户确认后收到 [Presenter.deleteRecord]。 */
        fun confirmDelete(record: Record)
    }

    interface Presenter {
        fun attach(view: View)

        fun detach()

        fun loadRecords()

        fun deleteRecord(id: Long)
    }
}
