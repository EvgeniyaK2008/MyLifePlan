package com.example.mylifeplan

enum class DeadlinePriority {
    LOW,    // 🐾 Спокойный
    MEDIUM, // ⭐ Важный
    HIGH    // 🔥 Горящий!
}

data class DeadlineItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val dueDate: String, // Формат "2026-10-15 14:00"
    val priority: DeadlinePriority = DeadlinePriority.MEDIUM,
    val isCompleted: Boolean = false
)