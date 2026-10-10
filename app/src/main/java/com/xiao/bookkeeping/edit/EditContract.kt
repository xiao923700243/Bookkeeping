package com.xiao.bookkeeping.edit

import com.xiao.bookkeeping.base.BaseView
import com.xiao.bookkeeping.model.RecordType

/** 新增 / 编辑页的 MVP 契约。 */
interface EditContract {

    /** 表单初始值。`id == 0L` 表示新增。 */
    data class FormState(
        val id: Long,
        val type: RecordType,
        val amount: String,
        val remark: String,
        val time: Long
    )

    interface View : BaseView {
        fun showForm(state: FormState)

        /** 金额校验失败，错误挂在金额输入框上而不是弹 Toast。 */
        fun showAmountError(message: String)

        fun showMessage(message: String)

        /**
         * 保存成功。[isNew] 区分新增与编辑——是否播铃声由 View 决定，
         * 而「入账铃声」只在新增收入时响。声音属于展示层的事，所以判断留给 View。
         */
        fun onSaved(type: RecordType, amount: Double, isNew: Boolean)

        fun close()

        fun confirmDelete()
    }

    interface Presenter {
        fun attach(view: View)

        fun detach()

        /** [recordId] 为 null 表示新增。 */
        fun start(recordId: Long?)

        fun save(type: RecordType, amountInput: String, remark: String, time: Long)

        fun delete()
    }
}
