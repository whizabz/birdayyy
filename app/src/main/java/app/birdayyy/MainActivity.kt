package app.birdayyy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.birdayyy.ui.AppViewModel
import app.birdayyy.ui.BirdayyyRoot
import app.birdayyy.ui.BirdayyyTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsState()
            BirdayyyTheme(settings.theme, settings.dynamicColor) { BirdayyyRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "open_today"
        const val EXTRA_WISH_PERSON = "wish_person"
        const val EXTRA_WISH_DATE = "wish_date"
    }
}
