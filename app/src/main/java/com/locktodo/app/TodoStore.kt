package com.locktodo.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Todo(
    val id: Long,
    val title: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Single source of truth for tasks, shared by the app, the lock-screen view and the notification. */
class TodoStore private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("todos", Context.MODE_PRIVATE)
    private val _todos = MutableStateFlow(load())
    val todos: StateFlow<List<Todo>> = _todos.asStateFlow()

    fun add(title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        save(listOf(Todo(id = System.nanoTime(), title = clean)) + _todos.value)
    }

    fun toggle(id: Long) = save(_todos.value.map { if (it.id == id) it.copy(done = !it.done) else it })

    fun delete(id: Long) = save(_todos.value.filterNot { it.id == id })

    fun clearDone() = save(_todos.value.filterNot { it.done })

    @Synchronized
    private fun save(list: List<Todo>) {
        _todos.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("done", it.done)
                    .put("createdAt", it.createdAt)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        LockScreenNotifier.update(context)
        TodoWidget.refresh(context)
    }

    private fun load(): List<Todo> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Todo(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    done = o.optBoolean("done"),
                    createdAt = o.optLong("createdAt"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY = "items"

        @Volatile
        private var instance: TodoStore? = null

        fun get(context: Context): TodoStore =
            instance ?: synchronized(this) {
                instance ?: TodoStore(context.applicationContext).also { instance = it }
            }
    }
}

/** Pending first, newest first; finished tasks sink to the bottom. */
fun List<Todo>.sortedForDisplay(): List<Todo> =
    sortedWith(compareBy<Todo> { it.done }.thenByDescending { it.createdAt })
