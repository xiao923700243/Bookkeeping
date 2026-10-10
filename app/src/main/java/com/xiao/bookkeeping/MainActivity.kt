package com.xiao.bookkeeping

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
import com.xiao.bookkeeping.util.MoneyUtils

class MainActivity : AppCompatActivity(), MainContract.View {

    private lateinit var presenter: MainPresenter
    private lateinit var adapter: RecordAdapter
    private lateinit var rootView: View
    private lateinit var textEmpty: TextView
    private lateinit var textTodayIncome: TextView
    private lateinit var textTodayExpense: TextView
    private lateinit var textTodayBalance: TextView

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

        presenter = MainPresenter(this)
        presenter.attach(this)
    }

    private fun bindViews() {
        textEmpty = findViewById(R.id.textEmpty)
        textTodayIncome = findViewById(R.id.textTodayIncome)
        textTodayExpense = findViewById(R.id.textTodayExpense)
        textTodayBalance = findViewById(R.id.textTodayBalance)
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
            EditActivity.start(this, recordId = null)
        }
    }

    override fun onResume() {
        super.onResume()
        // 新增/编辑页返回后要立刻看到最新数据，所以刷新放在 onResume 而不是 onCreate
        presenter.loadRecords()
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
        textTodayIncome.text = getString(R.string.amount_positive, MoneyUtils.format(income))
        textTodayExpense.text = getString(R.string.amount_negative, MoneyUtils.format(expense))

        val balance = income - expense
        textTodayBalance.text = if (balance < 0) {
            getString(R.string.amount_negative, MoneyUtils.format(-balance))
        } else {
            getString(R.string.amount_positive, MoneyUtils.format(balance))
        }
        textTodayBalance.setTextColor(
            ContextCompat.getColor(
                this,
                if (balance < 0) R.color.balance_negative else R.color.balance_positive
            )
        )
    }

    override fun showEmpty(empty: Boolean) {
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
}
