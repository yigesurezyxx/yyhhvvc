package com.parsehub.app.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 历史记录 Room 实体(spec 7.1)
 *
 * - 以 url 为主键(同 URL 去重,对齐旧 ParseHistory 行为)
 * - 字段与 [com.parsehub.app.data.HistoryItem] 一一对应
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val platform: String,
    val title: String,
    val timestamp: Long
)
