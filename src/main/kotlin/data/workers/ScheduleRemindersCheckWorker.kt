package com.fin.plan.services.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.fin.plan.data.data.RemindersManager
import com.fin.plan.di.DaggerProvider
import com.fin.plan.services.receivers.RemindersReceiver
import com.fin.plan.utils.resetMinutesSecondsMilliseconds
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ScheduleRemindersCheckWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : Worker(context, workerParameters) {

    @Inject lateinit var remindersManager: RemindersManager

    init {
        DaggerProvider.appComponent.inject(this)
    }

    override fun doWork(): Result {
        remindersManager.createNotificationChannel()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDERS_REQUEST_CODE,
            RemindersReceiver.newIntent(context),
            PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance()
        calendar.apply {
            resetMinutesSecondsMilliseconds(this)
            add(Calendar.HOUR_OF_DAY, 1)
        }

        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "scheduling_reminders_check"
        private const val REMINDERS_REQUEST_CODE = 1
        private const val PERIODIC_WORKER_REPEAT_INTERVAL = 30L

        fun makeRequest() =
            PeriodicWorkRequestBuilder<ScheduleRemindersCheckWorker>(
                PERIODIC_WORKER_REPEAT_INTERVAL,
                TimeUnit.MINUTES
            )
                .build()
    }
}