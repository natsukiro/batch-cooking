package app.petitrythme.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.petitrythme.data.Category
import app.petitrythme.data.DiaperKind
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.effectiveEnd
import app.petitrythme.ui.DaySummary
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.MainViewModel
import app.petitrythme.ui.components.Segmented
import app.petitrythme.ui.components.rememberNow
import app.petitrythme.ui.hasData
import app.petitrythme.ui.meanFeedInterval
import app.petitrythme.ui.summarize
import app.petitrythme.ui.theme.color
import java.time.LocalDate

private const val DAY = 86_400_000L

@Composable
fun StatsScreen(vm: MainViewModel, onOpenDay: (LocalDate) -> Unit) {
    var range by rememberSaveable { mutableIntStateOf(7) }
    val today = Fmt.today()
    val first = today.minusDays(range - 1L)
    val from = Fmt.startOfDay(first)
    val to = Fmt.startOfDay(today.plusDays(1))
    val events by remember(from, to) { vm.eventsBetween(from - DAY, to) }.collectAsState(initial = emptyList())
    val timers by vm.timers.collectAsStateWithLifecycle()
    val growth by vm.growth.collectAsStateWithLifecycle()
    val now by rememberNow(60_000)

    val days = remember(first, range) { (0 until range).map { first.plusDays(it.toLong()) } }
    val summaries = remember(events, days) {
        days.map { d -> summarize(events, Fmt.startOfDay(d), Fmt.startOfDay(d.plusDays(1))) }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Rythme", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Segmented(listOf(7, 14), range, { "$it j" }, { range = it }, Modifier.width(150.dp))
        }

        ChartCard("Sur 24 heures", "Touchez un jour pour l'ouvrir dans le journal") {
            RhythmChart(days.reversed(), events, timers.sleepStart, now, onOpenDay)
        }

        // Moyennes sur les jours complets réellement suivis (pas les jours vides avant le début du suivi).
        val tracked = summaries.dropLast(1).filter { it.hasData() }
            .ifEmpty { listOfNotNull(summaries.last().takeIf { it.hasData() }) }
        AveragesCard(tracked, complete = summaries.dropLast(1).any { it.hasData() }, interval = meanFeedInterval(events, from, to))

        BarCard("Repas par jour", days, summaries.map { it.feeds.toDouble() }, Category.FEED) { "${it.toInt()}" }
        BarCard("Sommeil", days, summaries.map { it.sleepMs / 3_600_000.0 }, Category.SLEEP) { Fmt.durationCompact((it * 3_600_000).toLong()) }
        if (summaries.any { it.bottleMl > 0 }) {
            BarCard("Lait au biberon", days, summaries.map { it.bottleMl }, Category.FEED) { "${it.toInt()} ml" }
        }
        if (summaries.any { it.nursingMs > 0 }) {
            BarCard("Temps au sein", days, summaries.map { it.nursingMs / 60_000.0 }, Category.FEED) { Fmt.durationCompact((it * 60_000).toLong()) }
        }
        BarCard("Couches mouillées (pipi)", days, summaries.map { it.pee.toDouble() }, Category.DIAPER) { "${it.toInt()}" }
        BarCard("Selles (caca)", days, summaries.map { it.poo.toDouble() }, Category.DIAPER) { "${it.toInt()}" }

        GrowthCard(growth)
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Une ligne par jour, de minuit à minuit : sommeil en barres, repas en traits,
 * couches en points sur une voie séparée. Formes et positions différentes en
 * plus des couleurs, pour rester lisible sans distinguer les teintes.
 */
@Composable
private fun RhythmChart(
    days: List<LocalDate>,
    events: List<Event>,
    sleepingSince: Long?,
    now: Long,
    onOpenDay: (LocalDate) -> Unit,
) {
    val sleepC = Category.SLEEP.color()
    val feedC = Category.FEED.color()
    val diaperC = Category.DIAPER.color()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val today = Fmt.today()
    val labelW = 44.dp

    // Heures
    Row {
        Spacer(Modifier.width(labelW))
        Canvas(
            Modifier
                .weight(1f)
                .height(16.dp)
        ) {
            listOf(0, 6, 12, 18, 24).forEach { h ->
                val r = measurer.measure("${h}h", labelStyle)
                val x = (size.width * h / 24f - r.size.width / 2f).coerceIn(0f, size.width - r.size.width)
                drawText(r, topLeft = Offset(x, 0f))
            }
        }
    }
    days.forEach { d ->
        val start = Fmt.startOfDay(d)
        val end = Fmt.startOfDay(d.plusDays(1))
        Row(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clickable { onOpenDay(d) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (d == today) "Auj." else "${Fmt.weekdayInitial(d)} ${d.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                modifier = Modifier.width(labelW),
            )
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val w = size.width
                val h = size.height
                fun x(t: Long) = ((t - start).toFloat() / DAY * w).coerceIn(0f, w)
                val laneTop = h * 0.18f
                val laneH = h * 0.42f
                drawRoundRect(track, Offset(0f, laneTop), Size(w, laneH), CornerRadius(4.dp.toPx()))
                for (i in 1..3) {
                    val gx = w * i / 4f
                    drawLine(grid, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                }
                // Sommeil
                val sleeps = events.filter { it.type == EventType.SLEEP }.map { it.time to it.effectiveEnd() } +
                    listOfNotNull(sleepingSince?.let { it to now })
                sleeps.forEach { (a, b) ->
                    if (b > start && a < end) {
                        val x0 = x(maxOf(a, start))
                        val x1 = x(minOf(b, end))
                        drawRoundRect(
                            sleepC, Offset(x0, laneTop), Size((x1 - x0).coerceAtLeast(2.dp.toPx()), laneH),
                            CornerRadius(4.dp.toPx()),
                        )
                    }
                }
                // Repas : traits verticaux qui débordent de la voie
                events.filter { it.type.isFeeding && it.time in start until end }.forEach { e ->
                    val fx = x(e.time)
                    drawLine(
                        feedC, Offset(fx, laneTop - 3.dp.toPx()), Offset(fx, laneTop + laneH + 3.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
                // Couches : points sous la voie
                events.filter { it.type == EventType.DIAPER && it.time in start until end }.forEach { e ->
                    val k = DiaperKind.of(e.sub)
                    val cy = laneTop + laneH + 7.dp.toPx()
                    val r = 3.dp.toPx()
                    if (k?.hasPoo == true) drawCircle(diaperC, r, Offset(x(e.time), cy))
                    else drawCircle(diaperC, r - 0.75.dp.toPx(), Offset(x(e.time), cy), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
    // Légende
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("Sommeil") { drawRoundRect(sleepC, Offset(0f, size.height * 0.25f), Size(size.width, size.height * 0.5f), CornerRadius(3.dp.toPx())) }
        LegendItem("Repas") {
            drawLine(feedC, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.5.dp.toPx(), StrokeCap.Round)
        }
        LegendItem("Caca") { drawCircle(diaperC, 3.dp.toPx()) }
        LegendItem("Pipi") { drawCircle(diaperC, 2.25.dp.toPx(), style = Stroke(1.5.dp.toPx())) }
    }
}

@Composable
private fun LegendItem(label: String, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(14.dp), onDraw = draw)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AveragesCard(s: List<DaySummary>, complete: Boolean, interval: Long?) {
    val n = s.size.coerceAtLeast(1)
    fun avg(f: (DaySummary) -> Double) = s.sumOf(f) / n
    val subtitle = when {
        s.isEmpty() -> "Pas encore de données"
        !complete -> "Aujourd'hui (pas encore de journée complète)"
        s.size == 1 -> "Sur le dernier jour complet"
        else -> "Sur les ${s.size} derniers jours complets suivis"
    }
    ChartCard("Moyennes par jour", subtitle) {
        if (s.isEmpty()) return@ChartCard
        val items = buildList {
            add("Repas" to Fmt.num(avg { it.feeds.toDouble() }, 1))
            if (interval != null) add("Entre deux repas" to Fmt.durationCompact(interval))
            add("Sommeil" to Fmt.durationCompact(avg { it.sleepMs.toDouble() }.toLong()))
            add("Plus long dodo" to Fmt.durationCompact(s.maxOfOrNull { it.longestSleepMs } ?: 0))
            add("Pipis" to Fmt.num(avg { it.pee.toDouble() }, 1))
            add("Cacas" to Fmt.num(avg { it.poo.toDouble() }, 1))
            if (s.any { it.bottleMl > 0 }) add("Biberon" to "${avg { it.bottleMl }.toInt()} ml")
            if (s.any { it.nursingMs > 0 }) add("Au sein" to Fmt.durationCompact(avg { it.nursingMs.toDouble() }.toLong()))
        }
        items.chunked(2).forEach { row ->
            Row(Modifier.padding(vertical = 4.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.titleLarge)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Histogramme d'une seule mesure ; un appui sur une barre affiche sa valeur. */
@Composable
private fun BarCard(
    title: String,
    days: List<LocalDate>,
    values: List<Double>,
    category: Category,
    format: (Double) -> String,
) {
    var selected by remember(days) { mutableIntStateOf(days.lastIndex) }
    val c = category.color()
    val muted = c.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = label)
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1e-9)
    val sel = selected.coerceIn(0, days.lastIndex)

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(format(values.getOrElse(sel) { 0.0 }), style = MaterialTheme.typography.titleLarge)
                    Text(Fmt.dayLabel(days[sel]), style = MaterialTheme.typography.bodySmall, color = label)
                }
            }
            Spacer(Modifier.height(10.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .pointerInput(days) {
                        detectTapGestures { p ->
                            val slot = size.width / days.size.toFloat()
                            selected = (p.x / slot).toInt().coerceIn(0, days.lastIndex)
                        }
                    }
            ) {
                val axisH = 16.dp.toPx()
                val chartH = size.height - axisH
                val slot = size.width / days.size
                val gap = 2.dp.toPx()
                val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                drawLine(grid, Offset(0f, chartH), Offset(size.width, chartH), 1f)
                values.forEachIndexed { i, v ->
                    val bh = (v / max * (chartH - 4.dp.toPx())).toFloat()
                    val left = slot * i + (slot - barW) / 2
                    if (bh > 0) {
                        val r = minOf(4.dp.toPx(), barW / 2, bh)
                        val path = Path().apply {
                            // coins arrondis en haut seulement, ancré sur la ligne de base
                            moveTo(left, chartH - gap / 2)
                            lineTo(left, chartH - bh + r)
                            quadraticTo(left, chartH - bh, left + r, chartH - bh)
                            lineTo(left + barW - r, chartH - bh)
                            quadraticTo(left + barW, chartH - bh, left + barW, chartH - bh + r)
                            lineTo(left + barW, chartH - gap / 2)
                            close()
                        }
                        drawPath(path, if (i == sel) c else muted)
                    }
                    if (days.size <= 7 || i % 2 == (days.size - 1) % 2) {
                        val t = measurer.measure(Fmt.weekdayInitial(days[i]), labelStyle)
                        drawText(t, topLeft = Offset(slot * i + (slot - t.size.width) / 2, chartH + 3.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun GrowthCard(growth: List<Event>) {
    val weights = growth.filter { it.value != null }.sortedBy { it.time }
    ChartCard("Croissance", if (growth.isEmpty()) "Ajoutez une mesure depuis l'accueil (📏)" else null) {
        if (growth.isEmpty()) return@ChartCard
        val last = growth.first()
        Row {
            listOf(
                "Poids" to weights.lastOrNull()?.value?.let { "${Fmt.num(it, 3)} kg" },
                "Taille" to growth.firstOrNull { it.heightCm != null }?.heightCm?.let { "${Fmt.num(it, 1)} cm" },
                "PC" to growth.firstOrNull { it.headCm != null }?.headCm?.let { "${Fmt.num(it, 1)} cm" },
            ).forEach { (l, v) ->
                Column(Modifier.weight(1f)) {
                    Text(v ?: "—", style = MaterialTheme.typography.titleLarge)
                    Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            "Dernière mesure : ${Fmt.dayLabel(Fmt.date(last.time))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (weights.size >= 2) {
            Spacer(Modifier.height(12.dp))
            WeightChart(weights)
        }
    }
}

@Composable
private fun WeightChart(points: List<Event>) {
    val c = Category.GROWTH.color()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = label)
    var sel by remember(points) { mutableStateOf(points.lastIndex) }
    val t0 = points.first().time
    val t1 = points.last().time.coerceAtLeast(t0 + 1)
    val vs = points.map { it.value!! }
    val lo = vs.min()
    val hi = vs.max().coerceAtLeast(lo + 0.1)
    Box {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(points) {
                    detectTapGestures { p ->
                        val pad = 8.dp.toPx()
                        val w = size.width - pad * 2
                        sel = points.indices.minBy { i ->
                            kotlin.math.abs(pad + (points[i].time - t0).toFloat() / (t1 - t0) * w - p.x)
                        }
                    }
                }
        ) {
            val pad = 8.dp.toPx()
            val top = 22.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            val w = size.width - pad * 2
            fun px(i: Int) = pad + (points[i].time - t0).toFloat() / (t1 - t0) * w
            fun py(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
            drawLine(grid, Offset(0f, bottom), Offset(size.width, bottom), 1f)
            val path = Path()
            points.indices.forEach { i ->
                if (i == 0) path.moveTo(px(i), py(vs[i])) else path.lineTo(px(i), py(vs[i]))
            }
            drawPath(path, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            points.indices.forEach { i ->
                drawCircle(surface, 6.dp.toPx(), Offset(px(i), py(vs[i])))
                drawCircle(c, if (i == sel) 5.dp.toPx() else 4.dp.toPx(), Offset(px(i), py(vs[i])))
            }
            val tl = measurer.measure("${Fmt.num(vs[sel], 3)} kg · ${Fmt.dayShort(Fmt.date(points[sel].time))}", style)
            val lx = (px(sel) - tl.size.width / 2).coerceIn(0f, size.width - tl.size.width)
            drawText(tl, topLeft = Offset(lx, 0f))
            val a = measurer.measure(Fmt.dayShort(Fmt.date(t0)), style)
            drawText(a, topLeft = Offset(0f, bottom + 3.dp.toPx()))
            val b = measurer.measure(Fmt.dayShort(Fmt.date(t1)), style)
            drawText(b, topLeft = Offset(size.width - b.size.width, bottom + 3.dp.toPx()))
        }
    }
}
