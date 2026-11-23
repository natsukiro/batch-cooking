package app.petitrythme.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import app.petitrythme.data.Event
import app.petitrythme.ui.entry.EntrySheet
import app.petitrythme.ui.entry.TypePickerSheet
import app.petitrythme.ui.home.HomeScreen
import app.petitrythme.ui.home.defaultDraft
import app.petitrythme.ui.journal.JournalScreen
import app.petitrythme.ui.settings.SettingsScreen
import app.petitrythme.ui.stats.StatsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Rounded.Home),
    JOURNAL("Journal", Icons.Rounded.ViewAgenda),
    STATS("Rythme", Icons.Rounded.Insights),
    SETTINGS("Réglages", Icons.Rounded.Settings),
}

@Composable
fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var draft by remember { mutableStateOf<Event?>(null) }
    var pickTypeFor by remember { mutableStateOf<LocalDate?>(null) }
    var journalDay by rememberSaveable { mutableLongStateOf(Fmt.today().toEpochDay()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Rafraîchit la notification si l'autorisation vient d'être donnée.
        if (it) vm.timers.value.let { t -> app.petitrythme.notify.TimerNotifier.sync(context, t) }
    }
    fun onTimerStarted() {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun message(text: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(text, action, withDismissAction = action == null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }

    fun quickSave(e: Event, text: String) {
        scope.launch {
            val id = vm.insert(e)
            message(text, "Annuler") { scope.launch { vm.deleteById(id, e.type) } }
        }
    }

    fun deleted(e: Event) {
        vm.delete(e)
        message("${describe(e).first} supprimé", "Annuler") { vm.restore(e) }
    }

    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    vm,
                    onOpenEntry = { draft = it },
                    onQuickSave = ::quickSave,
                    onSaved = { e, text -> message(text, "Modifier") { draft = e } },
                    onTimerStarted = ::onTimerStarted,
                    onOpenSettings = { tab = Tab.SETTINGS },
                )
                Tab.JOURNAL -> JournalScreen(
                    vm,
                    day = LocalDate.ofEpochDay(journalDay),
                    onDayChange = { journalDay = it.toEpochDay() },
                    onOpenEntry = { draft = it },
                    onAdd = { pickTypeFor = it },
                    onDelete = ::deleted,
                )
                Tab.STATS -> StatsScreen(vm, onOpenDay = { journalDay = it.toEpochDay(); tab = Tab.JOURNAL })
                Tab.SETTINGS -> SettingsScreen(vm, onMessage = { message(it) })
            }
        }
    }

    pickTypeFor?.let { day ->
        TypePickerSheet(onDismiss = { pickTypeFor = null }) { type ->
            pickTypeFor = null
            val at = if (day == Fmt.today()) System.currentTimeMillis()
            else day.atTime(LocalTime.now()).atZone(Fmt.zone).toInstant().toEpochMilli()
            if (type == app.petitrythme.data.EventType.BOTTLE) {
                val p = vm.profile.value
                draft = Event(type = type, time = at, amountMl = p.lastBottleMl.toDouble(), sub = p.lastMilk.code)
            } else {
                draft = defaultDraft(type, at)
            }
        }
    }

    draft?.let { d ->
        EntrySheet(
            initial = d,
            onDismiss = { draft = null },
            onSave = { e ->
                vm.save(e)
                message(if (e.id == 0L) "${describe(e).first} enregistré" else "Modification enregistrée")
            },
            onDelete = ::deleted,
        )
    }
}
