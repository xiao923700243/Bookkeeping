package com.xiao.bookkeeping.model

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地 SQLite 库，单例持有。
 *
 * 只存本地、不联网；调用方传任意 Context 都会落到 ApplicationContext 上，避免持有 Activity。
 */
class DbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID     INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TYPE   INTEGER NOT NULL,
                $COL_AMOUNT REAL    NOT NULL,
                $COL_REMARK TEXT,
                $COL_TIME   INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // 列表倒序与统计区间查询都按时间过滤
        db.execSQL("CREATE INDEX idx_${TABLE}_$COL_TIME ON $TABLE($COL_TIME)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 当前只有 v1，后续表结构变更在此按 oldVersion 增量迁移
    }

    companion object {
        private const val DB_NAME = "bookkeeping.db"
        private const val DB_VERSION = 1

        const val TABLE = "record"
        const val COL_ID = "id"
        const val COL_TYPE = "type"
        const val COL_AMOUNT = "amount"
        const val COL_REMARK = "remark"
        const val COL_TIME = "time"

        @Volatile
        private var instance: DbHelper? = null

        fun get(context: Context): DbHelper =
            instance ?: synchronized(this) {
                instance ?: DbHelper(context).also { instance = it }
            }
    }
}
