package app.petitrythme.ui.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.petitrythme.data.Appointment
import app.petitrythme.data.VaccineSchedule
import app.petitrythme.data.apptKind
import app.petitrythme.data.questionList
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.MainViewModel
import app.petitrythme.ui.components.Pill
import app.petitrythme.ui.components.SectionTitle
import app.petitrythme.ui.components.rememberNow

/** Un rendez-vous reste « à venir » jusqu'à 2 h après son heure (le temps de la consultation). */
private const val GRACE = 2 * 3_600_000L

fun isUpcoming(a: Appointment, now: Long) = a.time >= now - GRACE

/**
 * Section à part du suivi quotidien : rendez-vous à venir (avec les questions
 * à préparer) et rendez-vous passés (avec leur compte rendu).
 */
@Composable
fun AppointmentsScreen(vm: MainViewModel, onOpen: (Appointment) -> Unit, onNew: () -> Unit) {
    val all by vm.appointments.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val now by rememberNow(60_000)
    val upcoming = all.filter { isUpcoming(it, now) }.sortedBy { it.time }
    val past = all.filterNot { isUpcoming(it, now) }.sortedByDescending { it.time }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("Rendez-vous", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Pédiatre, vaccins, PMI… avec les questions à ne pas oublier.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "vaccines") {
                val today = Fmt.date(now)
                VaccineCard(profile.birthDate, all, today) { step ->
                    profile.birthDate?.let { onOpen(vaccineDraft(step, it, today)) }
                }
            }
            item(key = "upcomingTitle") { SectionTitle("À venir") }
            if (upcoming.isEmpty()) {
                item(key = "empty") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🗓️", fontSize = 34.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("Aucun rendez-vous prévu", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Ajoutez le prochain : vous pourrez y noter vos questions au fil des jours.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            items(upcoming, key = { "u${it.id}" }) { ApptCard(it, now, past = false) { onOpen(it) } }
            if (past.isNotEmpty()) {
                item(key = "pastTitle") { SectionTitle("Passés", Modifier.padding(top = 8.dp)) }
                items(past, key = { "p${it.id}" }) { ApptCard(it, now, past = true) { onOpen(it) } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Nouveau rendez-vous") },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

@Composable
fun ApptCard(a: Appointment, now: Long, past: Boolean, onClick: () -> Unit) {
    val k = a.apptKind
    val d = Fmt.date(a.time)
    val questions = a.questionList()
    val accent = MaterialTheme.colorScheme.secondary
    Surface(
        onClick = onClick,
        color = if (past) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Bloc date
            Surface(
                color = if (past) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    Modifier
                        .width(58.dp)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Fmt.weekdayShort(d), style = MaterialTheme.typography.labelSmall)
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleLarge)
                    Text(Fmt.monthShort(d), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${k.emoji} ${k.label}" + (a.who?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Fmt.time(a.time) + (a.place?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VaccineSchedule.step(a.vaccineKey)?.let { step ->
                    Text(
                        "💉 ${step.age} : ${step.summary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (past) {
                    a.notes?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "📝 $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                } else {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(Fmt.countdown(a.time, now) ?: Fmt.apptWhen(a.time, now).substringBefore(" à "), accent)
                        if (questions.isNotEmpty()) {
                            Pill("${questions.size} question${if (questions.size > 1) "s" else ""}", accent)
                        }
                    }
                }
            }
        }
    }
}

/** Bandeau discret sur l'accueil quand un rendez-vous approche (moins de 3 jours). */
@Composable
fun NextApptBanner(a: Appointment, now: Long, onClick: () -> Unit) {
    val k = a.apptKind
    val n = a.questionList().size
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(k.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${k.label} ${Fmt.apptWhen(a.time, now)}", style = MaterialTheme.typography.titleSmall)
                val sub = listOfNotNull(
                    a.who?.takeIf { it.isNotBlank() },
                    n.takeIf { it > 0 }?.let { "$it question${if (it > 1) "s" else ""} à poser" },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Prochain rendez-vous à afficher sur l'accueil, s'il a lieu dans les 3 jours. */
fun nextSoon(all: List<Appointment>, now: Long): Appointment? =
    all.filter { isUpcoming(it, now) && it.time - now < 3 * 86_400_000L }.minByOrNull { it.time }
