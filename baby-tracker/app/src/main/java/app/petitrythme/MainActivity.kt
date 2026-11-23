package app.petitrythme

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.petitrythme.ui.AppRoot
import app.petitrythme.ui.MainViewModel
import app.petitrythme.ui.theme.PetitRythmeTheme

class MainActivity : ComponentActivity() {
    companion object {
        /** Onglet à ouvrir (depuis une notification de rendez-vous). */
        const val EXTRA_TAB = "tab"
        const val TAB_APPOINTMENTS = "appointments"
    }

    private val requestedTab = mutableStateOf<String?>(null)
    /** Lien d'invitation reçu (petitrythme://join?…) à proposer à l'utilisateur. */
    private val pendingJoin = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TAB)?.let { requestedTab.value = it }
        readJoinLink(intent)
    }

    private fun readJoinLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "petitrythme" && data.host == "join") pendingJoin.value = data.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            requestedTab.value = intent.getStringExtra(EXTRA_TAB)
            readJoinLink(intent)
        }
        // Appli ouverte : on récupère les saisies de l'autre parent toutes les 20 s.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    (application as BabyApp).sync.sync()
                    delay(20_000)
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(application) } })
            val profile by vm.profile.collectAsStateWithLifecycle()
            PetitRythmeTheme(profile.theme) {
                AppRoot(
                    vm, requestedTab.value, onTabHandled = { requestedTab.value = null },
                    pendingJoin = pendingJoin.value, onJoinHandled = { pendingJoin.value = null },
                )
            }
        }
    }
}
