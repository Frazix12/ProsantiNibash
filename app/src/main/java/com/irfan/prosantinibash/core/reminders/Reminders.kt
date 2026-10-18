package com.irfan.prosantinibash.core.reminders

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.irfan.prosantinibash.MainActivity
import com.irfan.prosantinibash.ProsantiApplication
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.data.model.Book
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.first
import java.time.*
import java.util.concurrent.TimeUnit

/** Daily best-effort reminders: Android may defer jobs for power saving; no exact-alarm permission. */
object Reminders {
    fun schedule(context:Context,enabled:Boolean) {
        val manager=WorkManager.getInstance(context)
        if(!enabled) { manager.cancelUniqueWork("building-reminders");return }
        val now=ZonedDateTime.now(BuildingDates.zone)
        var next=now.toLocalDate().atTime(9,0).atZone(BuildingDates.zone)
        if(!next.isAfter(now)) next=next.plusDays(1)
        val request=PeriodicWorkRequestBuilder<ReminderWorker>(1,TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now,next).toMillis(),TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        manager.enqueueUniquePeriodicWork("building-reminders",ExistingPeriodicWorkPolicy.KEEP,request)
    }
    fun items(summary: JsonObject, today: LocalDate): List<String> = buildList {
        val oldDebt = summary.getValue("old_due").jsonPrimitive.long
        val debt = summary.getValue("due").jsonPrimitive.long
        if (oldDebt > 0 || (today.dayOfMonth >= 6 && debt > 0)) add(
            if (oldDebt == 0L && today.dayOfMonth == 6) "Rent due today" else "Outstanding rent needs attention")
        if (summary.getValue("agreement").jsonPrimitive.boolean) add("Agreement ending within 30 days")
        if (summary.getValue("recurring").jsonPrimitive.boolean) add("Recurring expense due")
        if (summary.getValue("maintenance").jsonPrimitive.boolean) add("Maintenance reminder")
    }
    fun items(book:Book,today:LocalDate):List<String> {
        val messages=mutableListOf<String>()
        val month=YearMonth.from(today)
        val oldDebt=BookMath.sum(book.tenancies.map { BookMath.due(book,it.id,month.minusMonths(1)) })
        val debt=BookMath.sum(book.tenancies.map { BookMath.due(book,it.id,month) })
        if(oldDebt>0 || (today.dayOfMonth>=6 && debt>0)) messages +=
            if(oldDebt==0L && today.dayOfMonth==6) "Rent due today" else "Outstanding rent needs attention"
        if(book.tenancies.any { it.status=="active" && it.agreement_end_date?.let { date ->
            LocalDate.parse(date) in today..today.plusDays(30) }==true }) messages += "Agreement ending within 30 days"
        if(book.recurring_expenses.any { it.active && LocalDate.parse(it.next_date)<=today }) messages += "Recurring expense due"
        if(book.maintenance.any { !it.voided && it.reminder_date?.let { date -> LocalDate.parse(date)<=today }==true }) messages += "Maintenance reminder"
        return messages
    }
}
class ReminderWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        try {
            val app=applicationContext as ProsantiApplication
            val preferences=app.container.preferences.state.first { !it.loading }
            if(preferences.error!=null || !preferences.preferences.remindersEnabled) return Result.success()
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return Result.success()
            val today=LocalDate.now(BuildingDates.zone)
            val cache=applicationContext.getSharedPreferences("reminder-state",Context.MODE_PRIVATE)
            if(cache.getString("day",null)==today.toString()) return Result.success()
            val repository=app.container.bookRepository
            val summary=repository.reminderSummary()
            val items=if(summary!=null) Reminders.items(summary,today) else Reminders.items(repository.load(),today)
            if(items.isNotEmpty()) notify(items)
            if(!cache.edit().putString("day",today.toString()).commit()) return Result.retry()
            return Result.success()
        } catch(error:CancellationException) { throw error }
        catch(_:Exception) { return if(runAttemptCount<3) Result.retry() else Result.failure() }
    }
    private fun notify(items:List<String>) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(applicationContext,
            Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val channel="building-reminders"
        if(Build.VERSION.SDK_INT>=26) applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channel,AppText.get(R.string.building_reminders),NotificationManager.IMPORTANCE_DEFAULT))
        val pending=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(applicationContext,channel).setSmallIcon(R.drawable.ic_home)
            .setContentTitle(AppText.get(R.string.prosanti_nibash)).setContentText(appLabel(items.first()))
            .setStyle(NotificationCompat.BigTextStyle().bigText(items.joinToString("\n") { appLabel(it) }))
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        NotificationManagerCompat.from(applicationContext).notify(900,notification)
    }
}
