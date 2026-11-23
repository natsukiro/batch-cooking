package app.petitrythme.quick

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.lifecycleScope
import app.petitrythme.BabyApp
import app.petitrythme.MainActivity
import app.petitrythme.data.DiaperKind
import app.petitrythme.data.Drafts
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.Side
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.describe
import app.petitrythme.ui.entry.EntrySheet
import app.petitrythme.ui.nextSide
import app.petitrythme.ui.theme.PetitRythmeTheme
import kotlinx.coroutines.launch

/**
 * Fenêtre transparente utilisée par le widget et les raccourcis de l'icône :
 * - avec [EXTRA_TYPE], affiche la fiche de saisie (ex. biberon) par-dessus l'écran d'accueil ;
 * - avec [EXTRA_QUICK], enregistre directement (couche) ou lance la tétée, sans interface.
 */
class QuickEntryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_QUICK = "quick"
        const val QUICK_NURSE = "NURSE"

        fun intent(context: Context, type: EventType) = Intent(context, QuickEntryActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_TYPE, type.name)

        fun quick(context: Context, what: String) = Intent(context, QuickEntryActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_QUICK, what)
    }

    private val app get() = application as BabyApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val quick = intent.getStringExtra(EXTRA_QUICK)
        if (quick != null) {
            handleQuick(quick)
            return
        }
        val type = EventType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) } ?: EventType.BOTTLE
        enableEdgeToEdge()
        setContent {
            val profile by app.profile.state.collectAsState()
            val draft by produceState<Event?>(null) {
                value = Drafts.forType(type, lastBottle = app.db.events().latest(listOf(EventType.BOTTLE)))
            }
            PetitRythmeTheme(profile.theme) {
                draft?.let { d ->
                    EntrySheet(
                        initial = d,
                        onDismiss = { finish() },
                        onSave = { e ->
                            // L'écriture continue même si la fenêtre se ferme tout de suite.
                            app.appScope.launch { app.db.events().insert(e) }
                            toast("${describe(e).first} enregistré")
                        },
                        onDelete = {},
                    )
                }
            }
        }
    }

    private fun handleQuick(what: String) {
        val kind = DiaperKind.of(what)
        lifecycleScope.launch {
            when {
                kind != null -> {
                    val e = Event(type = EventType.DIAPER, time = System.currentTimeMillis(), sub = kind.code)
                    app.db.events().insert(e)
                    toast("${describe(e).first} noté à ${Fmt.time(e.time)}")
                }
                what == QUICK_NURSE -> {
                    if (app.timers.state.value.nursing == null) {
                        val side = nextSide(app.db.events().latest(listOf(EventType.NURSING))) ?: Side.L
                        app.timers.tapSide(side)
                    }
                    startActivity(
                        Intent(this@QuickEntryActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }
            }
            finish()
        }
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
}
