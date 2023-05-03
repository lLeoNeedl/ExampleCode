package com.fin.plan.services.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.fin.plan.utils.analytics.ErrorHandler

fun startScheduleRemindersWorker(context: Context) {
    try {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            ScheduleRemindersCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            ScheduleRemindersCheckWorker.makeRequest()
        )
    } catch (t: Throwable) {
        ErrorHandler.createInstance().onNotificationError(
            t,
            "Failed to start ScheduleRemindersWorker"
        )
    }
}