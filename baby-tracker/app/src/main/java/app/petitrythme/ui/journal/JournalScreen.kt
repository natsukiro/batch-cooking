package app.petitrythme.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.petitrythme.data.DiaperKind
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.MainViewModel
import app.petitrythme.ui.components.DatePickDialog
import app.petitrythme.ui.components.TypeBadge
import app.petitrythme.ui.describe
import app.petitrythme.ui.summarize
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(
    vm: MainViewModel,
    day: LocalDate,
    onDayChange: (LocalDate) -> Unit,
    onOpenEntry: (Event) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onDelete: (Event) -> Unit,
) {
    val today = Fmt.today()
    val from = Fmt.startOfDay(day)
    val to = Fmt.startOfDay(day.plusDays(1))
    // On charge aussi la veille pour calculer l'intervalle avec le repas précédent.
    val events by remember(from) { vm.eventsBetween(from - 86_400_000L, to) }.collectAsState(initial = null)
    val all = events ?: emptyList()
    val dayEvents = all.filter { it.time in from until to }
    val summary = remember(all, from) { summarize(all, from, to) }
    val prevFeed: Map<Long, Long> = remember(all) {
        val feeds = all.filter { it.type.isFeeding }.sortedBy { it.time }
        feeds.zipWithNext().associate { (a, b) -> b.id to (b.time - a.time) }
    }
    var pickDay by remember { mutableStateOf(false) }
    val sync by vm.sync.collectAsState()
    // En suivi partagé, on indique qui a noté ce qui ne vient pas de ce téléphone.
    val me = sync.me.trim().takeIf { sync.enabled }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Navigation par jour
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onDayChange(day.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Jour précédent")
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { pickDay = true }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Fmt.dayLabel(day, today), style = MaterialTheme.typography.titleLarge)
                    if (day == today || day == today.minusDays(1)) {
                        Text(Fmt.dayFull(day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { onDayChange(day.plusDays(1)) }, enabled = day < today) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Jour suivant")
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        FlowRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            SummaryItem("🍽️", "${summary.feeds} repas")
                            if (summary.nursingMs > 0) SummaryItem("🤱", Fmt.durationCompact(summary.nursingMs))
                            if (summary.bottleMl > 0) SummaryItem("🍼", "${summary.bottleMl.toInt()} ml")
                            SummaryItem("💧", "${summary.pee}")
                            SummaryItem("💩", "${summary.poo}")
                            SummaryItem("😴", Fmt.durationCompact(summary.sleepMs))
                        }
                    }
                }
                if (events != null && dayEvents.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("🌙", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Rien de noté ce jour-là", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Utilisez « Ajouter » pour saisir un oubli.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(dayEvents, key = { it.id }) { e ->
                    SwipeRow(e, onDelete = { onDelete(e) }) {
                        EventRow(e, prevFeed[e.id], by = e.author?.takeIf { me != null && it != me }, onClick = { onOpenEntry(e) })
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onAdd(day) },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Ajouter") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (pickDay) {
        DatePickDialog(day, onDismiss = { pickDay = false }) { onDayChange(it) }
    }
}

@Composable
private fun SummaryItem(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 15.sp)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(e: Event, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

@Composable
fun EventRow(e: Event, sincePrevFeed: Long?, by: String? = null, onClick: () -> Unit) {
    val (title, sub) = describe(e)
    val subtitle = listOfNotNull(sub, by?.let { "par $it" }).joinToString(" · ").ifEmpty { null }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Fmt.time(e.time),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
            )
            val emoji = if (e.type == EventType.DIAPER) {
                if (DiaperKind.of(e.sub)?.hasPoo == true) "💩" else "💧"
            } else e.type.emoji
            TypeBadge(e.type, 40.dp, emoji)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (sincePrevFeed != null && e.type in EventType.FEEDINGS) {
                Text(
                    "+${Fmt.durationCompact(sincePrevFeed)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
