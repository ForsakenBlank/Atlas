package com.forsakenblank.atlas.extras

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.forsakenblank.atlas.AtlasApp
import com.forsakenblank.atlas.AtlasApplication
import com.forsakenblank.atlas.MainActivity
import com.forsakenblank.atlas.R
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.startOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// today's tasks and habits on the home screen, with buttons straight into atlas
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TodayWidget::class.java)
            if (manager.getAppWidgetIds(component).isEmpty()) return
            val atlas = (context.applicationContext as AtlasApplication).atlas
            atlas.appScope.launch(Dispatchers.IO) {
                runCatching { manager.updateAppWidget(component, build(context, atlas)) }
            }
        }

        private suspend fun build(context: Context, atlas: AtlasApp): RemoteViews {
            val today = LocalDate.now()
            val endOfToday = today.plusDays(1).startMillis()
            val due = atlas.repository.tasks().first()
                .filter { !it.done && it.due != null && it.due!! < endOfToday }
                .sortedWith(compareBy({ it.due }, { -it.priority }))
            val taskText = if (due.isEmpty()) {
                "Nothing due today ✨"
            } else {
                val shown = due.take(3).joinToString("\n") { task ->
                    (if (task.due!! < today.startMillis()) "⚠ " else "• ") + task.title
                }
                if (due.size > 3) "$shown\n+ ${due.size - 3} more" else shown
            }

            val habits = atlas.repository.trackers().first()
                .filter { it.tracker.kind == TrackerKind.YES_NO || it.tracker.kind == TrackerKind.COUNTER }
            val loggedToday = atlas.repository.logsSince(startOfDay()).first().groupingBy { it.trackerId }.eachCount()
            val done = habits.count { habit ->
                val goal = if (habit.tracker.kind == TrackerKind.COUNTER) (habit.tracker.dailyGoal ?: 1) else 1
                (loggedToday[habit.item.id] ?: 0) >= goal
            }

            return RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_date, today.format(dateFormat))
                setTextViewText(R.id.widget_tasks, taskText)
                setTextViewText(R.id.widget_habits, if (habits.isEmpty()) "" else "$done of ${habits.size} habits done")
                setOnClickPendingIntent(R.id.widget_root, open(context, null, 0))
                setOnClickPendingIntent(R.id.widget_tasks, open(context, MainActivity.ACTION_TASKS, 1))
                setOnClickPendingIntent(R.id.widget_note, open(context, MainActivity.ACTION_NEW_NOTE, 2))
                setOnClickPendingIntent(R.id.widget_focus, open(context, MainActivity.ACTION_FOCUS, 3))
                setOnClickPendingIntent(R.id.widget_journal, open(context, MainActivity.ACTION_JOURNAL, 4))
                setOnClickPendingIntent(R.id.widget_habits, open(context, MainActivity.ACTION_HABITS, 5))
            }
        }

        private fun open(context: Context, action: String?, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(action ?: Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
