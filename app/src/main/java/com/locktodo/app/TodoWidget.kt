package com.locktodo.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Home-screen widget: shows your tasks, tap one to tick it off. */
class TodoWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val id = intent.getLongExtra(EXTRA_TODO_ID, -1L)
            if (id != -1L) TodoStore.get(context).toggle(id) // toggle() refreshes the widget
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.locktodo.app.action.TOGGLE"
        const val EXTRA_TODO_ID = "todo_id"

        fun refresh(c: Context) {
            val manager = AppWidgetManager.getInstance(c) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(c, TodoWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { render(c, manager, it) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        @Suppress("DEPRECATION")
        private fun render(c: Context, manager: AppWidgetManager, widgetId: Int) {
            val pending = TodoStore.get(c).todos.value.count { !it.done }
            val views = RemoteViews(c.packageName, R.layout.widget_tasks)

            views.setTextViewText(
                R.id.widget_count,
                if (pending == 0) c.getString(R.string.widget_all_done)
                else c.resources.getQuantityString(R.plurals.tasks_left, pending, pending),
            )

            // List of tasks, filled by TodoWidgetService.
            val serviceIntent = Intent(c, TodoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Tapping a row toggles it.
            val toggle = PendingIntent.getBroadcast(
                c, 0,
                Intent(c, TodoWidget::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setPendingIntentTemplate(R.id.widget_list, toggle)

            // Header and "+" open the app.
            val open = PendingIntent.getActivity(
                c, 1,
                Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_header, open)
            views.setOnClickPendingIntent(R.id.widget_add, open)
            views.setOnClickPendingIntent(R.id.widget_empty, open)

            manager.updateAppWidget(widgetId, views)
        }
    }
}

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val c: Context) : RemoteViewsFactory {
        private var items: List<Todo> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() {
            items = TodoStore.get(c).todos.value.sortedForDisplay()
        }
        override fun onDestroy() {}
        override fun getCount() = items.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun hasStableIds() = true
        override fun getItemId(position: Int) = items.getOrNull(position)?.id ?: position.toLong()

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(c.packageName, R.layout.widget_item)
            val todo = items.getOrNull(position) ?: return views
            views.setTextViewText(R.id.item_title, todo.title)
            views.setImageViewResource(
                R.id.item_check,
                if (todo.done) R.drawable.ic_checked else R.drawable.ic_unchecked,
            )
            views.setTextColor(R.id.item_title, if (todo.done) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt())
            views.setOnClickFillInIntent(
                R.id.item_root,
                Intent().putExtra(TodoWidget.EXTRA_TODO_ID, todo.id),
            )
            return views
        }
    }
}
