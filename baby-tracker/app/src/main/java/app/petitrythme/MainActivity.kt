package app.petitrythme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.petitrythme.ui.AppRoot
import app.petitrythme.ui.MainViewModel
import app.petitrythme.ui.theme.PetitRythmeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(application) } })
            val profile by vm.profile.collectAsStateWithLifecycle()
            PetitRythmeTheme(profile.theme) {
                AppRoot(vm)
            }
        }
    }
}
