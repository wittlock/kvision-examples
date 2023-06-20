package com.example

import io.kvision.annotations.KVService
import kotlinx.serialization.Serializable

@KVService
interface IPingService {
    suspend fun ping(message: String): String
}

@Serializable
enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}

@Serializable
data class TodoItem(val title: String, val priority: Priority = Priority.MEDIUM)

@KVService
interface ITodoService {
    suspend fun addItem(newTodoItem: TodoItem): Boolean
    suspend fun getItems(): List<TodoItem>
}
