package com.xiao.bookkeeping.stats

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.model.StatsPeriod
import com.xiao.bookkeeping.model.StatsResult
import com.xiao.bookkeeping.util.MoneyUtils

class StatsActivity : AppCompatActivity(), StatsContract.View {

    private lateinit var presenter: StatsPresenter
    private lateinit var rootView: View

    private lateinit var tabPeriod: TabLayout
    private lateinit var lineChart: LineChart
    private lateinit var textRange: TextView
    private lateinit var textTotalIncome: TextView
    private lateinit var textTotalExpense: TextView
    private lateinit var textBalance: TextView
    private lateinit var textChartEmpty: TextView

    /** 当前统计粒度，旋转屏幕时靠它恢复选中的 Tab。 */
    private var currentPeriod = StatsPeriod.DAY

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
        configureChart()

        presenter = StatsPresenter(this)
        presenter.attach(this)

        // TabLayout 不保存选中态（父类只存滚动位置），旋转后得自己恢复用户选的粒度
        currentPeriod = savedInstanceState?.getString(STATE_PERIOD)
            ?.let { name -> StatsPeriod.values().firstOrNull { it.name == name } }
            ?: StatsPeriod.DAY

        setupTabs()

        // Tab 加入时首个 Tab 会被自动选中，而那时监听器还没注册，所以这里兜一次底。
        // 选中的是别的 Tab 时 select() 会触发 onTabSelected，别再重复加载。
        val tab = tabPeriod.getTabAt(currentPeriod.ordinal)
        if (tab != null && !tab.isSelected) {
            tab.select()
        } else {
            presenter.loadStats(currentPeriod)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PERIOD, currentPeriod.name)
    }

    private fun bindViews() {
        tabPeriod = findViewById(R.id.tabPeriod)
        lineChart = findViewById(R.id.lineChart)
        textRange = findViewById(R.id.textRange)
        textTotalIncome = findViewById(R.id.textTotalIncome)
        textTotalExpense = findViewById(R.id.textTotalExpense)
        textBalance = findViewById(R.id.textBalance)
        textChartEmpty = findViewById(R.id.textChartEmpty)
    }

    private fun setupTabs() {
        val titles = mapOf(
            StatsPeriod.DAY to R.string.period_day,
            StatsPeriod.WEEK to R.string.period_week,
            StatsPeriod.MONTH to R.string.period_month,
            StatsPeriod.YEAR to R.string.period_year
        )
        for (period in StatsPeriod.values()) {
            tabPeriod.addTab(tabPeriod.newTab().setText(titles.getValue(period)).setTag(period))
        }
        tabPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentPeriod = tab.tag as StatsPeriod
                presenter.loadStats(currentPeriod)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun configureChart() {
        // 图表库的默认坐标轴/网格颜色是写死的灰，深色模式下几乎看不见，这里显式跟着主题走
        val axisTextColor = ContextCompat.getColor(this, R.color.chart_axis_text)
        val gridColor = ContextCompat.getColor(this, R.color.chart_grid)

        lineChart.apply {
            description.isEnabled = false
            setNoDataText("")

            legend.isEnabled = true
            legend.form = Legend.LegendForm.LINE
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            legend.textColor = axisTextColor

            // 统计窗口最多 30 个点，缩放/拖拽只增加误操作，直接关掉
            setTouchEnabled(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            setDrawGridBackground(false)

            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.setDrawAxisLine(false)
            axisLeft.granularity = 1f
            axisLeft.textColor = axisTextColor
            axisLeft.gridColor = gridColor

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(true)
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = 0f
            xAxis.setAvoidFirstLastClipping(true)
            xAxis.textColor = axisTextColor
            xAxis.gridColor = gridColor
        }
    }

    // ---- StatsContract.View ----

    override fun showStats(result: StatsResult, rangeText: String) {
        textRange.text = rangeText
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

        renderChart(result)

        // 两条曲线都平在 0 时给句提示，否则用户对着空图表会以为是加载失败
        val hasData = result.totalIncome > 0.0 || result.totalExpense > 0.0
        textChartEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun renderChart(result: StatsResult) {
        val incomeSet = buildDataSet(
            values = result.income,
            label = getString(R.string.legend_income),
            colorRes = R.color.income
        )
        val expenseSet = buildDataSet(
            values = result.expense,
            label = getString(R.string.legend_expense),
            colorRes = R.color.expense
        )

        lineChart.data = LineData(incomeSet, expenseSet)
        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(result.labels)
        // 桶多时让图表自己抽稀 X 轴文字，否则 30 个标签会糊成一片
        lineChart.xAxis.labelCount = minOf(result.labels.size, MAX_X_LABELS)
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    private fun buildDataSet(values: List<Float>, label: String, colorRes: Int): LineDataSet {
        val color = ContextCompat.getColor(this, colorRes)
        val entries = values.mapIndexed { index, value -> Entry(index.toFloat(), value) }

        return LineDataSet(entries, label).apply {
            setColor(color)
            setCircleColor(color)
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawValues(false)
            // 需求要的是「曲线图」，用平滑曲线而不是折线
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillAlpha = 32
            fillColor = color
            highLightColor = color
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    private companion object {
        /** X 轴最多显示的标签数，超出部分交给图表抽稀。 */
        const val MAX_X_LABELS = 6

        const val STATE_PERIOD = "state_period"
    }
}
