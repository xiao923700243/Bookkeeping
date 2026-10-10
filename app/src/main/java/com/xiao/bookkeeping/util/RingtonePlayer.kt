package com.xiao.bookkeeping.util

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import com.xiao.bookkeeping.R

/**
 * 入账铃声播放器。应用级单例，不随 Activity 释放——保存成功后页面会立刻 finish，
 * 播放器若跟着 Activity 走就会被连带销毁，声音刚起就断。
 *
 * ## 为什么用 MediaPlayer 而不是 SoundPool
 *
 * SoundPool 看似更适合短提示音，但它引入了一条异步加载状态机，实测出两个静默失败点：
 * `load()` 直接返回 0 时 `OnLoadCompleteListener` 永远不会回调，播放请求就一直排队等下去；
 * `play()` 返回 0（失败）也不会报错。两者都会表现为「一点声音都没有」，且无从察觉。
 *
 * MediaPlayer 对本地资源是同步准备，没有加载状态机，失败会明确返回 null。
 * 这笔录音只有 1.4 秒、一天响不了几次，首播那点延迟无感，可靠性更值钱。
 *
 * ## 音频流
 *
 * 用默认的 `USAGE_MEDIA`（媒体流）而不是 `USAGE_ASSISTANCE_SONIFICATION`（系统音效流）：
 * 系统音效流在国内 ROM 上常被关闭，且在静音/振动模式下会被一并静音，
 * 而「入账必须听得见」正是这个功能存在的意义。
 */
class RingtonePlayer private constructor(context: Context) {

    private val appContext = context.applicationContext

    /** 持有当前播放器：局部变量可能被 GC，导致播放中途断掉。 */
    private var current: MediaPlayer? = null

    @Synchronized
    fun play() {
        stop()

        val player = MediaPlayer.create(appContext, R.raw.xm3237)
        if (player == null) {
            Log.w(TAG, "入账铃声创建失败，降级为系统提示音")
            playSystemFallback()
            return
        }

        current = player
        player.setOnCompletionListener { stop() }
        player.setOnErrorListener { mp, what, extra ->
            Log.w(TAG, "入账铃声播放出错 what=$what extra=$extra")
            runCatching { mp.release() }
            if (current === mp) current = null
            true
        }
        player.start()
        Log.i(TAG, "已播放入账铃声，媒体音量 ${mediaVolume()}")
    }

    /**
     * 排查用：铃声走媒体流，音量若为 0 就是「没声音」的直接原因，而非代码问题。
     */
    private fun mediaVolume(): String = runCatching {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        "${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"
    }.getOrDefault("未知")

    @Synchronized
    private fun stop() {
        current?.let { runCatching { it.release() } }
        current = null
    }

    @Synchronized
    private fun playSystemFallback() {
        runCatching {
            RingtoneManager.getRingtone(
                appContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )?.play()
        }.onFailure { Log.w(TAG, "系统提示音播放失败", it) }
    }

    companion object {
        private const val TAG = "RingtonePlayer"

        @Volatile
        private var instance: RingtonePlayer? = null

        fun get(context: Context): RingtonePlayer =
            instance ?: synchronized(this) {
                instance ?: RingtonePlayer(context).also { instance = it }
            }
    }
}
