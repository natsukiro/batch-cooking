package app.petitrythme.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.petitrythme.BabyApp
import app.petitrythme.MainActivity
import app.petitrythme.data.DiaperKind
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.Side
import app.petitrythme.data.Timers
import app.petitrythme.quick.QuickEntryActivity
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.describe
import app.petitrythme.ui.nextSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Ce que le widget affiche. */
data class WidgetData(
    val name: String,
    val lastFeed: Event?,
    val lastDiaper: Event?,
    val lastNursing: Event?,
    val timers: Timers,
    val lastAction: LastAction?,
)

fun widgetData(app: BabyApp): Flow<WidgetData> {
    val dao = app.db.events()
    return combine(
        dao.observeLatest(EventType.FEEDINGS),
        dao.observeLatest(listOf(EventType.DIAPER)),
        dao.observeLatest(listOf(EventType.NURSING)),
        app.timers.state,
        combine(app.profile.state, app.widgetStore.state) { p, a -> p.name.trim() to a },
    ) { feed, diaper, nursing, timers, (name, action) ->
        WidgetData(name, feed, diaper, nursing, timers, action)
    }
}

/**
 * Widget d'écran d'accueil : noter une couche, lancer une tétée ou un dodo
 * sans ouvrir l'appli. Le biberon ouvre une petite fenêtre pour la quantité.
 */
class BabyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as BabyApp
        val flow = widgetData(app)
        val initial = flow.first()
        provideContent {
            val data by flow.collectAsState(initial)
            WidgetContent(data, System.currentTimeMillis())
        }
    }
}

class BabyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BabyWidget()
}

// --- Apparence ----------------------------------------------------------------

private object WC {
    val bg = ColorProvider(day = Color(0xFFFFF8F4), night = Color(0xFF1D191E))
    val text = ColorProvider(day = Color(0xFF221A1D), night = Color(0xFFEBDFE3))
    val muted = ColorProvider(day = Color(0xFF6B5B61), night = Color(0xFFC9B9BF))
    val accent = ColorProvider(day = Color(0xFFB4507E), night = Color(0xFFF2A7C6))
    val diaper = ColorProvider(day = Color(0xFFF5EAD2), night = Color(0xFF3A3122))
    val feed = ColorProvider(day = Color(0xFFF9E0EA), night = Color(0xFF3D2531))
    val feedStrong = ColorProvider(day = Color(0xFFF2BFD2), night = Color(0xFF63304A))
    val sleep = ColorProvider(day = Color(0xFFE6E6F8), night = Color(0xFF2B2B46))
    val active = ColorProvider(day = Color(0xFFD2557F), night = Color(0xFFD96A93))
    val onActive = ColorProvider(day = Color.White, night = Color(0xFF1D191E))
}

/** Une action annulable reste proposée quelques minutes. */
private const val UNDO_WINDOW = 10 * 60_000L

@Composable
fun WidgetContent(d: WidgetData, now: Long) {
    val compact = LocalSize.current.height < 128.dp
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(WC.bg)
            .cornerRadius(24.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        StatusLine(d, now)
        Spacer(GlanceModifier.height(6.dp))
        val n = d.timers.nursing
        val sleeping = d.timers.sleepStart != null
        if (compact) {
            Row(GlanceModifier.fillMaxWidth()) {
                DiaperButton(DiaperKind.PEE)
                Gap()
                DiaperButton(DiaperKind.POO)
                Gap()
                BottleButton()
                Gap()
                SleepButton(sleeping)
            }
        } else {
            Row(GlanceModifier.fillMaxWidth()) {
                DiaperButton(DiaperKind.PEE)
                Gap()
                DiaperButton(DiaperKind.POO)
                Gap()
                DiaperButton(DiaperKind.BOTH)
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                val suggested = nextSide(d.lastNursing)
                Side.entries.forEach { side ->
                    val active = n?.side == side
                    Btn(
                        emoji = if (active) "❚❚" else "🤱",
                        label = side.label,
                        bg = when {
                            active -> WC.active
                            n == null && side == suggested -> WC.feedStrong
                            else -> WC.feed
                        },
                        fg = if (active) WC.onActive else WC.text,
                        action = actionRunCallback<NursingSideAction>(actionParametersOf(SideKey to side.code)),
                    )
                    Gap()
                }
                if (n != null) {
                    Btn("✓", "Fin tétée", WC.active, WC.onActive, actionRunCallback<NursingFinishAction>())
                    Gap()
                    BottleButton()
                } else {
                    BottleButton()
                    Gap()
                    SleepButton(sleeping)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(d: WidgetData, now: Long) {
    val a = d.lastAction?.takeIf { now - it.at < UNDO_WINDOW }
    val style = TextStyle(color = WC.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val n = d.timers.nursing
        val s = d.timers.sleepStart
        val text = when {
            a != null -> "✓ Noté : ${a.label} à ${Fmt.time(a.at)}"
            n != null -> "🤱 Tétée depuis ${Fmt.time(n.startedAt)} · " +
                (n.side?.let { "sein ${it.label.lowercase()}" } ?: "en pause")
            s != null -> "😴 Dort depuis ${Fmt.time(s)}"
            else -> buildString {
                if (d.name.isNotEmpty()) append(d.name).append("  ")
                append("🍽 ").append(d.lastFeed?.let { Fmt.whenShort(it.time, now) } ?: "—")
                append("   🧷 ").append(d.lastDiaper?.let { Fmt.whenShort(it.time, now) } ?: "—")
            }
        }
        Text(
            text,
            style = style,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
        )
        if (a != null) {
            Text(
                "Annuler",
                style = TextStyle(color = WC.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(start = 8.dp).clickable(actionRunCallback<UndoAction>()),
            )
        }
    }
}

@Composable
private fun RowScope.Gap() = Spacer(GlanceModifier.width(6.dp))

@Composable
private fun RowScope.Btn(emoji: String, label: String, bg: ColorProvider, fg: ColorProvider, action: Action) {
    Column(
        GlanceModifier
            .defaultWeight()
            .height(48.dp)
            .background(bg)
            .cornerRadius(14.dp)
            .clickable(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La couleur ne s'applique qu'aux symboles texte (❚❚, ✓), pas aux emojis.
        Text(emoji, style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        Text(label, style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun RowScope.DiaperButton(kind: DiaperKind) = Btn(
    emoji = kind.emoji,
    label = kind.label,
    bg = WC.diaper,
    fg = WC.text,
    action = actionRunCallback<DiaperAction>(actionParametersOf(KindKey to kind.code)),
)

@Composable
private fun RowScope.BottleButton() {
    val context = androidx.glance.LocalContext.current
    Btn(
        "🍼", "Biberon", WC.feed, WC.text,
        actionStartActivity(QuickEntryActivity.intent(context, EventType.BOTTLE)),
    )
}

@Composable
private fun RowScope.SleepButton(sleeping: Boolean) = Btn(
    emoji = if (sleeping) "☀️" else "😴",
    label = if (sleeping) "Réveil" else "Dodo",
    bg = if (sleeping) WC.active else WC.sleep,
    fg = if (sleeping) WC.onActive else WC.text,
    action = actionRunCallback<SleepToggleAction>(),
)

// --- Actions ------------------------------------------------------------------

private val KindKey = ActionParameters.Key<String>("kind")
private val SideKey = ActionParameters.Key<String>("side")

private fun Context.app() = applicationContext as BabyApp

/** Enregistre un évènement depuis le widget et le propose à l'annulation. */
private suspend fun BabyApp.logFromWidget(e: Event) {
    val id = db.events().insert(e)
    widgetStore.set(LastAction(id, e.type, describe(e).first, System.currentTimeMillis()))
}

class DiaperAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val kind = DiaperKind.of(parameters[KindKey]) ?: return
        context.app().logFromWidget(Event(type = EventType.DIAPER, time = System.currentTimeMillis(), sub = kind.code))
        BabyWidget().update(context, glanceId)
    }
}

class NursingSideAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val side = Side.of(parameters[SideKey]) ?: return
        context.app().timers.tapSide(side)
        BabyWidget().update(context, glanceId)
    }
}

class NursingFinishAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.app()
        app.timers.finishNursing()?.let { app.logFromWidget(it) }
        BabyWidget().update(context, glanceId)
    }
}

class SleepToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.app()
        if (app.timers.state.value.sleepStart != null) {
            app.timers.finishSleep()?.let { app.logFromWidget(it) }
        } else {
            app.timers.startSleep()
        }
        BabyWidget().update(context, glanceId)
    }
}

class UndoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.app()
        val a = app.widgetStore.state.value
        if (a != null && System.currentTimeMillis() - a.at < UNDO_WINDOW) {
            app.db.events().delete(Event(id = a.id, type = a.type, time = 0))
        }
        app.widgetStore.set(null)
        BabyWidget().update(context, glanceId)
    }
}
