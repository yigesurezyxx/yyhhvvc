package com.parsehub.app.data

import android.content.Context

/**
 * 历史记录入口(spec 7.3:委托 HistoryRepository,保持 IParseHistory 实现不变)
 *
 * - MainActivity 调用 [init] 时构建 Room DB + Repository
 * - 其余方法全部委托给 [repo]
 * - HistoryItem data class 仍定义在此处(被 UI 层引用)
 *
 * 注:旧 SharedPreferences 实现已废弃,数据不迁移(本轮为首次接入 Room)。
 */
data class HistoryItem(
    val url: String,
    val platform: String,
    val title: String,
    val timestamp: Long
)

object ParseHistory : IParseHistory {

    private var repo: IParseHistory? = null

    override fun init(context: Context) {
        if (repo == null) {
            synchronized(this) {
                if (repo == null) {
                    val dao = com.parsehub.app.data.history.HistoryDatabase
                        .get(context).historyDao()
                    repo = com.parsehub.app.data.history.HistoryRepository(dao)
                }
            }
        }
    }

    private fun delegate(): IParseHistory =
        repo ?: error("ParseHistory 未初始化,请先调用 init(context)")

    override fun load(): List<HistoryItem> = delegate().load()

    override fun add(item: HistoryItem) = delegate().add(item)

    override fun remove(url: String) = delegate().remove(url)

    override fun clear() = delegate().clear()
}
