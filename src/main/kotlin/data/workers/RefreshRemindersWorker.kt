package com.fin.plan.services.workers

import android.app.Notification
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.fin.plan.R
import com.fin.plan.data.data.RemindersManager
import com.fin.plan.data.TransactionItemData
import com.fin.plan.data.repositories.TransactionsRepository
import com.fin.plan.di.DaggerProvider
import com.fin.plan.domain.dashboard.DashboardItemsController
import com.fin.plan.presentation.StartActivity
import com.fin.plan.utils.analytics.ErrorHandler
import com.fin.plan.utils.equalDayMonthYear
import com.fin.plan.utils.formatDayAndMonth
import com.fin.plan.utils.getFeatureDate
import com.fin.plan.utils.resetMinutesSecondsMilliseconds
import com.fin.plan.utils.toCalendar
import java.util.Calendar
import javax.inject.Inject

class RefreshRemindersWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    @Inject lateinit var transactionsRepository: TransactionsRepository
    @Inject lateinit var controller: DashboardItemsController
    @Inject lateinit var errorHandler: ErrorHandler
    @Inject lateinit var remindersManager: RemindersManager

    init {
        DaggerProvider.appComponent.inject(this)
    }

    override fun doWork(): Result {
        return try {
            val remindersList = remindersManager.updateRemindersForTransactions()
            handleRemindersList(remindersList)
            Result.success()
        } catch (t: Throwable) {
            errorHandler.onNotificationError(
                t,
                "Failed to update reminders and show notifications"
            )
            Result.retry()
        }
    }

    private fun handleRemindersList(list: List<TransactionItemData>) {
        val scheduledTime = Calendar.getInstance()
        resetMinutesSecondsMilliseconds(scheduledTime)

        list.forEach {
            if (createTimeForReminderFromItem(it) == scheduledTime.timeInMillis) {
                val notification = getNotification(context, it)
                remindersManager.notificationManager.run {
                    notify(it.hashCode(), notification)
                    notify(REMINDERS_GROUP_ID, getNotificationGroup(context))
                }
            }
        }
    }

    private fun createTimeForReminderFromItem(item: TransactionItemData): Long {
        val timeForReminder = item.from.toCalendar()

        timeForReminder.apply {
            add(Calendar.DAY_OF_MONTH, -item.remindBy)
            set(Calendar.HOUR_OF_DAY, item.reminderTime)
        }

        return timeForReminder.timeInMillis
    }

    private fun getNotification(context: Context, item: TransactionItemData): Notification {
        val resultIntent = Intent(context, StartActivity::class.java)
        val resultPendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(resultIntent)
            getPendingIntent(
                NOTIFICATIONS_REQUEST_CODE,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val contentText = getNotificationContentText(item)

        return NotificationCompat.Builder(context, RemindersManager.REMINDERS_CHANNEL_ID)
            .setContentTitle(item.name)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_logo_dark)
            .setContentIntent(resultPendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_REMINDERS)
            .build()
    }

    private fun getNotificationGroup(context: Context) =
        NotificationCompat.Builder(context, RemindersManager.REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_logo_dark)
            .setGroup(GROUP_KEY_REMINDERS)
            .setGroupSummary(true)
            .build()

    private fun getNotificationContentText(item: TransactionItemData): String {
        val currentDay = Calendar.getInstance()
        val nextDay = getFeatureDate(1).time.toCalendar()

        return when {
            currentDay.equalDayMonthYear(item.from.toCalendar()) ->
                context.getString(R.string.reminder_notification_today)

            nextDay.equalDayMonthYear(item.from.toCalendar()) ->
                context.getString(R.string.reminder_notification_tomorrow)

            else -> formatDayAndMonth(item.from)
        }
    }

    companion object {
        const val WORK_NAME = "refreshing_reminders"
        private const val NOTIFICATIONS_REQUEST_CODE = 2
        private const val GROUP_KEY_REMINDERS = "REMINDERS_GROUP"
        private const val REMINDERS_GROUP_ID = 0
        private const val WAKE_LOCK_TAG = "budget_manager:screen_wakelock_tag"
        private const val WAKE_LOCK_TIMEOUT = 1000L

        fun makeRequest() =
            OneTimeWorkRequestBuilder<RefreshRemindersWorker>()
                .build()
    }
}