package com.xiao.bookkeeping.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

/**
 * 记录表的读写入口。
 *
 * 所有方法都会阻塞当前线程，调用方（Presenter）负责切到子线程执行。
 */
class RecordDao(context: Context) {

    private val helper = DbHelper.get(context)

    fun insert(record: Record): Long =
        helper.writableDatabase.insert(TABLE, null, record.toValues())

    fun update(record: Record): Int =
        helper.writableDatabase.update(
            TABLE,
            record.toValues(),
            "$COL_ID = ?",
            arrayOf(record.id.toString())
        )

    fun delete(id: Long): Int =
        helper.writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))

    fun queryById(id: Long): Record? =
        query("$COL_ID = ?", arrayOf(id.toString()), null).firstOrNull()

    /**
     * 取 `[startMs, endMs)` 区间内的记录。
     *
     * 统计页按正序分桶，列表页要最新的在上，所以留一个排序开关。
     */
    fun queryBetween(startMs: Long, endMs: Long, descending: Boolean = false): List<Record> =
        query(
            "$COL_TIME >= ? AND $COL_TIME < ?",
            arrayOf(startMs.toString(), endMs.toString()),
            if (descending) "$COL_TIME DESC" else "$COL_TIME ASC"
        )

    /**
     * 最早一条记录的时间，库里没有记录时返回 null。
     *
     * 年档要决定「历年」从哪年开始，翻到没有数据的年份没有意义。
     */
    fun queryEarliestTime(): Long? {
        val cursor = helper.readableDatabase.rawQuery(
            "SELECT MIN($COL_TIME) FROM $TABLE", null
        )
        return cursor.use { c ->
            // MIN 在没有行时返回一行 NULL，不能只看 moveToFirst
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }

    private fun query(selection: String?, args: Array<String>?, orderBy: String?): List<Record> {
        val cursor = helper.readableDatabase.query(
            TABLE, null, selection, args, null, null, orderBy
        )
        return cursor.use { c ->
            val out = ArrayList<Record>(c.count)
            while (c.moveToNext()) out += c.toRecord()
            out
        }
    }

    private fun Cursor.toRecord(): Record = Record(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        type = RecordType.fromCode(getInt(getColumnIndexOrThrow(COL_TYPE))),
        amount = getDouble(getColumnIndexOrThrow(COL_AMOUNT)),
        remark = getString(getColumnIndexOrThrow(COL_REMARK)).orEmpty(),
        time = getLong(getColumnIndexOrThrow(COL_TIME))
    )

    /** 不含 id —— 新增走自增主键，更新走 where 条件。 */
    private fun Record.toValues(): ContentValues = ContentValues().apply {
        put(COL_TYPE, type.code)
        put(COL_AMOUNT, amount)
        put(COL_REMARK, remark)
        put(COL_TIME, time)
    }

    private companion object {
        const val TABLE = DbHelper.TABLE
        const val COL_ID = DbHelper.COL_ID
        const val COL_TYPE = DbHelper.COL_TYPE
        const val COL_AMOUNT = DbHelper.COL_AMOUNT
        const val COL_REMARK = DbHelper.COL_REMARK
        const val COL_TIME = DbHelper.COL_TIME
    }
}
