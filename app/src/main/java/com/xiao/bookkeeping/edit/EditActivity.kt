package com.xiao.bookkeeping.edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.model.RecordType
import com.xiao.bookkeeping.util.DateUtils
import com.xiao.bookkeeping.util.MoneyUtils
import com.xiao.bookkeeping.util.RingtonePlayer
import java.util.Calendar

class EditActivity : AppCompatActivity(), EditContract.View {

    private lateinit var presenter: EditPresenter
    private lateinit var ringtonePlayer: RingtonePlayer
    private lateinit var rootView: View

    private lateinit var groupType: MaterialButtonToggleGroup
    private lateinit var layoutAmount: TextInputLayout
    private lateinit var layoutTime: TextInputLayout
    private lateinit var chipToday: Chip
    private lateinit var chipYesterday: Chip
    private lateinit var chipBeforeYesterday: Chip
    private lateinit var chipCustom: Chip
    private lateinit var editAmount: TextInputEditText
    private lateinit var editRemark: TextInputEditText
    private lateinit var editTime: TextInputEditText
    private lateinit var btnDelete: MaterialButton

    /** 当前选中的发生时间，由日期/时间选择器更新。 */
    private var selectedTime: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit)

        rootView = findViewById(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 窗口是 edge-to-edge 绘制的，adjustResize 不会把内容顶上去，
            // 必须自己用 IME insets 把底部的保存条抬到键盘之上
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        ringtonePlayer = RingtonePlayer.get(this)
        bindViews()

        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, NO_ID).takeIf { it != NO_ID }
        // 新增时表单时间默认落在这天；列表页没传就退回今天
        val defaultDayMs = intent.getLongExtra(EXTRA_DEFAULT_DAY, System.currentTimeMillis())
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = getString(if (recordId == null) R.string.title_add else R.string.title_edit)
            setNavigationOnClickListener { finish() }
        }

        presenter = EditPresenter(this)
        presenter.attach(this)

        if (savedInstanceState == null) {
            presenter.start(recordId, defaultDayMs)
        } else {
            // 配置变更后重建：金额/备注/类型这些控件内容由框架自动恢复，
            // selectedTime 是普通字段恢复不了，得自己补，否则界面显示的日期时间和实际入库的对不上。
            // 这里也不能再调 start()——新增态会把它重置成"旋转那一刻"，
            // 编辑态则是异步回填，会盖掉用户还没保存的修改。
            selectedTime = savedInstanceState.getLong(STATE_SELECTED_TIME, System.currentTimeMillis())
            renderTime()
            btnDelete.visibility = if (recordId != null) View.VISIBLE else View.GONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_SELECTED_TIME, selectedTime)
    }

    private fun bindViews() {
        groupType = findViewById(R.id.groupType)
        layoutAmount = findViewById(R.id.layoutAmount)
        layoutTime = findViewById(R.id.layoutTime)
        editAmount = findViewById(R.id.editAmount)
        editRemark = findViewById(R.id.editRemark)
        editTime = findViewById(R.id.editTime)
        btnDelete = findViewById(R.id.btnDelete)
        chipToday = findViewById(R.id.chipToday)
        chipYesterday = findViewById(R.id.chipYesterday)
        chipBeforeYesterday = findViewById(R.id.chipBeforeYesterday)
        chipCustom = findViewById(R.id.chipCustom)

        // 金额一改动就撤掉上一条错误，不用等到再次点保存
        editAmount.doAfterTextChanged { layoutAmount.error = null }

        editTime.setOnClickListener { pickDateTime() }
        layoutTime.setEndIconOnClickListener { pickDateTime() }

        // 用 OnClickListener 而不是 chip 的选中态监听：点一个已选中的 chip 也要有反应，
        // 而且 renderTime() 回填选中态时不会反过来把这些动作再触发一遍
        chipToday.setOnClickListener { shiftDateTo(0) }
        chipYesterday.setOnClickListener { shiftDateTo(1) }
        chipBeforeYesterday.setOnClickListener { shiftDateTo(2) }
        chipCustom.setOnClickListener { pickDateTime() }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        btnDelete.setOnClickListener { confirmDelete() }
    }

    /**
     * 把日期平移到 [daysAgo] 天前，保留已选中的时分。
     *
     * 点「昨天」通常只想改日期，把时间一并重置成此刻反而要再改一次。
     */
    private fun shiftDateTo(daysAgo: Int) {
        val previous = Calendar.getInstance().apply { timeInMillis = selectedTime }
        selectedTime = Calendar.getInstance().apply {
            timeInMillis = DateUtils.dayStart(System.currentTimeMillis())
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, previous.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, previous.get(Calendar.MINUTE))
        }.timeInMillis
        renderTime()
    }

    /** 让快捷按钮的选中态跟上当前日期；不在今天/昨天/前天之内就高亮「自选」。 */
    private fun syncQuickDateChips() {
        val today = DateUtils.dayStart(System.currentTimeMillis())
        val day = DateUtils.dayStart(selectedTime)

        val target = when (day) {
            today -> R.id.chipToday
            DateUtils.dayStart(DateUtils.plusDays(today, -1)) -> R.id.chipYesterday
            DateUtils.dayStart(DateUtils.plusDays(today, -2)) -> R.id.chipBeforeYesterday
            else -> R.id.chipCustom
        }

        chipToday.isChecked = target == R.id.chipToday
        chipYesterday.isChecked = target == R.id.chipYesterday
        chipBeforeYesterday.isChecked = target == R.id.chipBeforeYesterday
        chipCustom.isChecked = target == R.id.chipCustom
    }

    private fun save() {
        presenter.save(
            type = selectedType(),
            amountInput = editAmount.text?.toString().orEmpty(),
            remark = editRemark.text?.toString().orEmpty(),
            time = selectedTime
        )
    }

    private fun selectedType(): RecordType =
        if (groupType.checkedButtonId == R.id.btnIncome) RecordType.INCOME else RecordType.EXPENSE

    /** 先选日期再选时间，两步都完成才更新表单。 */
    private fun pickDateTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedTime }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        selectedTime = DateUtils.of(year, month, dayOfMonth, hour, minute)
                        renderTime()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun renderTime() {
        editTime.setText(DateUtils.formatDateTime(selectedTime))
        syncQuickDateChips()
    }

    // ---- EditContract.View ----

    override fun showForm(state: EditContract.FormState) {
        groupType.check(if (state.type == RecordType.INCOME) R.id.btnIncome else R.id.btnExpense)
        editAmount.setText(state.amount)
        editRemark.setText(state.remark)
        selectedTime = state.time
        renderTime()

        // 只有编辑已有记录时才给删除入口
        btnDelete.visibility = if (state.id != 0L) View.VISIBLE else View.GONE
    }

    override fun showAmountError(message: String) {
        layoutAmount.error = message
        editAmount.requestFocus()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSaved(type: RecordType, amount: Double, isNew: Boolean) {
        // 需求是「添加收入成功时」播入账铃声；编辑既有记录不再响，避免改个备注就响一声
        val shouldRing = isNew && type == RecordType.INCOME
        // 排查用：若只看到这行却听不到声音，问题在下游的播放器/音量；看不到这行才是判断逻辑的问题
        Log.i(TAG, "保存成功 isNew=$isNew type=$type 播铃声=$shouldRing")
        if (shouldRing) {
            ringtonePlayer.play()
        }
        val messageRes = when {
            !isNew -> R.string.saved_updated
            type == RecordType.INCOME -> R.string.saved_income
            else -> R.string.saved_expense
        }
        Toast.makeText(this, getString(messageRes, MoneyUtils.format(amount)), Toast.LENGTH_SHORT)
            .show()
        finish()
    }

    override fun close() {
        finish()
    }

    override fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> presenter.delete() }
            .show()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EditActivity"
        private const val EXTRA_RECORD_ID = "extra_record_id"
        private const val EXTRA_DEFAULT_DAY = "extra_default_day"
        private const val NO_ID = -1L
        private const val STATE_SELECTED_TIME = "state_selected_time"

        /**
         * [recordId] 传 null 表示新增。
         *
         * [defaultDayMs] 只在新增时生效，决定表单里的时间默认落在哪个自然日——用户在
         * 列表页翻到哪天，就该记哪天。编辑已有记录时以记录自身的时间为准，不用传。
         */
        fun start(
            context: Context,
            recordId: Long?,
            defaultDayMs: Long = System.currentTimeMillis()
        ) {
            val intent = Intent(context, EditActivity::class.java).apply {
                if (recordId != null) putExtra(EXTRA_RECORD_ID, recordId)
                putExtra(EXTRA_DEFAULT_DAY, defaultDayMs)
            }
            context.startActivity(intent)
        }
    }
}
