package com.parsehub.app.data

import android.content.Context

/**
 * ParseHistory 接口(spec 3.3:接口化便于 Mock)
 *
 * 现有 [ParseHistory] object 实现此接口。
 *
 * Phase ViewModel 接入后,Context 由实现层([ParseHistory.init])持有,
 * 接口方法不再需要 Context,VM 可零 Context 依赖。
 */
interface IParseHistory {
    fun load(): List<HistoryItem>
    fun add(item: HistoryItem)
    fun remove(url: String)
    fun clear()

    /**
     * 一次性注入 ApplicationContext(由 MainActivity.onCreate 调用)。
     * 接口暴露此方法仅为兼容现有 object 单例实现;若后续改为 class 注入,可移除。
     */
    fun init(context: Context)
}
