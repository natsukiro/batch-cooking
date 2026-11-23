package app.petitrythme.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.petitrythme.data.Amount
import app.petitrythme.data.DiaperKind
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.MilkKind
import app.petitrythme.data.POO_TEXTURES
import app.petitrythme.data.PooColor
import app.petitrythme.data.Side
import app.petitrythme.data.durationMs
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.components.ChoiceChips
import app.petitrythme.ui.components.ConfirmDialog
import app.petitrythme.ui.components.DateTimeField
import app.petitrythme.ui.components.Segmented
import app.petitrythme.ui.components.SectionTitle
import app.petitrythme.ui.components.Stepper
import app.petitrythme.ui.components.TypeBadge
import app.petitrythme.ui.theme.color
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val FOODS = listOf(
    "Carotte", "Courgette", "Patate douce", "Haricots verts", "Potiron", "Petits pois",
    "Pomme", "Poire", "Banane", "Pêche", "Compote", "Céréales", "Yaourt", "Viande", "Poisson", "Œuf",
)
private val MEDICINES = listOf("Vitamine D", "Paracétamol", "Sérum physiologique", "Vaccin", "Fer", "Probiotiques", "Crème")
private val ACTIVITIES = listOf("Sur le ventre", "Éveil", "Promenade", "Peau à peau", "Lecture", "Massage", "Musique")
private const val MIN = 60_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntrySheet(
    initial: Event,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit,
    onDelete: (Event) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var e by remember(initial) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = initial.id == 0L
    val accent = e.type.category.color()

    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onDismiss() }
    }

    val error: String? = when (e.type) {
        EventType.SLEEP -> if ((e.endTime ?: 0) <= e.time) "L'heure de réveil doit être après l'endormissement." else null
        EventType.NURSING -> if ((e.leftSec ?: 0) + (e.rightSec ?: 0) <= 0) "Indiquez la durée d'au moins un sein." else null
        EventType.GROWTH -> if (e.value == null && e.heightCm == null && e.headCm == null) "Indiquez au moins une mesure." else null
        EventType.NOTE -> if (e.note.isNullOrBlank()) "La note est vide." else null
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(e.type, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.type.label, style = MaterialTheme.typography.headlineSmall)
                    if (!isNew) {
                        Text("Modifier l'entrée", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!isNew) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            when (e.type) {
                EventType.NURSING -> NursingForm(e) { e = it }
                EventType.BOTTLE -> BottleForm(e) { e = it }
                EventType.SOLID -> SolidForm(e) { e = it }
                EventType.PUMP -> PumpForm(e) { e = it }
                EventType.DIAPER -> DiaperForm(e, isNew) { e = it }
                EventType.SLEEP -> SleepForm(e) { e = it }
                EventType.ACTIVITY -> ActivityForm(e) { e = it }
                EventType.TEMPERATURE -> TemperatureForm(e, isNew) { e = it }
                EventType.MEDICINE -> MedicineForm(e, isNew) { e = it }
                EventType.GROWTH -> GrowthForm(e, isNew) { e = it }
                EventType.BATH -> PointTime(e, isNew) { e = it }
                EventType.NOTE -> PointTime(e, isNew) { e = it }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = e.note ?: "",
                onValueChange = { e = e.copy(note = it.ifEmpty { null }) },
                label = { Text(if (e.type == EventType.NOTE) "Votre note" else "Note (facultatif)") },
                minLines = if (e.type == EventType.NOTE) 4 else 1,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { val out = normalize(e); close { onSave(out) } },
                enabled = error == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isNew) "Enregistrer" else "Mettre à jour", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Supprimer ?", "Cette entrée sera retirée du journal.", "Supprimer",
            onConfirm = { close { onDelete(initial) } },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun normalize(e: Event): Event = when (e.type) {
    EventType.NURSING -> {
        val l = e.leftSec ?: 0
        val r = e.rightSec ?: 0
        val last = Side.of(e.sub) ?: if (r > 0 && l == 0) Side.R else Side.L
        e.copy(endTime = e.time + (l + r) * 1000L, sub = last.code)
    }
    else -> e.copy(note = e.note?.trim()?.ifEmpty { null })
}

// --- Heure ------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PointTime(e: Event, isNew: Boolean, onChange: (Event) -> Unit) {
    DateTimeField("Heure", e.time, { onChange(e.copy(time = it)) }, Modifier.fillMaxWidth())
    if (isNew) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf(0 to "Maintenant", 5 to "−5 min", 15 to "−15 min", 30 to "−30 min", 60 to "−1 h").forEach { (m, label) ->
                SuggestionChip(onClick = { onChange(e.copy(time = System.currentTimeMillis() - m * MIN)) }, label = { Text(label) })
            }
        }
    }
}

/** Début + durée (en minutes) pour tire-lait et éveil. */
@Composable
private fun StartAndDuration(e: Event, step: Int, onChange: (Event) -> Unit) {
    val dur = ((e.endTime ?: e.time) - e.time).coerceAtLeast(0)
    val minutes = (dur / MIN).toInt()
    DateTimeField("Début", e.time, { onChange(e.copy(time = it, endTime = it + dur)) }, Modifier.fillMaxWidth())
    SectionTitle("Durée")
    Stepper(
        "$minutes", "min",
        onMinus = { onChange(e.copy(endTime = e.time + (minutes - step).coerceAtLeast(0) * MIN)) },
        onPlus = { onChange(e.copy(endTime = e.time + (minutes + step) * MIN)) },
        accent = e.type.category.color(),
        modifier = Modifier.fillMaxWidth(),
    )
}

// --- Formulaires ------------------------------------------------------------

@Composable
private fun NursingForm(e: Event, onChange: (Event) -> Unit) {
    DateTimeField("Début", e.time, { onChange(e.copy(time = it)) }, Modifier.fillMaxWidth())
    val accent = e.type.category.color()
    Side.entries.forEach { side ->
        val sec = (if (side == Side.L) e.leftSec else e.rightSec) ?: 0
        val min = (sec / 60.0).roundToInt()
        fun set(m: Int) {
            val v = (m.coerceAtLeast(0)) * 60
            onChange(if (side == Side.L) e.copy(leftSec = v) else e.copy(rightSec = v))
        }
        SectionTitle("Sein ${side.label.lowercase()}")
        Stepper("$min", "min", { set(min - 1) }, { set(min + 1) }, Modifier.fillMaxWidth(), accent, MaterialTheme.typography.headlineMedium)
    }
    SectionTitle("Terminé par le sein…")
    Segmented(Side.entries, Side.of(e.sub), { it.label }, { onChange(e.copy(sub = it.code)) }, Modifier.fillMaxWidth())
    Text(
        "Total : ${Fmt.duration(e.durationMs())}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottleForm(e: Event, onChange: (Event) -> Unit) {
    PointTime(e, e.id == 0L, onChange)
    SectionTitle("Lait")
    Segmented(MilkKind.entries, MilkKind.of(e.sub), { it.label }, { onChange(e.copy(sub = it.code)) }, Modifier.fillMaxWidth())
    SectionTitle("Quantité bue")
    val ml = (e.amountMl ?: 0.0).roundToInt()
    Stepper(
        "$ml", "ml",
        onMinus = { onChange(e.copy(amountMl = (ml - 10).coerceAtLeast(0).toDouble())) },
        onPlus = { onChange(e.copy(amountMl = (ml + 10).coerceAtMost(500).toDouble())) },
        accent = e.type.category.color(),
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        listOf(30, 60, 90, 120, 150, 180, 210, 240).forEach { v ->
            SuggestionChip(onClick = { onChange(e.copy(amountMl = v.toDouble())) }, label = { Text("$v") })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SolidForm(e: Event, onChange: (Event) -> Unit) {
    PointTime(e, e.id == 0L, onChange)
    SectionTitle("Aliment")
    OutlinedTextField(
        value = e.detail ?: "",
        onValueChange = { onChange(e.copy(detail = it.ifEmpty { null })) },
        placeholder = { Text("Ex. : purée de carotte") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        FOODS.forEach { f ->
            SuggestionChip(onClick = {
                val cur = e.detail?.trim().orEmpty()
                onChange(e.copy(detail = if (cur.isEmpty()) f else "$cur, ${f.lowercase()}"))
            }, label = { Text(f) })
        }
    }
    SectionTitle("Quantité")
    Segmented(Amount.entries, Amount.of(e.sub), { it.label }, { onChange(e.copy(sub = it.code)) }, Modifier.fillMaxWidth())
}

@Composable
private fun PumpForm(e: Event, onChange: (Event) -> Unit) {
    StartAndDuration(e, 1, onChange)
    SectionTitle("Côté")
    Segmented(listOf("L", "R", "BOTH"), e.sub, {
        when (it) { "L" -> "Gauche"; "R" -> "Droit"; else -> "Les deux" }
    }, { onChange(e.copy(sub = it)) }, Modifier.fillMaxWidth())
    SectionTitle("Quantité tirée")
    val ml = (e.amountMl ?: 0.0).roundToInt()
    Stepper(
        "$ml", "ml",
        onMinus = { onChange(e.copy(amountMl = (ml - 10).coerceAtLeast(0).toDouble())) },
        onPlus = { onChange(e.copy(amountMl = (ml + 10).toDouble())) },
        accent = e.type.category.color(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaperForm(e: Event, isNew: Boolean, onChange: (Event) -> Unit) {
    PointTime(e, isNew, onChange)
    SectionTitle("Contenu")
    val kind = DiaperKind.of(e.sub)
    Segmented(DiaperKind.entries, kind, { "${it.emoji} ${it.label}" }, { onChange(e.copy(sub = it.code)) }, Modifier.fillMaxWidth())
    if (kind?.hasPoo == true) {
        SectionTitle("Couleur des selles")
        val selected = PooColor.of(e.detail)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PooColor.entries.forEach { c ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onChange(e.copy(detail = if (selected == c) null else c.code)) }
                        .padding(4.dp)
                        .width(64.dp),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(c.argb))
                            .border(
                                if (selected == c) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected == c) Icon(Icons.Rounded.Check, null, tint = if (c == PooColor.PALE || c == PooColor.YELLOW) Color.Black else Color.White)
                    }
                    Text(c.label, style = MaterialTheme.typography.labelSmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        selected?.warning?.let { Warning(it) }
        SectionTitle("Texture")
        ChoiceChips(POO_TEXTURES, e.detail2, { it }, { onChange(e.copy(detail2 = it)) })
    }
}

@Composable
private fun SleepForm(e: Event, onChange: (Event) -> Unit) {
    DateTimeField("Endormissement", e.time, { onChange(e.copy(time = it)) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    DateTimeField("Réveil", e.endTime ?: e.time, { onChange(e.copy(endTime = it)) }, Modifier.fillMaxWidth())
    val d = (e.endTime ?: e.time) - e.time
    if (d > 0) {
        Text(
            "Durée : ${Fmt.duration(d)}",
            style = MaterialTheme.typography.titleMedium,
            color = e.type.category.color(),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ActivityForm(e: Event, onChange: (Event) -> Unit) {
    SectionTitle("Activité")
    ChoiceChips(ACTIVITIES, e.detail, { it }, { onChange(e.copy(detail = it)) })
    Spacer(Modifier.height(8.dp))
    StartAndDuration(e, 5, onChange)
}

@Composable
private fun TemperatureForm(e: Event, isNew: Boolean, onChange: (Event) -> Unit) {
    PointTime(e, isNew, onChange)
    SectionTitle("Température")
    val v = e.value ?: 37.0
    fun set(x: Double) = onChange(e.copy(value = (Math.round(x * 10) / 10.0).coerceIn(34.0, 42.5)))
    Stepper(
        Fmt.num(v, 1).let { if (it.contains(',')) it else "$it,0" }, "°C",
        onMinus = { set(v - 0.1) }, onPlus = { set(v + 0.1) },
        accent = if (v >= 38.0) MaterialTheme.colorScheme.error else e.type.category.color(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (v >= 38.0) {
        Warning("Fièvre (≥ 38 °C). Chez un bébé de moins de 3 mois, toute fièvre nécessite un avis médical rapide.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicineForm(e: Event, isNew: Boolean, onChange: (Event) -> Unit) {
    PointTime(e, isNew, onChange)
    SectionTitle("Quoi ?")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MEDICINES.forEach { m ->
            androidx.compose.material3.FilterChip(
                selected = e.detail == m,
                onClick = { onChange(e.copy(detail = m)) },
                label = { Text(m) },
            )
        }
    }
    OutlinedTextField(
        value = e.detail ?: "",
        onValueChange = { onChange(e.copy(detail = it.ifEmpty { null })) },
        label = { Text("Nom") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
    OutlinedTextField(
        value = e.detail2 ?: "",
        onValueChange = { onChange(e.copy(detail2 = it.ifEmpty { null })) },
        label = { Text("Dose (facultatif)") },
        placeholder = { Text("Ex. : 1 goutte, 2,5 ml") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
}

@Composable
private fun GrowthForm(e: Event, isNew: Boolean, onChange: (Event) -> Unit) {
    PointTime(e, isNew, onChange)
    Spacer(Modifier.height(8.dp))
    DecimalField("Poids", "kg", e.value) { onChange(e.copy(value = it)) }
    DecimalField("Taille", "cm", e.heightCm) { onChange(e.copy(heightCm = it)) }
    DecimalField("Périmètre crânien", "cm", e.headCm) { onChange(e.copy(headCm = it)) }
}

@Composable
private fun DecimalField(label: String, unit: String, value: Double?, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.let { Fmt.num(it, 3) } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val clean = t.filter { it.isDigit() || it == ',' || it == '.' }
            text = clean
            onChange(clean.replace(',', '.').toDoubleOrNull())
        },
        label = { Text(label) },
        suffix = { Text(unit) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
}

@Composable
private fun Warning(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Row(Modifier.padding(12.dp)) {
            Text("⚠️ ")
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Choix du type d'évènement à ajouter (depuis le journal). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TypePickerSheet(onDismiss: () -> Unit, onPick: (EventType) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Ajouter", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            EventType.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { t ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(t) }
                                }
                                .padding(vertical = 14.dp),
                        ) {
                            TypeBadge(t, 44.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(t.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
