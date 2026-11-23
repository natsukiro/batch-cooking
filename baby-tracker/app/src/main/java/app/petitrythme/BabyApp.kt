package app.petitrythme

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.room.InvalidationTracker
import app.petitrythme.data.AppDatabase
import app.petitrythme.data.ProfileStore
import app.petitrythme.data.TimerStore
import app.petitrythme.notify.ApptReminder
import app.petitrythme.notify.FeedReminder
import app.petitrythme.notify.TimerNotifier
import app.petitrythme.quick.Shortcuts
import app.petitrythme.sync.SyncConfig
import app.petitrythme.sync.SyncManager
import app.petitrythme.sync.SyncScheduler
import app.petitrythme.widget.BabyWidget
import app.petitrythme.widget.WidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BabyApp : Application() {
    /** Portée de l'appli : écritures et rafraîchissements qui doivent survivre à un écran fermé. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var db: AppDatabase
        private set
    lateinit var profile: ProfileStore
        private set
    lateinit var timers: TimerStore
        private set
    lateinit var widgetStore: WidgetStore
        private set
    lateinit var sync: SyncManager
        private set

    // Regroupe les demandes de rafraîchissement rapprochées (une seule mise à jour du widget).
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        widgetStore = WidgetStore(this)
        profile = ProfileStore(this) { refresh() }
        timers = TimerStore(this) {
            TimerNotifier.sync(this, it)
            refresh()
        }
        // Toute écriture (appli, widget, raccourci, restauration…) rafraîchit le widget
        // et replanifie les rappels (repas, rendez-vous).
        sync = SyncManager(db, profile, timers, SyncConfig(this))
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer("events", "appointments", "tombstones") {
            override fun onInvalidated(tables: Set<String>) = refresh()
        })
        appScope.launch {
            for (r in refreshRequests) {
                delay(300)
                runCatching { BabyWidget().updateAll(this@BabyApp) }
                runCatching { FeedReminder.reschedule(this@BabyApp) }
                runCatching { ApptReminder.reschedule(this@BabyApp) }
                // Partage avec l'autre parent : synchro en arrière-plan quelques secondes plus tard.
                if (sync.config.state.value.enabled) {
                    runCatching { SyncScheduler.requestSoon(this@BabyApp) }
                    runCatching { SyncScheduler.enablePeriodic(this@BabyApp) }
                }
            }
        }
        Shortcuts.installOnce(this)
        refresh()
    }

    fun refresh() {
        refreshRequests.trySend(Unit)
    }
}
