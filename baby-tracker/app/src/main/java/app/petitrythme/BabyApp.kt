package app.petitrythme

import android.app.Application
import app.petitrythme.data.AppDatabase
import app.petitrythme.data.ProfileStore
import app.petitrythme.data.TimerStore
import app.petitrythme.notify.TimerNotifier

class BabyApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var profile: ProfileStore
        private set
    lateinit var timers: TimerStore
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        profile = ProfileStore(this)
        timers = TimerStore(this) { TimerNotifier.sync(this, it) }
    }
}
