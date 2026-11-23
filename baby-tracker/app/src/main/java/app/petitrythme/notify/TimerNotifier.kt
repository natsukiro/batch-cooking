package app.petitrythme.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.petitrythme.MainActivity
import app.petitrythme.R
import app.petitrythme.data.Side
import app.petitrythme.data.Timers
import app.petitrythme.ui.Fmt

/**
 * Notification persistante pendant un chronomètre (tétée, sommeil, tire-lait),
 * pour voir le temps écoulé depuis l'écran verrouillé.
 */
object TimerNotifier {
    private const val CHANNEL = "timers"
    private const val ID_NURSING = 1
    private const val ID_SLEEP = 2
    private const val ID_PUMP = 3

    fun sync(context: Context, t: Timers) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Chronomètres en cours", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Affiche la tétée, le sommeil ou le tire-lait en cours"
                setShowBadge(false)
            }
        )
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val now = System.currentTimeMillis()

        fun base() = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_STOPWATCH)

        val n = t.nursing
        if (n != null) {
            val b = base()
            val split = "G ${Fmt.chrono(n.leftAt(now))} · D ${Fmt.chrono(n.rightAt(now))}"
            if (n.paused) {
                b.setContentTitle("Tétée en pause").setContentText(split).setShowWhen(false)
            } else {
                b.setContentTitle("Tétée · sein ${if (n.side == Side.L) "gauche" else "droit"}")
                    .setContentText("Appuyez pour changer de sein ou terminer")
                    .setUsesChronometer(true)
                    .setShowWhen(true)
                    .setWhen(now - n.totalAt(now))
            }
            nm.notify(ID_NURSING, b.build())
        } else nm.cancel(ID_NURSING)

        val s = t.sleepStart
        if (s != null) {
            nm.notify(
                ID_SLEEP,
                base().setContentTitle("Bébé dort depuis ${Fmt.time(s)}")
                    .setContentText("Appuyez pour noter le réveil")
                    .setUsesChronometer(true).setShowWhen(true).setWhen(s)
                    .build()
            )
        } else nm.cancel(ID_SLEEP)

        val p = t.pumpStart
        if (p != null) {
            nm.notify(
                ID_PUMP,
                base().setContentTitle("Tire-lait en cours")
                    .setUsesChronometer(true).setShowWhen(true).setWhen(p)
                    .build()
            )
        } else nm.cancel(ID_PUMP)
    }
}
