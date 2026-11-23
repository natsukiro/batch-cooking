package app.petitrythme.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.petitrythme.data.ThemeMode
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.MainViewModel
import app.petitrythme.ui.components.ChoiceChips
import app.petitrythme.ui.components.ConfirmDialog
import app.petitrythme.ui.components.DatePickDialog
import app.petitrythme.ui.components.Segmented
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: MainViewModel, onMessage: (String) -> Unit) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pickBirth by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val stamp = java.time.LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.exportJson(uri) }
                .onSuccess { onMessage("Sauvegarde enregistrée") }
                .onFailure { onMessage("Échec de la sauvegarde : ${it.message}") }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.exportCsv(uri) }
                .onSuccess { onMessage("Export CSV enregistré") }
                .onFailure { onMessage("Échec de l'export : ${it.message}") }
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    // Le rappel n'a de sens que si les notifications sont autorisées : on les demande en l'activant.
    val context = androidx.compose.ui.platform.LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onMessage("Autorisez les notifications de Petit Rythme dans les réglages Android pour recevoir le rappel.")
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))

        Group("Bébé") {
            Column(Modifier.padding(16.dp)) {
                // État local : le prénom est enregistré tel quel (les espaces en fin de saisie
                // ne doivent pas disparaître pendant qu'on tape « Léa Marie »).
                var name by remember { mutableStateOf(profile.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; vm.updateProfile { p -> p.copy(name = it) } },
                    label = { Text("Prénom") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row_(
                Icons.Rounded.Cake, "Date de naissance",
                profile.birthDate?.let { "${Fmt.dayFull(it)} · ${Fmt.age(it)}" } ?: "Non renseignée",
            ) { pickBirth = true }
        }

        Group("Rappel de repas") {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Une notification quand le dernier repas date de :",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                val options = listOf(0, 120, 150, 180, 210, 240)
                ChoiceChips(
                    options, profile.feedReminderMin,
                    label = { if (it == 0) "Non" else Fmt.duration(it * 60_000L) },
                    onSelect = { v ->
                        vm.updateProfile { it.copy(feedReminderMin = v ?: 0) }
                        if ((v ?: 0) > 0 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    allowDeselect = false,
                )
                Text(
                    "Compté depuis le début du dernier repas. Rien n'est envoyé pendant une tétée en cours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Group("Apparence") {
            Column(Modifier.padding(16.dp)) {
                Text("Thème", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Segmented(ThemeMode.entries, profile.theme, { it.label }, { t -> vm.updateProfile { it.copy(theme = t) } }, Modifier.fillMaxWidth())
                Text(
                    "Le thème sombre est très peu lumineux, idéal pour les tétées de nuit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Group("Données") {
            Row_(Icons.Rounded.Download, "Sauvegarder", "Journal et rendez-vous, à garder dans Drive ou par mail") {
                exportJson.launch("petit-rythme-$stamp.json")
            }
            Row_(Icons.Rounded.Upload, "Restaurer une sauvegarde", "Remplace les données actuelles") {
                importJson.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }
            Row_(Icons.Rounded.TableChart, "Exporter pour un tableur", "CSV lisible dans Excel / Sheets, utile pour le pédiatre") {
                exportCsv.launch("petit-rythme-$stamp.csv")
            }
            Row_(Icons.Rounded.DeleteForever, "Tout effacer", "Supprime définitivement le journal", danger = true) {
                confirmClear = true
            }
        }

        Text(
            "Petit Rythme ne remplace pas un avis médical. Toutes les données restent sur ce téléphone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (pickBirth) {
        DatePickDialog(profile.birthDate ?: Fmt.today(), onDismiss = { pickBirth = false }) { d ->
            vm.updateProfile { it.copy(birthDate = d) }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            "Tout effacer ?", "Tout le journal sera supprimé. Pensez à faire une sauvegarde avant.", "Effacer",
            onConfirm = { scope.launch { vm.clearAll(); onMessage("Journal effacé") } },
            onDismiss = { confirmClear = false },
        )
    }
    pendingRestore?.let { uri ->
        ConfirmDialog(
            "Restaurer cette sauvegarde ?", "Les données actuelles seront remplacées par celles du fichier.", "Restaurer",
            onConfirm = {
                scope.launch {
                    runCatching { vm.importJson(uri) }
                        .onSuccess { onMessage("$it entrées restaurées") }
                        .onFailure { onMessage("Fichier illisible : ${it.message}") }
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
            Column { content() }
        }
    }
}

@Composable
private fun Row_(icon: ImageVector, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = color) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
