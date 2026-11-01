package com.organizador.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.organizador.app.core.security.LockManager
import com.organizador.app.core.sync.SyncCoordinator
import com.organizador.app.ui.AppRoot
import com.organizador.app.ui.lock.LockScreen
import com.organizador.app.ui.theme.OrganizadorTheme
import com.organizador.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var lock: LockManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sync: SyncCoordinator

    /** Rota solicitada por notificação ("organizador://open/<rota>"). */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lock.start()
        handleIntent(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val ready by lock.ready.collectAsStateWithLifecycle()
            val locked by lock.locked.collectAsStateWithLifecycle()
            val current = settings
            if (current == null || !ready) {
                Box(Modifier.fillMaxSize())
                return@setContent
            }
            OrganizadorTheme(current) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (locked && current.pinEnabled) {
                        LockScreen(biometricEnabled = current.biometricEnabled, onUnlocked = { lock.unlock() })
                    } else {
                        AppRoot(current, pendingRoute)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Garante execuções e alarmes atualizados ao voltar ao app (ex.: virada de dia em segundo plano).
        sync.requestSync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "organizador" && data.host == "open") {
            pendingRoute.value = data.path?.trimStart('/')?.takeIf { it.isNotBlank() }
        }
    }
}
