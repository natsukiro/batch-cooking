package app.petitrythme.ui.appointments

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.petitrythme.data.ApptKind
import app.petitrythme.data.Appointment
import app.petitrythme.data.apptKind
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.components.ConfirmDialog
import app.petitrythme.ui.components.DateTimeField
import app.petitrythme.ui.components.SectionTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApptSheet(
    initial: Appointment,
    babyName: String,
    onDismiss: () -> Unit,
    onSave: (Appointment) -> Unit,
    onDelete: (Appointment) -> Unit,
    onAddGrowth: (Long) -> Unit,
    onMessage: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var a by remember(initial) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = initial.id == 0L
    val isPast = a.time < System.currentTimeMillis()

    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onDismiss() }
    }

    fun clean(x: Appointment) = x.copy(
        who = x.who?.trim()?.ifEmpty { null },
        place = x.place?.trim()?.ifEmpty { null },
        questions = x.questions?.trim()?.ifEmpty { null },
        notes = x.notes?.trim()?.ifEmpty { null },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.apptKind.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isNew) "Nouveau rendez-vous" else "Rendez-vous",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (!isNew) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            SectionTitle("Pour quoi ?", Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApptKind.entries.forEach { k ->
                    FilterChip(
                        selected = a.apptKind == k,
                        onClick = { a = a.copy(kind = k.code) },
                        label = { Text("${k.emoji} ${k.label}") },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            DateTimeField("Quand ?", a.time, { a = a.copy(time = it) }, Modifier.fillMaxWidth(), allowFuture = true)

            OutlinedTextField(
                value = a.who ?: "",
                onValueChange = { a = a.copy(who = it) },
                label = { Text("Avec qui ?") },
                placeholder = { Text("Ex. : Dr Martin") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            OutlinedTextField(
                value = a.place ?: "",
                onValueChange = { a = a.copy(place = it) },
                label = { Text("Où ?") },
                placeholder = { Text("Cabinet, adresse, PMI…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )

            SectionTitle("Questions à poser", Modifier.padding(top = 10.dp))
            OutlinedTextField(
                value = a.questions ?: "",
                onValueChange = { a = a.copy(questions = it) },
                placeholder = { Text("Une question par ligne, à compléter au fil des jours") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Rappel", Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = a.remindEve,
                    onClick = { a = a.copy(remindEve = !a.remindEve) },
                    label = { Text("La veille à 19 h") },
                )
                FilterChip(
                    selected = a.remindHour,
                    onClick = { a = a.copy(remindHour = !a.remindHour) },
                    label = { Text("1 h avant") },
                )
            }

            if (!isNew || isPast) {
                SectionTitle("Compte rendu", Modifier.padding(top = 10.dp))
                OutlinedTextField(
                    value = a.notes ?: "",
                    onValueChange = { a = a.copy(notes = it) },
                    placeholder = { Text("Conseils, traitement, prochain rendez-vous…") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isPast) {
                    OutlinedButton(
                        onClick = { val t = a.time; val out = clean(a); close { onSave(out); onAddGrowth(t) } },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("📏 Noter poids et taille dans le suivi") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { if (!addToCalendar(context, clean(a), babyName)) onMessage("Aucune application d'agenda trouvée") },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(58.dp),
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agenda")
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { val out = clean(a); close { onSave(out) } },
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isNew) "Enregistrer" else "Mettre à jour", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                "« Agenda » l'ajoute aussi à l'agenda du téléphone (pratique pour le partager avec l'autre parent).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Supprimer ce rendez-vous ?", "Ses questions et son compte rendu seront supprimés.", "Supprimer",
            onConfirm = { close { onDelete(initial) } },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Ouvre l'agenda du téléphone avec le rendez-vous pré-rempli (aucune autorisation nécessaire). */
private fun addToCalendar(context: Context, a: Appointment, babyName: String): Boolean {
    val k = a.apptKind
    val title = k.label + (a.who?.let { " – $it" } ?: "") + (babyName.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, a.time)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, a.time + 30 * 60_000L)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, a.place ?: "")
        .putExtra(CalendarContract.Events.DESCRIPTION, a.questions?.let { "Questions à poser :\n$it" } ?: "")
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Nouveau rendez-vous par défaut : la semaine prochaine, même jour, à 10 h. */
fun newAppointment(): Appointment {
    val d = Fmt.today().plusDays(7)
    return Appointment(time = d.atTime(10, 0).atZone(Fmt.zone).toInstant().toEpochMilli())
}
