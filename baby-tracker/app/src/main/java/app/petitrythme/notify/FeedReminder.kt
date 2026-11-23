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
import app.petitrythme.data.EventType
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.describe
import app.petitrythme.ui.nextSide
import kotlinx.coroutines.launch

/**
 * Rappel facultatif « prochain repas » : une notification X heures après le
 * début du dernier repas. Replanifié à chaque modification des données.
 */
object FeedReminder {
    private const val CHANNEL = "feed_reminder"
    private const val NOTIF_ID = 10
    private const val REQUEST = 42

    /** Heure du prochain rappel, ou null s'il n'y a rien à programmer. */
    fun nextAt(lastFeedStart: Long?, intervalMin: Int, nursingRunning: Boolean, now: Long): Long? {
        if (intervalMin <= 0 || lastFeedStart == null || nursingRunning) return null
        return (lastFeedStart + intervalMin * 60_000L).takeIf { it > now }
    }

    suspend fun reschedule(app: BabyApp) {
        val last = app.db.events().latest(EventType.FEEDINGS)
        val at = nextAt(
            last?.time,
            app.profile.state.value.feedReminderMin,
            app.timers.state.value.nursing != null,
            System.currentTimeMillis(),
        )
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            app, REQUEST, Intent(app, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            am.cancel(pi)
        } else {
            // Alarme inexacte (quelques minutes de marge) : pas besoin d'autorisation spéciale.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            // Un nouveau repas rend l'éventuel rappel affiché obsolète.
            app.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        }
    }

    suspend fun show(app: BabyApp) {
        if (app.profile.state.value.feedReminderMin <= 0 || app.timers.state.value.nursing != null) return
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val dao = app.db.events()
        val last = dao.latest(EventType.FEEDINGS) ?: return
        val now = System.currentTimeMillis()
        val side = nextSide(dao.latest(listOf(EventType.NURSING)))
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Rappel de repas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Prévient quand le dernier repas date d'un certain temps"
            }
        )
        val open = PendingIntent.getActivity(
            app, 1, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = buildString {
            append(describe(last).first.substringBefore(" · "))
            append(" à ").append(Fmt.time(last.time))
            if (side != null) append(" · prochain sein : ").append(side.label.lowercase())
        }
        nm.notify(
            NOTIF_ID,
            Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("Dernier repas ${Fmt.ago(last.time, now)}")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BabyApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                FeedReminder.show(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Après un redémarrage : notifications des chronos, widget et rappel sont reconstruits. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Le simple démarrage de l'application (BabyApp.onCreate) fait déjà tout le travail.
        (context.applicationContext as BabyApp).refresh()
    }
}
