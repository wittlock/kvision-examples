package com.example

import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
actual class PingService : IPingService {

    override suspend fun ping(message: String): String {
        println(message)
        return "Hello world from server!"
    }
}

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Suppress("ACTUAL_WITHOUT_EXPECT")
actual class TodoService : ITodoService {
    private val todoList = mutableListOf<TodoItem>()
    override suspend fun addItem(newTodoItem: TodoItem): Boolean {
        this.todoList.add(newTodoItem)
        println("Number of todos is now: ${todoList.size}")
        return true
    }

    override suspend fun getItems() = todoList
}
