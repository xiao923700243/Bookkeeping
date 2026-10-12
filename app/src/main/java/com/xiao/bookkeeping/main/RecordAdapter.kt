package com.xiao.bookkeeping.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xiao.bookkeeping.R
import com.xiao.bookkeeping.model.Record
import com.xiao.bookkeeping.model.RecordType
import com.xiao.bookkeeping.util.DateUtils
import com.xiao.bookkeeping.util.MoneyUtils

class RecordAdapter(
    private val onItemClick: (Record) -> Unit,
    private val onDeleteClick: (Record) -> Unit
) : ListAdapter<Record, RecordAdapter.RecordViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return RecordViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val textRemark: TextView = itemView.findViewById(R.id.textRemark)
        private val textDateTime: TextView = itemView.findViewById(R.id.textDateTime)
        private val textAmount: TextView = itemView.findViewById(R.id.textAmount)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(record: Record) {
            val context = itemView.context
            val isIncome = record.type == RecordType.INCOME

            // 没写备注时回落到类型名，否则这一行会是空的，用户看不出记了什么
            textRemark.text = record.remark.ifBlank {
                context.getString(if (isIncome) R.string.type_income else R.string.type_expense)
            }
            // 列表已按天筛过，日期由顶部卡片给出，这里只留时分
            textDateTime.text = DateUtils.formatTime(record.time)
            textAmount.text = MoneyUtils.formatSigned(record.amount, record.type)
            textAmount.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isIncome) R.color.income else R.color.expense
                )
            )

            itemView.setOnClickListener { onItemClick(record) }
            btnDelete.setOnClickListener { onDeleteClick(record) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Record>() {
            override fun areItemsTheSame(oldItem: Record, newItem: Record): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Record, newItem: Record): Boolean =
                oldItem == newItem
        }
    }
}
