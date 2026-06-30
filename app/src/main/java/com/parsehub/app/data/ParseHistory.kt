package com.parsehub.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 解析历史记录（最近 10 条）
 * 用 SharedPreferences 持久化存储，JSON 序列化
 */
data class HistoryItem(
    val url: String,
    val platform: String,
    val title: String,
    val timestamp: Long
)

object ParseHistory {
    private const val PREFS_NAME = "parse_history"
    private const val KEY_HISTORY = "history_items"
    private const val MAX_SIZE = 10

    fun load(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val items = mutableListOf<HistoryItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                items.add(
                    HistoryItem(
                        url = obj.optString("url"),
                        platform = obj.optString("platform"),
                        title = obj.optString("title"),
                        timestamp = obj.optLong("timestamp")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return items
    }

    /**
     * 新增一条记录（相同 URL 自动去重，最新的在最前）
     */
    fun add(context: Context, item: HistoryItem) {
        val current = load(context).toMutableList()
        current.removeAll { it.url == item.url }
        current.add(0, item)
        save(context, current.take(MAX_SIZE))
    }

    fun remove(context: Context, url: String) {
        val current = load(context).toMutableList()
        current.removeAll { it.url == url }
        save(context, current)
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, items: List<HistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("url", item.url)
                    put("platform", item.platform)
                    put("title", item.title)
                    put("timestamp", item.timestamp)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, arr.toString())
            .apply()
    }
}
