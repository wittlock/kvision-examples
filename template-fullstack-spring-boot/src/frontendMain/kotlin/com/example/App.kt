package com.example

import io.kvision.*
import io.kvision.core.onClickLaunch
import io.kvision.form.formPanel
import io.kvision.form.select.select
import io.kvision.form.text.text
import io.kvision.html.*
import io.kvision.i18n.DefaultI18nManager
import io.kvision.i18n.I18n
import io.kvision.panel.fieldsetPanel
import io.kvision.panel.root
import io.kvision.remote.getService
import io.kvision.state.bindEach
import io.kvision.state.observableListOf
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

val AppScope = CoroutineScope(window.asCoroutineDispatcher())

class App : Application() {

    private val todoItemService = getService<ITodoService>()
    private val todoList = observableListOf<TodoItem>()

    override fun start(state: Map<String, Any>) {
        I18n.manager =
            DefaultI18nManager(
                mapOf(
                    "en" to io.kvision.require("i18n/messages-en.json"),
                    "pl" to io.kvision.require("i18n/messages-pl.json")
                )
            )
        val root = root("kvapp") {
            div {
                val form = formPanel<TodoItemForm> {
                    fieldsetPanel(legend = "Uppgift") {
                        text(label = "Uppgift:").bind(TodoItemForm::title, required = true)
                        select(
                            options = listOf("Low" to "Låg", "Medium" to "Mellan", "High" to "Hög"),
                            value = "Mellan",
                            label = "Prioritet"
                        ).bind(TodoItemForm::priority, required = true)
                    }
                }
                button("Lägg till uppgift", "fas fa-plus").onClickLaunch {
                    val formItem = form.getData()
                    val newTodoItem = TodoItem(formItem.title, convertPriority(formItem.priority))
                    todoItemService.addItem(newTodoItem)
                    form.clearData()
                    form.focus()
                }
            }
            div {
                button("Hämta uppgifter").onClickLaunch {
                    todoList.clear()
                    todoList.addAll(todoItemService.getItems().sortedByDescending { it.priority })
                }
            }
            div {
                ul().bindEach(todoList) {
                    li("${it.title} (${it.priority})")
                }
            }

        }
        AppScope.launch {
            val pingResult = Model.ping("Hello world from client!")
            root.add(Span(pingResult))
        }
    }

    private fun convertPriority(priority: String): Priority {
        return when (priority) {
            "Low" -> Priority.LOW
            "High" -> Priority.HIGH
            else -> Priority.MEDIUM
        }
    }
}

@Serializable
data class TodoItemForm(val title: String, val priority: String)

fun main() {
    startApplication(::App, module.hot, BootstrapModule, BootstrapCssModule, CoreModule)
}
