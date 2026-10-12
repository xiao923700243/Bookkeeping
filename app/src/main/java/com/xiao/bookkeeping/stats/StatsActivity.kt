package com.xiao.bookkeeping.stats

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.xiao.bookkeeping.MainActivity
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.model.CalendarCell
import com.xiao.bookkeeping.model.HeatLevel
import com.xiao.bookkeeping.model.StatsResult
import com.xiao.bookkeeping.model.StatsScale
import com.xiao.bookkeeping.util.DateUtils
import com.xiao.bookkeeping.util.MoneyUtils
import kotlin.math.abs

/**
 * 统计页。三种粒度共用一张日历：天档一页一个月、月档一页一年、年档一页历年。
 *
 * 格子按结余的热力深浅上色，形状和配色见 tools/heatmap_palette.py。
 */
class StatsActivity : AppCompatActivity(), StatsContract.View {

    private lateinit var presenter: StatsPresenter
    private lateinit var rootView: View

    private lateinit var tabScale: TabLayout
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var textTitle: TextView
    private lateinit var weekdayHeader: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var textEmpty: TextView
    private lateinit var textTotalIncome: TextView
    private lateinit var textTotalExpense: TextView
    private lateinit var textBalance: TextView

    /** 当前粒度。旋转屏幕时靠它恢复选中的 Tab。 */
    private var scale = StatsScale.DAY

    /**
     * 页锚点，始终保留到「月」这一级精度（值恒为某个月的 1 号）。
     *
     * 切档时**不**把它对齐到新粒度：那样是有损的，10 月切到年档会被截成 1 月，
     * 再切回天档就回不到 10 月了。各档要哪一级周期由 Presenter 自己截断
     * （见 StatsPresenter.build），这里只负责记住用户翻到哪个月。
     */
    private var anchor: Long = DateUtils.monthStart(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_stats)

        rootView = findViewById(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bindViews()
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        presenter = StatsPresenter(this)
        presenter.attach(this)

        // TabLayout 不保存选中态（父类只存滚动位置），scale 和 anchor 也都是普通字段，
        // 配置变更后框架不会恢复，只能自己存
        if (savedInstanceState != null) {
            scale = savedInstanceState.getString(STATE_SCALE)
                ?.let { name -> StatsScale.values().firstOrNull { it.name == name } }
                ?: StatsScale.DAY
            anchor = savedInstanceState.getLong(STATE_ANCHOR, anchor)
        }

        buildWeekdayHeader()
        setupTabs()
        setupNav()

        // Tab 加入时首个 Tab 会被自动选中，而那时监听器还没注册，所以这里兜一次底。
        // 选中的是别的 Tab 时 select() 会触发 onTabSelected，别再重复加载。
        val tab = tabScale.getTabAt(scale.ordinal)
        if (tab != null && !tab.isSelected) {
            tab.select()
        } else {
            load()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SCALE, scale.name)
        outState.putLong(STATE_ANCHOR, anchor)
    }

    private fun bindViews() {
        tabScale = findViewById(R.id.tabScale)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        textTitle = findViewById(R.id.textTitle)
        weekdayHeader = findViewById(R.id.weekdayHeader)
        grid = findViewById(R.id.grid)
        textEmpty = findViewById(R.id.textEmpty)
        textTotalIncome = findViewById(R.id.textTotalIncome)
        textTotalExpense = findViewById(R.id.textTotalExpense)
        textBalance = findViewById(R.id.textBalance)
    }

    private fun buildWeekdayHeader() {
        weekdayHeader.removeAllViews()
        for (name in resources.getStringArray(R.array.calendar_weekdays)) {
            val cell = layoutInflater.inflate(R.layout.item_calendar_weekday, weekdayHeader, false)
            (cell as TextView).text = name
            weekdayHeader.addView(cell)
        }
    }

    private fun setupTabs() {
        val titles = mapOf(
            StatsScale.DAY to R.string.period_day,
            StatsScale.MONTH to R.string.period_month,
            StatsScale.YEAR to R.string.period_year
        )
        for (item in StatsScale.values()) {
            tabScale.addTab(tabScale.newTab().setText(titles.getValue(item)).setTag(item))
        }
        tabScale.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                // 锚点原样带着走：切档不改它，各档看到的周期由 Presenter 截断
                scale = tab.tag as StatsScale
                load()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupNav() {
        btnPrev.setOnClickListener { turnPage(-1) }
        btnNext.setOnClickListener { turnPage(1) }
    }

    private fun turnPage(pages: Int) {
        anchor = scale.shiftAnchor(anchor, pages)
        load()
    }

    private fun load() {
        presenter.loadStats(scale, anchor)
    }

    // ---- StatsContract.View ----

    override fun showStats(result: StatsResult) {
        textTitle.text = result.title

        // 用 isEnabled 吞掉点击，而不是只靠置灰——置灰只是视觉，仍会响应点击
        btnPrev.isEnabled = result.canGoPrev
        btnNext.isEnabled = result.canGoNext
        btnPrev.alpha = if (result.canGoPrev) 1f else ALPHA_DISABLED
        btnNext.alpha = if (result.canGoNext) 1f else ALPHA_DISABLED

        renderTotals(result)
        renderGrid(result)

        textEmpty.text = getString(R.string.empty_stats, result.title)
        textEmpty.visibility = if (result.isEmpty) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun renderTotals(result: StatsResult) {
        textTotalIncome.text = getString(R.string.amount_positive, MoneyUtils.format(result.totalIncome))
        textTotalExpense.text = getString(R.string.amount_negative, MoneyUtils.format(result.totalExpense))

        val balance = result.balance
        textBalance.text = if (balance < 0) {
            getString(R.string.amount_negative, MoneyUtils.format(-balance))
        } else {
            getString(R.string.amount_positive, MoneyUtils.format(balance))
        }
        textBalance.setTextColor(
            ContextCompat.getColor(
                this,
                if (balance < 0) R.color.balance_negative else R.color.balance_positive
            )
        )
    }

    /**
     * 日历格子全是现搭的。
     *
     * 用不着 RecyclerView：一页最多 6×7 格，没有滚动也没有复用，重建比配一套
     * Adapter + GridLayoutManager 直接得多，行列对齐也全在这一处看得到。
     */
    private fun renderGrid(result: StatsResult) {
        grid.removeAllViews()
        weekdayHeader.visibility = if (result.scale == StatsScale.DAY) View.VISIBLE else View.GONE

        val columns = result.scale.columns
        val levels = HeatLevel.levelsOf(result.cells)
        // 三档统一固定行高。天档早先是按剩余高度平分的，格子会随屏幕一起长，
        // 大屏上被拉成一片；定高之后日历只占它自己需要的那点地方
        val rowHeight = resources.getDimensionPixelSize(R.dimen.calendar_row_height)

        result.cells.chunked(columns).forEachIndexed { rowIndex, row ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    rowHeight
                )
            }
            row.forEachIndexed { columnIndex, cell ->
                rowView.addView(buildCell(cell, levels[rowIndex * columns + columnIndex], result.scale))
            }
            // 兜底补齐：模型本该把每页都补成整行，万一没补，缺的格子会让这一行
            // 剩下的几格被撑宽，和上一行的列错开
            repeat(columns - row.size) { rowView.addView(buildCell(null, HeatLevel.NONE, result.scale)) }
            grid.addView(rowView)
        }
    }

    private fun buildCell(cell: CalendarCell?, level: Int, scale: StatsScale): View {
        val view = layoutInflater.inflate(R.layout.item_calendar_cell, grid, false)
        view.findViewById<TextView>(R.id.textLabel).text = cell?.label.orEmpty()

        val amount = view.findViewById<TextView>(R.id.textAmount)
        amount.text = cell?.takeIf { it.hasData }?.let { formatBalance(it.balance) }.orEmpty()
        view.backgroundTintList = ColorStateList.valueOf(heatColor(cell, level))

        // 只有天档的格子能点：点某天是要去翻那天的明细，月和年没有对应的页面可跳
        if (cell != null && scale == StatsScale.DAY) {
            view.setOnClickListener { openDay(cell) }
            view.contentDescription = if (cell.hasData) {
                getString(
                    R.string.cd_day_cell,
                    DateUtils.formatDate(cell.startMs),
                    formatBalance(cell.balance)
                )
            } else {
                DateUtils.formatDate(cell.startMs)
            }
        }
        return view
    }

    /**
     * 格子里结余的展示形式。
     *
     * 符号跟着**显示出来的数字**走：不足一元时缩略后就是 0，再挂个 +/− 反而费解。
     */
    private fun formatBalance(balance: Double): String {
        val magnitude = MoneyUtils.formatCompact(abs(balance))
        if (magnitude == ZERO_TEXT) return magnitude
        return if (balance > 0) {
            getString(R.string.amount_positive, magnitude)
        } else {
            getString(R.string.amount_negative, magnitude)
        }
    }

    /** 正负决定走绿色臂还是红色臂，档位决定深浅；没有记录的格子不填色。 */
    private fun heatColor(cell: CalendarCell?, level: Int): Int = when {
        cell == null || !cell.hasData -> Color.TRANSPARENT
        // 有记录但收支持平：两条臂都套不上，用中性灰
        level == HeatLevel.NONE -> heatOf(R.color.heat_zero)
        cell.balance > 0 -> heatOf(POSITIVE_HEAT[level - 1])
        else -> heatOf(NEGATIVE_HEAT[level - 1])
    }

    private fun heatOf(colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

    /**
     * 跳到某一天的明细。
     *
     * 统计页是从列表页进来的，这里要回到**那一个**实例：不指定 CLEAR_TOP 的话
     * 会新起一个列表页叠在上面，返回时得按两次才出得来。
     */
    private fun openDay(cell: CalendarCell) {
        MainActivity.start(this, cell.startMs)
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    private companion object {
        /** 翻页按钮不可用时的透明度，跟 Material 的 disabled 观感对齐。 */
        const val ALPHA_DISABLED = 0.38f

        /** 缩略金额不足一元时就是这个，见 [formatBalance]。 */
        const val ZERO_TEXT = "0"

        /** 热力档位对应的颜色，下标 = 档位 - 1。 */
        val POSITIVE_HEAT = intArrayOf(
            R.color.heat_pos_1, R.color.heat_pos_2, R.color.heat_pos_3, R.color.heat_pos_4
        )
        val NEGATIVE_HEAT = intArrayOf(
            R.color.heat_neg_1, R.color.heat_neg_2, R.color.heat_neg_3, R.color.heat_neg_4
        )

        const val STATE_SCALE = "state_scale"
        const val STATE_ANCHOR = "state_anchor"
    }
}
