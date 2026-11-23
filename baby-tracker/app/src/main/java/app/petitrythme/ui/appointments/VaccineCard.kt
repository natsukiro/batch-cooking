package app.petitrythme.ui.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.petitrythme.data.Appointment
import app.petitrythme.data.VaccineSchedule
import app.petitrythme.ui.Fmt
import java.time.LocalDate

/**
 * Suggestions issues du calendrier vaccinal, calculées depuis la date de naissance.
 * Une étape disparaît des suggestions dès qu'un rendez-vous y est lié (« Planifier »).
 */
@Composable
fun VaccineCard(
    birth: LocalDate?,
    appointments: List<Appointment>,
    today: LocalDate,
    onPlan: (VaccineSchedule.Step) -> Unit,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val plannedBy = appointments.filter { it.vaccineKey != null }.associateBy { it.vaccineKey!! }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💉", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("Vaccins conseillés", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            if (birth == null) {
                Text(
                    "Renseignez la date de naissance (Réglages) pour voir les prochains vaccins du calendrier.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                return@Column
            }
            val next = VaccineSchedule.suggestions(birth, today, plannedBy.keys)
            if (next.isEmpty() && !showAll) {
                Text(
                    "Rien à planifier pour l'instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (!showAll) {
                next.forEach { step ->
                    Spacer(Modifier.height(10.dp))
                    StepRow(step, birth, today, planned = null, onPlan = { onPlan(step) })
                }
            } else {
                VaccineSchedule.steps.forEach { step ->
                    Spacer(Modifier.height(10.dp))
                    StepRow(step, birth, today, planned = plannedBy[step.key], onPlan = { onPlan(step) })
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "À confirmer avec le médecin et le carnet de santé · ${VaccineSchedule.SOURCE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 6.dp),
                )
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Réduire" else "Tout le calendrier")
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    step: VaccineSchedule.Step,
    birth: LocalDate,
    today: LocalDate,
    planned: Appointment?,
    onPlan: () -> Unit,
) {
    val due = step.due(birth)
    val past = due.isBefore(today.minusDays(21))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${step.age} · ${Fmt.weekdayShort(due)} ${due.dayOfMonth} ${Fmt.monthShort(due)}" +
                    if (due.year != today.year) " ${due.year}" else "",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                step.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            planned != null -> Text(
                "✓ ${Fmt.dayShort(Fmt.date(planned.time))}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            past -> Text("passé", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> FilledTonalButton(onClick = onPlan) { Text("Planifier") }
        }
    }
}

/** Brouillon de rendez-vous pour une étape : à la date prévue (ou la semaine prochaine si elle est passée), 10 h. */
fun vaccineDraft(step: VaccineSchedule.Step, birth: LocalDate, today: LocalDate): Appointment {
    val due = step.due(birth).let { if (it.isBefore(today)) today.plusDays(7) else it }
    return Appointment(
        time = due.atTime(10, 0).atZone(Fmt.zone).toInstant().toEpochMilli(),
        kind = app.petitrythme.data.ApptKind.VACCIN.code,
        vaccineKey = step.key,
    )
}
