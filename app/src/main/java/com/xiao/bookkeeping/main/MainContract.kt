package com.xiao.bookkeeping.main

import android.net.Uri
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

        /**
         * 导入覆盖确认，同样由 View 弹窗，用户确认后才走 [Presenter.commitImport]。
         *
         * [fileName]、[count]、[exportedAt] 全部来自**已经校验通过**的备份文件，
         * 拿来给用户一个「选对文件了没」的凭据——覆盖不可撤销，值得让他看清楚再点。
         */
        fun confirmImport(fileName: String, count: Int, exportedAt: Long)
    }

    interface Presenter {
        fun attach(view: View)

        fun detach()

        /** 加载 [dayMs] 所在自然日的记录，卡片汇总和列表都跟着它走。 */
        fun loadRecords(dayMs: Long)

        fun deleteRecord(id: Long)

        /** 把整本账导出到 [uri] 指向的文件。 */
        fun exportBackup(uri: Uri)

        /**
         * 读 [uri] 指向的备份并整份校验，通过后请 View 弹覆盖确认。
         *
         * 此时还**没有**动数据库：文件不合法就直接出提示，账本原样不动。
         */
        fun prepareImport(uri: Uri)

        /** 用户确认覆盖后，把待导入的备份整份写进库。没有待导入的备份时什么都不做。 */
        fun commitImport()
    }
}
