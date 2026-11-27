package com.example.mylifeplan

import android.content.DialogInterface
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class ToDo {
    data class ToDoItem(val id: String, val text: String, var completed: Boolean = false) {
        fun complete() {
            completed = true
            println("Task '$text' (ID: $id) marked as complete")
        }
    }

    // Example usage (using the generated HTML):
    fun main() {
        val todoItems = listOf(
            ToDoItem("0.4292584275375174", "Buy groceries"),
            ToDoItem("0.634584481144216", "Call John")
        )

        todoItems.forEach { item ->
            if (!item.completed) {
                item.complete()
            }
        }
    }
}