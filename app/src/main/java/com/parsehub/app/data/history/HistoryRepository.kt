package com.parsehub.app.data.history

import android.content.Context
import com.parsehub.app.data.HistoryItem
import com.parsehub.app.data.IParseHistory
import kotlinx.coroutines.runBlocking

/**
 * 历史记录 Repository(spec 7.2:实现 IParseHistory,Room 后端)
 *
 * - 接口 [IParseHistory] 是同步签名(load(): List),内部用 [runBlocking] 桥接 Room suspend DAO
 * - 这是过渡期妥协,下轮 IParseHistory 升级为 Flow 时移除 runBlocking
 * - add 去重由 DAO 的 OnConflictStrategy.REPLACE 保证
 *
 * 注:[init] 在此实现中是空操作 — DAO 在构造时已注入,无需额外初始化。
 * 保留方法仅为满足 [IParseHistory] 接口契约(ParseHistory 委托时调用)。
 */
class HistoryRepository(
    private val dao: HistoryDao
) : IParseHistory {

    override fun init(context: Context) {
        // DAO 已在构造时注入,无需操作
    }

    override fun load(): List<HistoryItem> = runBlocking {
        dao.loadAll().map { it.toHistoryItem() }
    }

    override fun add(item: HistoryItem) = runBlocking {
        dao.upsert(item.toEntity())
    }

    override fun remove(url: String) = runBlocking {
        dao.deleteByUrl(url)
    }

    override fun clear() = runBlocking {
        dao.clear()
    }

    private fun HistoryEntity.toHistoryItem() = HistoryItem(
        url = url,
        platform = platform,
        title = title,
        timestamp = timestamp
    )

    private fun HistoryItem.toEntity() = HistoryEntity(
        url = url,
        platform = platform,
        title = title,
        timestamp = timestamp
    )
}
