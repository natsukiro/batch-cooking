package app.petitrythme

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TAB)?.let { requestedTab.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedTab.value = intent.getStringExtra(EXTRA_TAB)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(application) } })
            val profile by vm.profile.collectAsStateWithLifecycle()
            PetitRythmeTheme(profile.theme) {
                AppRoot(vm, requestedTab.value, onTabHandled = { requestedTab.value = null })
            }
        }
    }
}
