package com.fin.plan.data.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.fin.plan.data.datasource.local.LocalDataSource
import com.fin.plan.domain.dashboard.DashboardItemsController
import com.fin.plan.utils.resetTime
import io.reactivex.schedulers.Schedulers
import java.util.Calendar
import javax.inject.Inject

class RemindersManager @Inject constructor(
    context: Context,
    private val controller: DashboardItemsController,
    private val localDataSource: LocalDataSource
) {

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun updateRemindersForTransactions(): List<TransactionItemData> {
        val transactionItems =
            localDataSource.getOriginalItems()
                .subscribeOn(Schedulers.io())
                .blockingGet()
                .map { it.value }

        val currentMonth = Calendar.getInstance()
        resetTime(currentMonth)

        val remindersListForCurrentMonth =
            loadRemindersWithController(currentMonth, transactionItems)

        currentMonth.add(Calendar.MONTH, 1)

        val remindersListForNextMonth =
            loadRemindersWithController(currentMonth, transactionItems)

        return remindersListForCurrentMonth
            .plus(remindersListForNextMonth)
            .filterNot { it.remindBy == DO_NOT_REMIND }
            .filterNot { it.completed }
    }

    private fun loadRemindersWithController(
        month: Calendar,
        items: List<TransactionItemData>
    ): List<TransactionItemData> {

        controller.run {
            clear()
            loadCachedItems(month.timeInMillis)
        }

        return controller.getDashboardItemsForMonth(month, items)
    }

    fun createNotificationChannel() {
        val notificationChannel = NotificationChannel(
            REMINDERS_CHANNEL_ID,
            REMINDERS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(notificationChannel)
    }

    companion object {
        const val REMINDERS_CHANNEL_ID = "channel_reminder"
        private const val REMINDERS_CHANNEL_NAME = "Transaction reminder"
    }
}