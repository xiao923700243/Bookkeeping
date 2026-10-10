package com.xiao.bookkeeping.base

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Presenter 基类，统一处理两件容易出错的事：
 *
 * 1. **生命周期**：[detach] 必须释放 View 引用，否则 Presenter 会拖着已销毁的 Activity 泄漏。
 * 2. **线程**：数据库读写一律走子线程，结果切回主线程再回调 View。
 *    这样 Presenter 不必持有 lifecycleScope，也就不会被 Android 生命周期绑死。
 */
abstract class BasePresenter<V : BaseView> {

    private var viewRef: V? = null
    private var worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    protected val view: V? get() = viewRef

    open fun attach(view: V) {
        // 配置变更后 Activity 会重建：detach 已 shutdown 过线程池，这里需要重新开一个，
        // 否则旋转屏幕后所有数据库操作都会静默失效。
        if (worker.isShutdown) worker = Executors.newSingleThreadExecutor()
        viewRef = view
    }

    open fun detach() {
        viewRef = null
        worker.shutdown()
    }

    /** 子线程执行 [task]，不需要返回值。 */
    protected fun runAsync(task: () -> Unit) {
        if (worker.isShutdown) return
        worker.execute {
            try {
                task()
            } catch (t: Throwable) {
                postToMain { onError(t) }
            }
        }
    }

    /** 子线程执行 [task]，结果切回主线程交给 [onSuccess]。 */
    protected fun <T> runAsync(task: () -> T, onSuccess: (T) -> Unit) {
        if (worker.isShutdown) return
        worker.execute {
            val result = runCatching(task)
            postToMain {
                result.fold(
                    onSuccess = onSuccess,
                    onFailure = { onError(it) }
                )
            }
        }
    }

    /** 后台任务抛异常时的回调，子类按需覆盖以给出业务提示。 */
    protected open fun onError(t: Throwable) {
        Log.e(javaClass.simpleName, "后台任务执行失败", t)
    }

    /** 保证回调落在主线程。View 已 detach 时 [view] 为 null，回调自然丢弃，不会泄漏。 */
    protected fun postToMain(action: () -> Unit) {
        mainHandler.post(action)
    }
}
