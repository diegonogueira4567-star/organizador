package com.organizador.app.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.organizador.domain.model.AppSettings
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bloqueio do app por PIN/biometria. O app trava na abertura e ao voltar do segundo plano depois do
 * tempo configurado. O estado vive apenas em memória: encerrar o processo sempre exige novo desbloqueio.
 */
@Singleton
class LockManager @Inject constructor(
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : DefaultLifecycleObserver {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile private var current: AppSettings = AppSettings()
    private var backgroundedAt: Long? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            var first = true
            settings.settings.collect {
                current = it
                if (first) {
                    _locked.value = it.pinEnabled
                    _ready.value = true
                    first = false
                } else if (!it.pinEnabled) {
                    _locked.value = false
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = time.now().toEpochMilli()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        if (current.pinEnabled && time.now().toEpochMilli() - since >= current.lockTimeoutSeconds * 1000L) {
            _locked.value = true
        }
    }

    fun unlock() {
        _locked.value = false
    }

    fun lockNow() {
        if (current.pinEnabled) _locked.value = true
    }
}
