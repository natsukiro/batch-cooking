package app.petitrythme.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.petitrythme.data.Appointment
import app.petitrythme.data.Category
import app.petitrythme.data.DiaperKind
import app.petitrythme.data.Drafts
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.NursingTimer
import app.petitrythme.data.Side
import app.petitrythme.data.durationMs
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.MainViewModel
import app.petitrythme.ui.appointments.NextApptBanner
import app.petitrythme.ui.appointments.nextSoon
import app.petitrythme.ui.components.ConfirmDialog
import app.petitrythme.ui.components.Pill
import app.petitrythme.ui.components.TimePickDialog
import app.petitrythme.ui.components.rememberNow
import app.petitrythme.ui.describe
import app.petitrythme.ui.nextSide
import app.petitrythme.ui.summarize
import app.petitrythme.ui.theme.ChronoStyle
import app.petitrythme.ui.theme.color
import app.petitrythme.ui.theme.container
import kotlinx.coroutines.launch

/**
 * Accueil : tout ce qu'on cherche à 3 h du matin, sans chercher.
 * - les chronos en cours en haut,
 * - « depuis combien de temps » pour repas / couche / sommeil,
 * - les gestes les plus fréquents en un seul appui.
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenEntry: (Event) -> Unit,
    onFinishPump: (Event) -> Unit,
    onQuickSave: (Event, String) -> Unit,
    onSaved: (Event, String) -> Unit,
    onTimerStarted: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppointment: (Appointment) -> Unit,
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val timers by vm.timers.collectAsStateWithLifecycle()
    val lastFeed by vm.lastFeed.collectAsStateWithLifecycle()
    val lastNursing by vm.lastNursing.collectAsStateWithLifecycle()
    val lastDiaper by vm.lastDiaper.collectAsStateWithLifecycle()
    val lastPoo by vm.lastPoo.collectAsStateWithLifecycle()
    val lastBottle by vm.lastBottle.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val lastSleep by vm.lastSleep.collectAsStateWithLifecycle()
    val now by rememberNow()
    val today = Fmt.date(now)
    val dayStart = Fmt.startOfDay(today)
    val dayEnd = Fmt.startOfDay(today.plusDays(1))
    val todayEvents by remember(dayStart) { vm.eventsBetween(dayStart - 86_400_000L, dayEnd) }
        .collectAsState(initial = emptyList())
    val summary = remember(todayEvents, dayStart) { summarize(todayEvents, dayStart, dayEnd) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // En-tête
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.trim().ifBlank { "Mon bébé" },
                    style = MaterialTheme.typography.headlineMedium,
                )
                val b = profile.birthDate
                if (b != null) {
                    Text(Fmt.age(b, today), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    TextButton(onClick = onOpenSettings, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text("Ajouter le prénom et la date de naissance")
                    }
                }
            }
            Text(Fmt.time(now), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Rendez-vous proche (moins de 3 jours) : un simple rappel, le détail est dans l'onglet RDV.
        nextSoon(appointments, now)?.let { a -> NextApptBanner(a, now) { onOpenAppointment(a) } }

        // Chronos en cours
        timers.nursing?.let { n ->
            NursingTimerCard(
                n, now,
                onSide = { vm.tapSide(it); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onFinish = {
                    scope.launch {
                        vm.finishNursing()?.let { e -> onSaved(e, "Tétée enregistrée · ${Fmt.duration(e.durationMs())}") }
                    }
                },
                onCancel = vm::cancelNursing,
                onEditStart = vm::setNursingStart,
            )
        }
        timers.sleepStart?.let { s ->
            SimpleTimerCard(
                type = EventType.SLEEP,
                title = "Dort depuis ${Fmt.time(s)}",
                elapsed = now - s,
                start = s,
                onEditStart = vm::setSleepStart,
                finishLabel = "Réveillé·e",
                onFinish = {
                    scope.launch {
                        vm.finishSleep()?.let { e -> onSaved(e, "Sommeil enregistré · ${Fmt.duration(e.durationMs())}") }
                    }
                },
                onCancel = vm::cancelSleep,
            )
        }
        timers.pumpStart?.let { p ->
            SimpleTimerCard(
                type = EventType.PUMP,
                title = "Tire-lait depuis ${Fmt.time(p)}",
                elapsed = now - p,
                start = p,
                onEditStart = vm::setPumpStart,
                finishLabel = "Terminer",
                onFinish = {
                    onFinishPump(Event(type = EventType.PUMP, time = p, endTime = System.currentTimeMillis(), sub = "BOTH"))
                },
                onCancel = vm::cancelPump,
            )
        }

        // Depuis…
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val feed = lastFeed
            val nursing = timers.nursing
            SinceCard(
                Modifier.weight(1f), Category.FEED, "Repas",
                big = when {
                    nursing != null -> Fmt.durationCompact(now - nursing.startedAt)
                    feed != null -> Fmt.durationCompact(now - feed.time)
                    else -> "—"
                },
                small = when {
                    nursing != null -> "Tétée en cours"
                    feed != null -> describe(feed).first.substringBefore(" · ") + " à " + Fmt.time(feed.time)
                    else -> "Aucun"
                },
                onClick = { if (nursing == null) feed?.let(onOpenEntry) },
            )
            val d = lastDiaper
            val poo = lastPoo
            SinceCard(
                Modifier.weight(1f), Category.DIAPER, "Couche",
                big = d?.let { Fmt.durationCompact(now - it.time) } ?: "—",
                small = when {
                    d == null -> "Aucune"
                    // Le dernier caca est souvent la question du pédiatre : on l'affiche s'il est plus ancien.
                    poo != null && poo.id != d.id -> "${describe(d).first} à ${Fmt.time(d.time)}\n💩 ${Fmt.whenShort(poo.time, now)}"
                    poo == null && DiaperKind.of(d.sub)?.hasPoo != true -> "${describe(d).first} à ${Fmt.time(d.time)}\n💩 aucun noté"
                    else -> "${describe(d).first} à ${Fmt.time(d.time)}"
                },
                onClick = { d?.let(onOpenEntry) },
            )
            val sleeping = timers.sleepStart
            val sl = lastSleep
            SinceCard(
                Modifier.weight(1f), Category.SLEEP,
                if (sleeping != null) "Dort" else "Éveillé·e",
                big = when {
                    sleeping != null -> Fmt.durationCompact(now - sleeping)
                    sl?.endTime != null -> Fmt.durationCompact(now - sl.endTime)
                    else -> "—"
                },
                small = when {
                    sleeping != null -> "depuis ${Fmt.time(sleeping)}"
                    sl?.endTime != null -> "réveil à ${Fmt.time(sl.endTime)}"
                    else -> "Aucun dodo"
                },
                onClick = { sl?.let(onOpenEntry) },
            )
        }

        // Aujourd'hui
        TodayStrip(summary)

        // Tétée : un appui sur le sein lance le chrono
        val suggested = nextSide(lastNursing)
        if (timers.nursing == null) {
            ActionCard(EventType.NURSING, onEdit = {
                onOpenEntry(Event(type = EventType.NURSING, time = System.currentTimeMillis() - 15 * 60_000L, leftSec = 0, rightSec = 0))
            }, subtitle = suggested?.let { "Prochain sein conseillé : ${it.label.lowercase()}" }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Side.entries.forEach { side ->
                        BigButton(
                            text = side.label,
                            badge = if (side == suggested) "suivant" else null,
                            color = Category.FEED.color(),
                            filled = side == suggested,
                            modifier = Modifier.weight(1f),
                        ) {
                            vm.tapSide(side)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTimerStarted()
                        }
                    }
                }
            }
        }

        // Couche express : un seul appui
        ActionCard(EventType.DIAPER, onEdit = {
            onOpenEntry(Event(type = EventType.DIAPER, time = System.currentTimeMillis(), sub = DiaperKind.PEE.code))
        }, subtitle = "Un appui enregistre · crayon pour les détails") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DiaperKind.entries.forEach { k ->
                    BigButton(
                        text = k.label,
                        top = k.emoji,
                        color = Category.DIAPER.color(),
                        modifier = Modifier.weight(1f),
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val e = Event(type = EventType.DIAPER, time = System.currentTimeMillis(), sub = k.code)
                        onQuickSave(e, "${describe(e).first} noté à ${Fmt.time(e.time)}")
                    }
                }
            }
        }

        // Sommeil + biberon
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (timers.sleepStart == null) {
                Tile(
                    EventType.SLEEP, "Dodo", "Lancer le chrono", Modifier.weight(1f),
                    onEdit = {
                        val n = System.currentTimeMillis()
                        onOpenEntry(Event(type = EventType.SLEEP, time = n - 60 * 60_000L, endTime = n))
                    },
                ) {
                    vm.startSleep()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTimerStarted()
                }
            }
            val lb = lastBottle
            Tile(
                EventType.BOTTLE, "Biberon",
                lb?.amountMl?.let { "${Fmt.num(it, 0)} ml la dernière fois" } ?: "Quantité et type de lait",
                Modifier.weight(1f),
            ) {
                onOpenEntry(Drafts.bottle(lb))
            }
        }

        // Le reste
        Text(
            "Autres",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val others = listOf(
            EventType.SOLID, EventType.PUMP, EventType.BATH, EventType.ACTIVITY,
            EventType.TEMPERATURE, EventType.MEDICINE, EventType.GROWTH, EventType.NOTE,
        )
        others.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    SmallTile(t, Modifier.weight(1f)) {
                        if (t == EventType.PUMP && timers.pumpStart == null) {
                            vm.startPump(); onTimerStarted()
                        } else {
                            onOpenEntry(Drafts.forType(t, lastBottle = lastBottle))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SinceCard(
    modifier: Modifier,
    category: Category,
    label: String,
    big: String,
    small: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = category.container()),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = category.color())
            Text(
                big,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Text(
                small,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayStrip(s: app.petitrythme.ui.DaySummary) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Aujourd'hui", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("🍽️", "${s.feeds}", "repas")
                if (s.nursingMs > 0) Stat("🤱", Fmt.durationCompact(s.nursingMs), "au sein")
                if (s.bottleMl > 0) Stat("🍼", "${s.bottleMl.toInt()}", "ml")
                Stat("💧", "${s.pee}", "pipi")
                Stat("💩", "${s.poo}", "caca")
                Stat("😴", if (s.sleepMs > 0) Fmt.durationCompact(s.sleepMs) else "0", "dodo")
            }
        }
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionCard(
    type: EventType,
    onEdit: () -> Unit,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(type.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(type.label, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Saisie détaillée", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.padding(end = 8.dp)) { content() }
        }
    }
}

@Composable
private fun BigButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    badge: String? = null,
    top: String? = null,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (top != null) Text(top, fontSize = 20.sp)
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
            if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = MaterialTheme.colorScheme.surface),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.6f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) { content() }
    }
}

@Composable
private fun Tile(
    type: EventType,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        colors = CardDefaults.cardColors(containerColor = type.category.container()),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box {
            Column(Modifier.padding(16.dp)) {
                Text(type.emoji, fontSize = 26.sp)
                Spacer(Modifier.height(6.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Saisie détaillée", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SmallTile(type: EventType, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 78.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(type.emoji, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                when (type) {
                    EventType.SOLID -> "Solide"
                    EventType.TEMPERATURE -> "Temp."
                    else -> type.label
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NursingTimerCard(
    n: NursingTimer,
    now: Long,
    onSide: (Side) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onEditStart: (Long) -> Unit,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    val accent = Category.FEED.color()
    Card(
        colors = CardDefaults.cardColors(containerColor = Category.FEED.container()),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤱 Tétée", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Pill(if (n.paused) "En pause" else "En cours · ${n.side?.label?.lowercase()}", accent)
            }
            Text(Fmt.chrono(n.totalAt(now)), style = ChronoStyle, modifier = Modifier.padding(vertical = 4.dp))
            EditableStart("Commencée à", n.startedAt, onEditStart)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Side.entries.forEach { side ->
                    val active = n.side == side
                    val ms = if (side == Side.L) n.leftAt(now) else n.rightAt(now)
                    val btnContent: @Composable () -> Unit = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(" ${side.label}", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(Fmt.chrono(ms), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (active) {
                        Button(
                            onClick = { onSide(side) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 72.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.surface),
                        ) { btnContent() }
                    } else {
                        OutlinedButton(
                            onClick = { onSide(side) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 72.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(alpha = 0.6f)),
                        ) { btnContent() }
                    }
                }
            }
            Text(
                "Appuyez sur l'autre sein pour changer, sur le sein actif pour mettre en pause.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { confirmCancel = true }) { Text("Annuler") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onFinish, modifier = Modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("Terminer et enregistrer")
                }
            }
        }
    }
    if (confirmCancel) {
        ConfirmDialog(
            "Annuler la tétée ?", "Le chronomètre sera arrêté sans rien enregistrer.", "Oui, annuler",
            onConfirm = onCancel, onDismiss = { confirmCancel = false },
        )
    }
}

@Composable
private fun SimpleTimerCard(
    type: EventType,
    title: String,
    elapsed: Long,
    start: Long,
    onEditStart: (Long) -> Unit,
    finishLabel: String,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = type.category.container()),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${type.emoji} ${type.label}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Pill(title, type.category.color())
            }
            EditableStart("Début", start, onEditStart)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.chrono(elapsed), style = ChronoStyle, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmCancel = true }) { Text("Annuler") }
                Button(
                    onClick = onFinish,
                    modifier = Modifier.heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = type.category.color(), contentColor = MaterialTheme.colorScheme.surface),
                ) { Text(finishLabel) }
            }
        }
    }
    if (confirmCancel) {
        ConfirmDialog(
            "Annuler le chronomètre ?", "Rien ne sera enregistré.", "Oui, annuler",
            onConfirm = onCancel, onDismiss = { confirmCancel = false },
        )
    }
}

/** « Commencée à 11:12 ✎ » : toucher pour corriger l'heure si le chrono a été lancé en retard. */
@Composable
private fun EditableStart(label: String, start: Long, onChange: (Long) -> Unit) {
    var pick by remember { mutableStateOf(false) }
    TextButton(onClick = { pick = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
        Text(
            "$label ${Fmt.time(start)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.Edit, contentDescription = "Corriger l'heure de début", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (pick) {
        val zdt = java.time.Instant.ofEpochMilli(start).atZone(Fmt.zone)
        TimePickDialog(zdt.toLocalTime(), onDismiss = { pick = false }) { t ->
            val now = System.currentTimeMillis()
            var candidate = Fmt.today().atTime(t).atZone(Fmt.zone).toInstant().toEpochMilli()
            // Une heure « dans le futur » veut dire hier (ex. 23:50 choisi à 00:20).
            if (candidate > now) candidate -= 86_400_000L
            onChange(candidate)
        }
    }
}
