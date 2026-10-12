package com.xiao.bookkeeping.edit

import android.content.Context
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.base.BasePresenter
import com.xiao.bookkeeping.model.Record
import com.xiao.bookkeeping.model.RecordDao
import com.xiao.bookkeeping.model.RecordType
import com.xiao.bookkeeping.util.DateUtils
import com.xiao.bookkeeping.util.MoneyUtils

class EditPresenter(context: Context) :
    BasePresenter<EditContract.View>(), EditContract.Presenter {

    private val appContext = context.applicationContext
    private val dao = RecordDao(appContext)

    /** 0 表示新增，非 0 表示正在编辑这条记录。 */
    private var editingId: Long = 0L

    /** 保存是否在途。写库完成前再点保存直接忽略，避免连点插入重复记录。 */
    private var saving = false

    override fun start(recordId: Long?, defaultDayMs: Long) {
        editingId = recordId ?: 0L

        if (editingId == 0L) {
            view?.showForm(
                EditContract.FormState(
                    id = 0L,
                    // 主要用途是记每天的兼职收入，所以默认落在收入，省一次点击
                    type = RecordType.INCOME,
                    amount = "",
                    remark = "",
                    time = defaultTimeOn(defaultDayMs)
                )
            )
            return
        }

        runAsync(
            task = { dao.queryById(editingId) },
            onSuccess = { record ->
                if (record == null) {
                    // 可能在别处已被删除
                    view?.showMessage(appContext.getString(R.string.error_record_missing))
                    view?.close()
                } else {
                    view?.showForm(
                        EditContract.FormState(
                            id = record.id,
                            type = record.type,
                            // 编辑时金额回填成纯数字：输入框带 ¥ 前缀，再拼符号会重复
                            amount = MoneyUtils.formatPlain(record.amount),
                            remark = record.remark,
                            time = record.time
                        )
                    )
                }
            }
        )
    }

    override fun save(type: RecordType, amountInput: String, remark: String, time: Long) {
        val amount = MoneyUtils.parse(amountInput)
        if (amount == null) {
            view?.showAmountError(appContext.getString(R.string.error_amount_invalid))
            return
        }
        // 写库是异步的，回调回来之前 finish 还没执行，连点两次会插两条
        if (saving) return
        saving = true

        val record = Record(
            id = editingId,
            type = type,
            amount = amount,
            remark = remark.trim(),
            time = time
        )
        val isNew = editingId == 0L

        runAsync(
            task = {
                // insert 失败返回 -1，update 目标行不存在返回 0，都不能当成保存成功
                if (isNew) dao.insert(record) > 0 else dao.update(record) > 0
            },
            onSuccess = { written ->
                saving = false
                if (written) {
                    view?.onSaved(type, amount, isNew)
                } else {
                    view?.showMessage(appContext.getString(R.string.error_save_failed))
                }
            }
        )
    }

    override fun delete() {
        val id = editingId
        if (id == 0L) {
            view?.close()
            return
        }
        runAsync(
            task = { dao.delete(id) },
            onSuccess = {
                view?.showMessage(appContext.getString(R.string.deleted))
                view?.close()
            }
        )
    }

    /**
     * 新增时的默认发生时间：落在 [defaultDayMs] 那一天，时分沿用此刻。
     *
     * 用户在列表页翻到 10-09 再点新增，意图就是「补记 10-09 的事」。
     * 落到今天等于让他进去再手动改一次日期——记完返回还看不到，因为列表停在 10-09。
     *
     * 沿用时分而不是清零，理由同 `EditActivity.shiftDateTo`：改日期时通常不想连
     * 时间一起改，把时分重置成 00:00 反而要再改一次。
     */
    private fun defaultTimeOn(defaultDayMs: Long): Long {
        val now = System.currentTimeMillis()
        // 今天就用此刻本身，不必把秒和毫秒抹掉
        if (DateUtils.dayStart(defaultDayMs) == DateUtils.dayStart(now)) return now

        return DateUtils.of(
            DateUtils.yearOf(defaultDayMs),
            DateUtils.monthOf(defaultDayMs),
            DateUtils.dayOf(defaultDayMs),
            DateUtils.hourOf(now),
            DateUtils.minuteOf(now)
        )
    }

    override fun onError(t: Throwable) {
        super.onError(t)
        // 失败后要放开闸门，否则用户重试会被当成重复点击直接吞掉
        saving = false
        view?.showMessage(appContext.getString(R.string.error_operation_failed))
    }
}
