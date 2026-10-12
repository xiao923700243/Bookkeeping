package com.xiao.bookkeeping

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.xiao.bookkeeping.edit.EditActivity
import com.xiao.bookkeeping.main.MainContract
import com.xiao.bookkeeping.main.MainPresenter
import com.xiao.bookkeeping.main.RecordAdapter
import com.xiao.bookkeeping.model.Record
import com.xiao.bookkeeping.stats.StatsActivity
import com.xiao.bookkeeping.util.DateUtils
import com.xiao.bookkeeping.util.MoneyUtils
import java.util.Calendar

class MainActivity : AppCompatActivity(), MainContract.View {

    private lateinit var presenter: MainPresenter
    private lateinit var adapter: RecordAdapter
    private lateinit var rootView: View
    private lateinit var textEmpty: TextView
    private lateinit var textDay: TextView
    private lateinit var btnPrevDay: ImageButton
    private lateinit var btnNextDay: ImageButton
    private lateinit var btnPickDay: View
    private lateinit var textDayIncome: TextView
    private lateinit var textDayExpense: TextView
    private lateinit var textDayBalance: TextView

    /** 正在查看的自然日，恒为当天 00:00。默认今天，由箭头切换。 */
    private var selectedDay: Long = DateUtils.dayStart(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        rootView = findViewById(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bindViews()
        setupList()
        setupToolbar()
        setupFab()
        setupDayBar()

        presenter = MainPresenter(this)
        presenter.attach(this)

        // selectedDay 是普通字段，配置变更后框架不会恢复它，不补会跳回今天
        if (savedInstanceState != null) {
            selectedDay = savedInstanceState.getLong(STATE_SELECTED_DAY, selectedDay)
        }
        // 统计页点某天跳过来时带的日子优先于恢复值——用户刚点的那天才算数
        applyIntentDay()
        renderDay()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 随后会走 onResume 按新的 selectedDay 拉数据，这里只要把日期和文案更新掉
        applyIntentDay()
        renderDay()
    }

    /**
     * 采纳启动 Intent 里带的日子，没有就什么都不做。
     *
     * 采纳后立刻清掉：旋转屏幕会重建 Activity 但复用同一个 Intent，不清的话
     * 用户在列表页翻到别的日子，一转屏又会被拽回统计页点的那天。
     */
    private fun applyIntentDay() {
        val day = intent.getLongExtra(EXTRA_DAY, 0L)
        if (day == 0L) return
        selectedDay = DateUtils.dayStart(day)
        intent.removeExtra(EXTRA_DAY)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_SELECTED_DAY, selectedDay)
    }

    private fun bindViews() {
        textEmpty = findViewById(R.id.textEmpty)
        textDay = findViewById(R.id.textDay)
        btnPrevDay = findViewById(R.id.btnPrevDay)
        btnNextDay = findViewById(R.id.btnNextDay)
        btnPickDay = findViewById(R.id.btnPickDay)
        textDayIncome = findViewById(R.id.textDayIncome)
        textDayExpense = findViewById(R.id.textDayExpense)
        textDayBalance = findViewById(R.id.textDayBalance)
    }

    private fun setupList() {
        adapter = RecordAdapter(
            onItemClick = { record -> EditActivity.start(this, record.id) },
            onDeleteClick = { record -> confirmDelete(record) }
        )
        findViewById<RecyclerView>(R.id.listRecords).adapter = adapter
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_stats) {
                startActivity(Intent(this, StatsActivity::class.java))
                true
            } else {
                false
            }
        }
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            // 带着选中日期进新增页：用户在 10-09 状态点「+」，意思就是记 10-09 的账，
            // 默认落到今天的话，记完返回还会因为列表停在 10-09 而看不到这一笔
            EditActivity.start(this, recordId = null, defaultDayMs = selectedDay)
        }
    }

    private fun setupDayBar() {
        btnPrevDay.setOnClickListener { goToDay(DateUtils.plusDays(selectedDay, -1)) }
        btnNextDay.setOnClickListener { goToDay(DateUtils.plusDays(selectedDay, 1)) }
        btnPickDay.setOnClickListener { pickDay() }
    }

    /** 切到 [day] 所在自然日并刷新，卡片汇总和列表都跟着走。 */
    private fun goToDay(day: Long) {
        selectedDay = DateUtils.dayStart(day)
        renderDay()
        presenter.loadRecords(selectedDay)
    }

    /**
     * 弹日期选择器跳到任意一天。
     *
     * 只能一天一天地翻时，想看三周前要点 21 下，所以留一个直达入口。
     * 上限卡在今天——右箭头已经不让人看未来，这里得保持一致。
     */
    private fun pickDay() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDay }
        // 按「确定」这条兜底路不用自己关窗，DatePickerDialog 收到确定就会关
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth -> applyPickedDay(DateUtils.of(year, month, dayOfMonth, 0, 0)) },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // 监听器挂在 show 之后：构造 DatePickerDialog 时 DatePicker 会自己 init 一次，
        // 那时就挂上去的话，这次初始化可能被当成用户改动、弹窗一闪就关。
        dialog.setOnShowListener {
            dialog.datePicker.apply {
                // 上限取今天 23:59:59.999 而不是「此刻」：传此刻的话上限落在今天正午，
                // 万一框架按毫秒比，今天下午就全成了越界日期。取当天末尾两种比法都对。
                maxDate = DateUtils.dayEndExclusive(System.currentTimeMillis()) - 1

                // 点中某天立刻跳转，不用再按「确定」——翻个日期是轻量操作，两步确认反而碍事
                setOnDateChangedListener { _, year, month, dayOfMonth ->
                    // 这条路要自己关窗，DatePickerDialog 只在按「确定」时才关
                    if (applyPickedDay(DateUtils.of(year, month, dayOfMonth, 0, 0))) {
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    /**
     * 采纳在日期选择器里选定的 [day]，返回是否跳转过去了。
     *
     * 未来日期一律不采纳。不能只靠 [DatePicker.setMaxDate] 挡——它只把越界日期画成灰色，
     * 框架仍然允许点中（API 21 起的老问题，见 issuetracker 36967746），所以得自己再拦一道。
     */
    private fun applyPickedDay(day: Long): Boolean {
        if (DateUtils.dayStart(day) > DateUtils.dayStart(System.currentTimeMillis())) {
            Toast.makeText(this, R.string.error_future_day, Toast.LENGTH_SHORT).show()
            return false
        }
        goToDay(day)
        return true
    }

    /** 刷新日期栏文案，并按「看不到未来」决定右箭头是否可用。 */
    private fun renderDay() {
        textDay.text = dayLabel(selectedDay)

        // 用 isEnabled 吞掉点击，而不是靠箭头置灰——置灰只是视觉，仍会响应点击
        btnNextDay.isEnabled = selectedDay != DateUtils.dayStart(System.currentTimeMillis())
        btnNextDay.alpha = if (btnNextDay.isEnabled) 1f else ALPHA_DISABLED
    }

    /** 近三天给「今天 / 昨天 / 前天」，更早的只报月日。 */
    private fun dayLabel(day: Long): String {
        val monthDay = DateUtils.formatMonthDay(day)
        val relative = when (DateUtils.daysAgo(day, System.currentTimeMillis())) {
            0 -> getString(R.string.date_today)
            1 -> getString(R.string.date_yesterday)
            2 -> getString(R.string.date_before_yesterday)
            else -> return monthDay
        }
        return getString(R.string.day_label, monthDay, relative)
    }

    override fun onResume() {
        super.onResume()
        // 新增/编辑页返回后要立刻看到最新数据，所以刷新放在 onResume 而不是 onCreate；
        // 顺带也能覆盖跨零点打开时 selectedDay 已变成「昨天」的情况
        presenter.loadRecords(selectedDay)
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    // ---- MainContract.View ----

    override fun showRecords(records: List<Record>) {
        adapter.submitList(records)
    }

    override fun showSummary(income: Double, expense: Double) {
        textDayIncome.text = getString(R.string.amount_positive, MoneyUtils.format(income))
        textDayExpense.text = getString(R.string.amount_negative, MoneyUtils.format(expense))

        val balance = income - expense
        textDayBalance.text = if (balance < 0) {
            getString(R.string.amount_negative, MoneyUtils.format(-balance))
        } else {
            getString(R.string.amount_positive, MoneyUtils.format(balance))
        }
        textDayBalance.setTextColor(
            ContextCompat.getColor(
                this,
                if (balance < 0) R.color.balance_negative else R.color.balance_positive
            )
        )
    }

    override fun showEmpty(empty: Boolean) {
        if (empty) {
            // 带上日期：翻到某天没记录时，只说「还没有记账记录」会被当成整库为空
            textEmpty.text = getString(R.string.empty_records_day, dayLabel(selectedDay))
        }
        textEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun confirmDelete(record: Record) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> presenter.deleteRecord(record.id) }
            .show()
    }

    companion object {
        private const val STATE_SELECTED_DAY = "state_selected_day"
        private const val EXTRA_DAY = "extra_day"

        /** 右箭头不可用时的透明度，跟 Material 的 disabled 观感对齐。 */
        private const val ALPHA_DISABLED = 0.38f

        /**
         * 打开列表页并定位到 [dayMs] 所在那天。
         *
         * 带上 CLEAR_TOP 是回到栈里已有的那个列表页，而不是再起一个——统计页就是从
         * 列表页进来的，叠第二个实例意味着用户要按两次返回才出得去。
         */
        fun start(context: Context, dayMs: Long) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_DAY, dayMs)
            )
        }
    }
}
