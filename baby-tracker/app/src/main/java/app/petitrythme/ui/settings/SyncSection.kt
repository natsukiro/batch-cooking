package app.petitrythme.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.petitrythme.sync.SyncConfig
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.MainViewModel
import app.petitrythme.ui.components.ConfirmDialog
import app.petitrythme.ui.components.rememberNow
import kotlinx.coroutines.launch

/** Partage du suivi avec l'autre parent, via le serveur de la famille (voir server/README.md). */
@Composable
fun SyncSection(vm: MainViewModel, onMessage: (String) -> Unit) {
    val s by vm.sync.collectAsStateWithLifecycle()
    val now by rememberNow(30_000)
    val scope = rememberCoroutineScope()
    var create by remember { mutableStateOf(false) }
    var join by remember { mutableStateOf(false) }
    var invite by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }

    Column(Modifier.padding(16.dp)) {
        if (!s.enabled) {
            Text(
                "Partagez le suivi avec le téléphone de l'autre parent : chacun voit en direct les tétées, " +
                    "couches, dodos et rendez-vous notés par l'autre. Il faut un petit serveur à vous " +
                    "(voir la notice « server/README.md »).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { create = true }, modifier = Modifier.weight(1f)) { Text("Créer l'espace") }
                OutlinedButton(onClick = { join = true }, modifier = Modifier.weight(1f)) { Text("Rejoindre") }
            }
        } else {
            val status = when {
                s.running -> "⟳ Synchronisation…"
                s.lastError != null -> "⚠ ${s.lastError}"
                s.lastSuccessAt > 0 -> "✓ Synchronisé ${Fmt.ago(s.lastSuccessAt, now)}"
                else -> "En attente de la première synchronisation"
            }
            Text(status, style = MaterialTheme.typography.titleSmall,
                color = if (s.lastError != null && !s.running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(
                "Serveur : ${s.server.removePrefix("https://").removePrefix("http://")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (SyncConfig.isInsecure(s.server)) InsecureWarning()
            var me by remember { mutableStateOf(s.me) }
            OutlinedTextField(
                value = me,
                onValueChange = { me = it; vm.setSyncName(it) },
                label = { Text("Mon prénom, vu par l'autre parent") },
                placeholder = { Text("Ex. : Papa") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { invite = true }, modifier = Modifier.weight(1f)) { Text("Inviter") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            vm.syncNow().onSuccess { n -> onMessage(if (n == 0) "Déjà à jour" else "$n changement(s) reçu(s)") }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Synchroniser") }
            }
            TextButton(onClick = { leave = true }) {
                Text("Arrêter le partage sur ce téléphone", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (create) CreateSyncDialog(vm, onDismiss = { create = false }, onDone = { create = false; invite = true; onMessage("Espace famille créé") })
    if (join) JoinSyncDialog(vm, initial = "", onDismiss = { join = false }, onDone = { join = false; onMessage("Suivi partagé : bienvenue !") })
    if (invite) InviteDialog(s.inviteLink, onDismiss = { invite = false })
    if (leave) {
        ConfirmDialog(
            "Arrêter le partage ?",
            "Ce téléphone ne recevra plus les saisies de l'autre parent. Les données déjà présentes restent ici. " +
                "Pour reprendre, il faudra un nouveau lien d'invitation.",
            "Arrêter",
            onConfirm = { vm.leaveSync(); onMessage("Partage arrêté sur ce téléphone") },
            onDismiss = { leave = false },
        )
    }
}

@Composable
private fun InsecureWarning() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            "⚠️ Adresse http:// hors du réseau local : les données circulent en clair sur Internet. Utilisez https://.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun CreateSyncDialog(vm: MainViewModel, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var me by remember { mutableStateOf(vm.sync.value.me) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Créer l'espace famille") },
        text = {
            Column {
                Text("Adresse de votre serveur de synchro :", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = server, onValueChange = { server = it; error = null },
                    placeholder = { Text("https://bebe.example.fr") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (SyncConfig.isInsecure(SyncConfig.normalizeServer(server))) InsecureWarning()
                OutlinedTextField(
                    value = me, onValueChange = { me = it }, label = { Text("Votre prénom") },
                    placeholder = { Text("Ex. : Papa") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("Mot de passe de création (si demandé)") }, singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && server.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        vm.createSync(server, me, token).onSuccess { onDone() }.onFailure { error = it.message }
                        busy = false
                    }
                },
            ) { if (busy) CircularProgressIndicator(Modifier.height(18.dp)) else Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Annuler") } },
    )
}

/** Rejoindre l'espace de l'autre parent (ouvert aussi par le lien d'invitation). */
@Composable
fun JoinSyncDialog(vm: MainViewModel, initial: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var invite by remember { mutableStateOf(initial) }
    var server by remember { mutableStateOf("") }
    var me by remember { mutableStateOf(vm.sync.value.me) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val needsServer = invite.isNotBlank() && !invite.contains("/join#k=") && !invite.startsWith("petitrythme://")
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Rejoindre le suivi") },
        text = {
            Column {
                Text(
                    "Collez le lien reçu de l'autre parent. Vos saisies déjà présentes sur ce téléphone seront ajoutées au suivi commun.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = invite, onValueChange = { invite = it; error = null },
                    label = { Text("Lien ou code d'invitation") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                if (needsServer) {
                    OutlinedTextField(
                        value = server, onValueChange = { server = it },
                        label = { Text("Adresse du serveur") }, placeholder = { Text("https://bebe.example.fr") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = me, onValueChange = { me = it }, label = { Text("Votre prénom") },
                    placeholder = { Text("Ex. : Maman") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && invite.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        vm.joinSync(invite, server, me).onSuccess { onDone() }.onFailure { error = it.message }
                        busy = false
                    }
                },
            ) { if (busy) CircularProgressIndicator(Modifier.height(18.dp)) else Text("Rejoindre") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Annuler") } },
    )
}

@Composable
private fun InviteDialog(link: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inviter l'autre parent") },
        text = {
            Column {
                Text(
                    "Envoyez ce lien à l'autre parent (SMS, WhatsApp…). Sur son téléphone, il ouvre Petit Rythme " +
                        "et rejoint votre suivi.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                ) {
                    SelectionContainer { Text(link, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp)) }
                }
                Text(
                    "Ce lien donne accès au suivi de bébé : ne le partagez qu'avec l'autre parent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Rejoins le suivi de bébé sur Petit Rythme : $link")
                context.startActivity(Intent.createChooser(send, "Envoyer l'invitation"))
            }) { Text("Partager") }
        },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(link)); onDismiss() }) { Text("Copier") }
        },
    )
}
