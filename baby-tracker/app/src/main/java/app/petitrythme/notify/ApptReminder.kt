package app.petitrythme.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.petitrythme.BabyApp
import app.petitrythme.MainActivity
import app.petitrythme.R
import app.petitrythme.data.Appointment
import app.petitrythme.data.apptKind
import app.petitrythme.data.questionList
import app.petitrythme.ui.Fmt
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Rappels de rendez-vous : la veille à 19 h et/ou 1 h avant.
 * Une seule alarme est programmée, vers le prochain rappel ; à son déclenchement
 * on affiche ce qui est dû puis on programme le suivant.
 */
object ApptReminder {
    private const val CHANNEL = "appointments"
    private const val REQUEST = 43
    private const val HOUR = 3_600_000L

    /** Heures de rappel d'un rendez-vous. */
    fun reminderTimes(a: Appointment): List<Long> = buildList {
        if (a.remindEve) {
            val eve = Instant.ofEpochMilli(a.time).atZone(Fmt.zone).toLocalDate().minusDays(1)
                .atTime(19, 0).atZone(Fmt.zone).toInstant().toEpochMilli()
            add(eve)
        }
        if (a.remindHour) add(a.time - HOUR)
    }

    /** Prochain rappel à venir, tous rendez-vous confondus. */
    fun next(appts: List<Appointment>, now: Long): Long? =
        appts.flatMap(::reminderTimes).filter { it > now }.minOrNull()

    /**
     * Rappels dus dans ]from, to]. On ignore ceux d'un rendez-vous déjà passé ou trop
     * anciens (téléphone éteint longtemps) pour ne pas notifier hors de propos.
     */
    fun due(appts: List<Appointment>, from: Long, to: Long): List<Appointment> =
        appts.filter { a ->
            a.time > to && reminderTimes(a).any { it in (from + 1)..to && it > to - 12 * HOUR }
        }

    private fun prefs(c: Context) = c.getSharedPreferences("appt_reminder", Context.MODE_PRIVATE)

    suspend fun reschedule(app: BabyApp) {
        val now = System.currentTimeMillis()
        val p = prefs(app)
        if (!p.contains("upTo")) p.edit().putLong("upTo", now).apply()
        val at = next(app.db.appointments().from(now), now)
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            app, REQUEST, Intent(app, ApptReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) am.cancel(pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    suspend fun fire(app: BabyApp) {
        val now = System.currentTimeMillis()
        val p = prefs(app)
        val from = p.getLong("upTo", now - HOUR)
        val to = now + 60_000
        p.edit().putLong("upTo", to).apply()
        val list = due(app.db.appointments().from(now), from, to)
        if (list.isNotEmpty() &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            list.forEach { notify(app, it, now) }
        }
        reschedule(app)
    }

    private fun notify(app: BabyApp, a: Appointment, now: Long) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Rendez-vous", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Rappel la veille ou une heure avant un rendez-vous"
            }
        )
        val open = PendingIntent.getActivity(
            app, 2,
            Intent(app, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_APPOINTMENTS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val k = a.apptKind
        val questions = a.questionList()
        val text = listOfNotNull(
            a.who?.takeIf { it.isNotBlank() },
            a.place?.takeIf { it.isNotBlank() },
            questions.size.takeIf { it > 0 }?.let { "$it question${if (it > 1) "s" else ""} à poser" },
        ).joinToString(" · ")
        nm.notify(
            (3000 + a.id % 1000).toInt(),
            Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("${k.emoji} ${k.label} ${Fmt.apptWhen(a.time, now)}")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(
                    if (questions.isEmpty()) text else text + "\n" + questions.joinToString("\n") { "• $it" }
                ))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_EVENT)
                .build()
        )
    }
}

class ApptReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BabyApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                ApptReminder.fire(app)
            } finally {
                pending.finish()
            }
        }
    }
}
