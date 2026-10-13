package com.xiao.bookkeeping.util

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.xiao.bookkeeping.model.Record
import com.xiao.bookkeeping.model.RecordType

/**
 * 备份文件的读写。
 *
 * 格式是带版本号的一层 JSON，一眼能看懂，用户想自己核对也能打开看：
 *
 * ```json
 * {
 *   "format": "bookkeeping-backup",
 *   "version": 1,
 *   "exportedAt": 1760000000000,
 *   "records": [
 *     { "type": 1, "amount": 150.0, "remark": "外卖", "time": 1759132800000 }
 *   ]
 * }
 * ```
 *
 * 几条约定：
 *
 * - `type` 存 [RecordType.code]，跟数据库是同一套值，不存 enum 的 ordinal。
 * - 不存 `id`：它是自增主键、界面从不展示，导入时重新分配即可。
 * - `amount` 原样搬运。录入时已经由 [MoneyUtils.parse] 量化到分，这里再取整一次
 *   只会让往返不再严格无损。
 * - 多余字段一律忽略，将来加分类、账户这类字段时，旧版本读新文件不至于整份失败。
 *
 * 这个类是纯 Kotlin + Gson，不碰 Android API，所以能直接用 JVM 单测覆盖。
 */
object BackupCodec {

    /** 握手字段。用它认「这是本应用的备份」，比扩展名可靠。 */
    const val FORMAT = "bookkeeping-backup"

    /** 格式版本。只是加字段不用动它；语义变了才加一，并在 [decode] 里按版本分支。 */
    const val VERSION = 1

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    /** 把整本账打成一份备份文本。 */
    fun encode(records: List<Record>, exportedAt: Long): String = gson.toJson(
        BackupDto().apply {
            format = FORMAT
            version = VERSION
            this.exportedAt = exportedAt
            this.records = records.map { record ->
                RecordDto().apply {
                    type = record.type.code
                    amount = record.amount
                    remark = record.remark
                    time = record.time
                }
            }
        }
    )

    /**
     * 解一份备份文本，逐条校验后返回。
     *
     * 整个文件先全部校验完才返回，任何一处不对都抛 [BackupFormatException]——
     * 调用方拿到结果时就意味着这份文件可以放心写库了。
     */
    fun decode(text: String): Backup {
        // 记事本存 UTF-8 会带 BOM，用户打开备份看过一眼再存回去就会带上
        val json = text.removePrefix(BOM)
        // 空文件先挡掉：Gson 对空输入是返回 null 而不是报错，放下去会走到
        // 「不是我们的结构」那一支，把拷了一半的空文件说成「选错文件了」
        if (json.isBlank()) throw BackupFormatException(BackupProblem.MALFORMED)

        // 根节点分两步看：先要「是合法 JSON」，再看「是不是我们的结构」。
        // 这两种情况对用户是两回事：文件坏了 vs 选错了文件。
        val root = try {
            JsonParser.parseString(json)
        } catch (e: JsonParseException) {
            // 抓父类而不是它的子类 JsonSyntaxException：这层可能抛出的不止一个子类
            throw BackupFormatException(BackupProblem.MALFORMED)
        }
        if (!root.isJsonObject) throw BackupFormatException(BackupProblem.NOT_BACKUP)

        val file = try {
            gson.fromJson(root, BackupDto::class.java)
        } catch (e: JsonParseException) {
            // 字段类型对不上（records 写成数字、version 写成字母）算文件坏了；
            // 类型对但值越界（type = 2）留给下面按条报，好让用户知道是第几条
            throw BackupFormatException(BackupProblem.MALFORMED)
        }

        if (file.format != FORMAT) throw BackupFormatException(BackupProblem.NOT_BACKUP)
        val version = file.version ?: throw BackupFormatException(BackupProblem.MALFORMED)
        // 新版本可能带了本版本读不懂的字段，照读会把数据读残，宁可拒绝
        if (version > VERSION) throw BackupFormatException(BackupProblem.NEWER_VERSION)
        if (version <= 0) throw BackupFormatException(BackupProblem.MALFORMED)
        val exportedAt = file.exportedAt ?: throw BackupFormatException(BackupProblem.MALFORMED)
        val rows = file.records ?: throw BackupFormatException(BackupProblem.MALFORMED)

        return Backup(rows.mapIndexed { index, row -> row.toRecord(index) }, exportedAt)
    }

    /**
     * 校验一条记录并转成 [Record]。
     *
     * 校验不过就整份放弃，不跳过坏的那条——半份账本比没有账本更危险，
     * 用户会以为自己恢复了全部数据。
     */
    private fun RecordDto.toRecord(index: Int): Record {
        val code = type ?: throw BackupFormatException(BackupProblem.BAD_RECORD, index)
        // 这里不能用 RecordType.fromCode：它把未知 code 兜底成支出，
        // 外面文件里的脏数据会被当成正经账目存进库
        val recordType = RecordType.fromCodeOrNull(code)
            ?: throw BackupFormatException(BackupProblem.BAD_RECORD, index)

        val value = amount ?: throw BackupFormatException(BackupProblem.BAD_RECORD, index)
        // 非有限值（1e400 那种写法）会让所有求和变成 NaN
        if (!value.isFinite() || value <= 0) {
            throw BackupFormatException(BackupProblem.BAD_RECORD, index)
        }

        val at = time ?: throw BackupFormatException(BackupProblem.BAD_RECORD, index)
        if (at <= 0) throw BackupFormatException(BackupProblem.BAD_RECORD, index)

        return Record(type = recordType, amount = value, remark = remark.orEmpty(), time = at)
    }
    /** UTF-8 BOM。记事本存 UTF-8 会带上它，用户打开备份看过一眼再存回去就会带上。 */
    private const val BOM = "\uFEFF"

    /**
     * 文件里的一层结构。
     *
     * 字段全可空是必须的：只有「缺了」和「是 0」能分开，才谈得上校验。
     * Gson 给 Kotlin 的非空字段塞 null 是不报错的，全靠这里挡住。
     */
    @Keep
    private class BackupDto {
        var format: String? = null
        var version: Int? = null
        var exportedAt: Long? = null
        var records: List<RecordDto>? = null
    }

    /** 文件里的一条记录。字段可空的理由同上。 */
    @Keep
    private class RecordDto {
        var type: Int? = null
        var amount: Double? = null
        var remark: String? = null
        var time: Long? = null
    }
}

/**
 * 一份解出来的备份。
 *
 * [exportedAt] 是导出时刻，导入确认弹窗里要拿它告诉用户「这是哪次导的备份」——
 * 手里攒了几份备份时，这是认出选错文件最直接的线索。
 */
data class Backup(val records: List<Record>, val exportedAt: Long)

/** 备份文件不合法。[index] 只在 [BackupProblem.BAD_RECORD] 时有意义，是出问题的记录下标（从 0 起）。 */
class BackupFormatException(
    val problem: BackupProblem,
    val index: Int = -1
) : Exception("$problem at $index")

enum class BackupProblem {
    /** 不是合法 JSON，或结构对不上（缺字段、字段类型不对）。 */
    MALFORMED,

    /** 是合法 JSON，但不是本应用导出的备份。 */
    NOT_BACKUP,

    /** 备份的格式版本比当前应用新。 */
    NEWER_VERSION,

    /** 某一条记录的字段值越界，见 [BackupFormatException.index]。 */
    BAD_RECORD
}
