package com.example.mylifeplan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DeadlineManager(context: Context) {
    private val prefs = context.getSharedPreferences("MyLifePlanDeadlines", Context.MODE_PRIVATE)

    fun saveDeadline(item: DeadlineItem) {
        val list = getAllDeadlines().toMutableList()
        list.add(item)
        saveList(list)
    }

    fun getAllDeadlines(): List<DeadlineItem> {
        val rawJson = prefs.getString("deadlines_json", "[]") ?: "[]"
        val array = JSONArray(rawJson)
        val result = mutableListOf<DeadlineItem>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                DeadlineItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    dueDate = obj.getString("dueDate"),
                    priority = DeadlinePriority.valueOf(obj.optString("priority", "MEDIUM")),
                    isCompleted = obj.optBoolean("isCompleted", false)
                )
            )
        }
        return result
    }

    private fun saveList(list: List<DeadlineItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("dueDate", item.dueDate)
            obj.put("priority", item.priority.name)
            obj.put("isCompleted", item.isCompleted)
            array.put(obj)
        }
        prefs.edit().putString("deadlines_json", array.toString()).apply()
    }

    /**
     * Возвращает компактную строчку для Gemini (только невыполненные, топ-3)
     */
    fun getTopDeadlinesSummary(): String {
        val active = getAllDeadlines().filter { !it.isCompleted }.take(3)
        if (active.isEmpty()) return "No urgent deadlines."

        return active.joinToString("; ") {
            "${it.title} (Due: ${it.dueDate}, Priority: ${it.priority.name})"
        }
    }
}