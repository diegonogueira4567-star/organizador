package com.organizador.app.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.organizador.data.security.PinCheck
import com.organizador.data.security.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(val pin: PinManager) : ViewModel()

private fun showBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val manager = BiometricManager.from(activity)
    if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) return
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Organizador")
            .setNegativeButtonText("Usar PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build(),
    )
}

@Composable
fun LockScreen(biometricEnabled: Boolean, onUnlocked: () -> Unit) {
    val vm: LockViewModel = hiltViewModel()
    val activity = LocalContext.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(biometricEnabled) { if (biometricEnabled && activity != null) showBiometric(activity, onUnlocked) }

    fun submit() {
        if (entered.length < 4 || checking) return
        checking = true
        scope.launch {
            when (val r = vm.pin.verify(entered)) {
                PinCheck.Ok, PinCheck.NotSet -> onUnlocked()
                is PinCheck.Wrong -> message = "PIN incorreto. ${r.attemptsLeft} tentativa(s) antes do bloqueio temporário."
                is PinCheck.LockedOut -> message = "Muitas tentativas. Tente novamente após " +
                    DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(r.untilEpochMillis).atZone(ZoneId.systemDefault()))
            }
            entered = ""
            checking = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Digite seu PIN", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(maxOf(4, entered.length)) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < entered.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(message.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        when (key) {
                            "del" -> IconButton(onClick = { entered = entered.dropLast(1) }) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Apagar") }
                            "bio" -> if (biometricEnabled && activity != null) {
                                IconButton(onClick = { showBiometric(activity, onUnlocked) }) { Icon(Icons.Outlined.Fingerprint, "Biometria") }
                            }
                            else -> TextButton(onClick = { if (entered.length < 12) entered += key }, modifier = Modifier.size(72.dp)) {
                                Text(key, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { submit() }, enabled = entered.length >= 4 && !checking) { Text("Desbloquear") }
    }
}
