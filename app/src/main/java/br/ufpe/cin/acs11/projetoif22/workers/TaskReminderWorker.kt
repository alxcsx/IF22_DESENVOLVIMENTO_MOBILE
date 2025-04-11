package br.ufpe.cin.acs11.projetoif22.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import br.ufpe.cin.acs11.projetoif22.R
import br.ufpe.cin.acs11.projetoif22.data.Task
import br.ufpe.cin.acs11.projetoif22.data.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TaskReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val NOTIFICATION_THRESHOLD_MINUTES: Long = 10
        const val TASK_ID_KEY = "task_id"

        private fun notificationTag(taskId: Int): String = "task_reminder_${taskId}"

        private fun notificationTag(task: Task): String = notificationTag(task.id)

        fun scheduleRemindersForTask(context: Context, task: Task) {
            WorkManager.getInstance(context).cancelAllWorkByTag(notificationTag(task))
            if (task.isCompleted) return

            val now = Calendar.getInstance().timeInMillis
            val taskDeadline = task.deadline.time
            val tenMinutesBeforeDeadline = taskDeadline - TimeUnit.MINUTES.toMillis(NOTIFICATION_THRESHOLD_MINUTES)

            if (tenMinutesBeforeDeadline <= now) return

            val delayMillis = tenMinutesBeforeDeadline - now

            val inputData = Data.Builder()
                .putInt(TASK_ID_KEY, task.id)
                .build()

            val reminderRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(notificationTag(task))
                .build()

            WorkManager.getInstance(context).enqueue(reminderRequest)
        }

        fun scheduleAllReminders(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val taskDao = TaskDatabase.getDatabase(context).taskDao()
                val tasks = taskDao.getAllTasksSync()
                tasks.forEach { task -> scheduleRemindersForTask(context, task) }
            }
        }

        fun cancelReminderForTask(context: Context, taskId: Int) {
            WorkManager.getInstance(context).cancelAllWorkByTag(notificationTag(taskId))
        }
    }

    override fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS);
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                return Result.failure()
            }
        }

        val taskId = inputData.getInt(TASK_ID_KEY, -1)

        if (taskId == -1) return Result.failure()

        val (shouldShowNotification, task) = validateTaskSync(taskId)
        if (!shouldShowNotification || task == null) {
            return Result.success()
        }

        createNotificationChannel()
        showNotification(task)

        return Result.success()
    }

    private fun validateTaskSync(taskId: Int): Pair<Boolean, Task?> {
        val taskDao = TaskDatabase.getDatabase(context).taskDao()
        return runBlocking {
            val task = taskDao.getTaskById(taskId)
            Pair(task != null && !task.isCompleted, task)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.task_notification_channel_name)
            val descriptionText = context.getString(R.string.task_notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(task: Task) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = context.getString(
            R.string.task_notification_text,
            task.title,
            NOTIFICATION_THRESHOLD_MINUTES.toString()
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.task_notification_title))
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        notificationManager.notify(task.id, notification)
    }
}