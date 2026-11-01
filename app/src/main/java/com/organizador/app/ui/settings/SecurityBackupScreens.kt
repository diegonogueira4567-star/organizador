package com.organizador.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.core.security.LockManager
import com.organizador.app.core.sync.SyncCoordinator
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.ConfirmDialog
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.SectionHeader
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.data.backup.BackupException
import com.organizador.data.backup.BackupManager
import com.organizador.data.backup.BackupPreview
import com.organizador.data.backup.BackupSection
import com.organizador.data.backup.RestoreMode
import com.organizador.data.security.PinCheck
import com.organizador.data.security.PinManager
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ---------------- Segurança ----------------

@HiltViewModel
class SecurityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pin: PinManager,
    private val settings: SettingsRepository,
    private val lock: LockManager,
) : ViewModel() {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun biometricAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun enablePin(newPin: String) = viewModelScope.launch {
        try {
            pin.setPin(newPin)
            settings.update { it.copy(pinEnabled = true) }
            messages.emit("Bloqueio por PIN ativado")
        } catch (e: IllegalArgumentException) {
            messages.emit(e.message ?: "PIN inválido")
        }
    }

    fun disablePin(current: String) = viewModelScope.launch {
        when (val r = pin.verify(current)) {
            PinCheck.Ok, PinCheck.NotSet -> {
                pin.clear()
                settings.update { it.copy(pinEnabled = false, biometricEnabled = false) }
                messages.emit("Bloqueio desativado")
            }
            is PinCheck.Wrong -> messages.emit("PIN incorreto (${r.attemptsLeft} tentativas restantes)")
            is PinCheck.LockedOut -> messages.emit("Muitas tentativas. Aguarde para tentar novamente.")
        }
    }

    fun setBiometric(enabled: Boolean) = viewModelScope.launch { settings.update { it.copy(biometricEnabled = enabled) } }
    fun setTimeout(seconds: Int) = viewModelScope.launch { settings.update { it.copy(lockTimeoutSeconds = seconds) } }
    fun lockNow() = lock.lockNow()

    /** Remove arquivos temporários (restaurações interrompidas, arquivos compartilhados). */
    fun clearTemp() = viewModelScope.launch {
        val freed = withContext(Dispatchers.IO) {
            context.cacheDir.listFiles()?.filter { it.name.startsWith("restore-") || it.name == "shared" }?.sumOf { f ->
                val size = f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                f.deleteRecursively()
                size
            } ?: 0L
        }
        messages.emit("Arquivos temporários removidos (${Formats.fileSize(freed)})")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecuritySettings() {
    val vm: SecurityViewModel = hiltViewModel()
    val actions = LocalAppActions.current
    val s = LocalAppSettings.current
    var setPin by remember { mutableStateOf(false) }
    var disablePin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { actions.showMessage(it, null, null) } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionHeader("Bloqueio do aplicativo")
        SwitchLine("Bloqueio por PIN", s.pinEnabled, "O PIN é guardado apenas como hash criptográfico") { v -> if (v) setPin = true else disablePin = true }
        if (s.pinEnabled) {
            TextButton(onClick = { setPin = true }) { Text("Alterar PIN") }
            val biometric = vm.biometricAvailable()
            SwitchLine(
                "Desbloquear com biometria", s.biometricEnabled && biometric,
                if (biometric) "Impressão digital ou rosto cadastrados no aparelho" else "Nenhuma biometria disponível neste aparelho",
                enabled = biometric,
            ) { vm.setBiometric(it) }
            Text("Bloquear após sair do app por", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Imediato", 30 to "30 s", 60 to "1 min", 300 to "5 min", 900 to "15 min").forEach { (sec, label) ->
                    FilterChip(s.lockTimeoutSeconds == sec, { vm.setTimeout(sec) }, { Text(label) })
                }
            }
            OutlinedButton(onClick = { vm.lockNow() }, modifier = Modifier.padding(top = 8.dp)) { Text("Bloquear agora") }
        }
        SectionHeader("Privacidade")
        Text(
            "• Todos os dados ficam no armazenamento privado do app, inacessível a outros aplicativos.\n" +
                "• O app não possui permissão de internet e não envia informações.\n" +
                "• O backup automático do Android está desativado; use o backup do próprio app (com senha opcional).\n" +
                "• Anexos são copiados para a área privada e compartilhados apenas quando você os abre.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { vm.clearTemp() }, modifier = Modifier.padding(top = 12.dp)) { Text("Limpar arquivos temporários") }
        Spacer(Modifier.height(48.dp))
    }

    if (setPin) {
        var first by remember { mutableStateOf("") }
        var second by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { setPin = false },
            title = { Text("Definir PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(first, { if (it.length <= 12 && it.all(Char::isDigit)) first = it }, label = { Text("Novo PIN (4 a 12 dígitos)") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                    OutlinedTextField(second, { if (it.length <= 12 && it.all(Char::isDigit)) second = it }, label = { Text("Confirme o PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, isError = second.isNotEmpty() && second != first)
                    Text("Se esquecer o PIN, não há como recuperá-lo sem apagar os dados do app. Mantenha um backup.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = first.length >= 4 && first == second, onClick = { vm.enablePin(first); setPin = false }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { setPin = false }) { Text("Cancelar") } },
        )
    }
    if (disablePin) {
        var current by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { disablePin = false },
            title = { Text("Desativar bloqueio") },
            text = { OutlinedTextField(current, { if (it.all(Char::isDigit)) current = it }, label = { Text("PIN atual") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true) },
            confirmButton = { TextButton(enabled = current.length >= 4, onClick = { vm.disablePin(current); disablePin = false }) { Text("Desativar") } },
            dismissButton = { TextButton(onClick = { disablePin = false }) { Text("Cancelar") } },
        )
    }
}

// ---------------- Backup ----------------

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val message: String) : BackupUiState
    data class NeedsPassword(val source: BackupSource, val wrong: Boolean) : BackupUiState
    data class Preview(val source: BackupSource, val preview: BackupPreview, val password: CharArray?) : BackupUiState
}

/** Origem reabrível do backup (o arquivo é lido duas vezes: inspeção completa e restauração). */
sealed interface BackupSource {
    data class Document(val uri: Uri) : BackupSource
    data class Local(val file: File) : BackupSource
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupManager,
    private val sync: SyncCoordinator,
    private val time: TimeProvider,
) : ViewModel() {
    val state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val preventive = MutableStateFlow(backup.preventiveBackups())

    fun suggestedName(): String = "organizador-backup-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(time.now().atZone(ZoneId.systemDefault())) + ".zip"

    private fun open(source: BackupSource) = when (source) {
        is BackupSource.Document -> context.contentResolver.openInputStream(source.uri) ?: throw java.io.IOException("Não foi possível abrir o arquivo")
        is BackupSource.Local -> source.file.inputStream()
    }

    fun export(uri: Uri, sections: Set<BackupSection>, password: String?) = viewModelScope.launch {
        state.value = BackupUiState.Working("Gerando backup…")
        try {
            val manifest = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")?.use { backup.export(it, sections, password?.toCharArray()) }
                    ?: throw java.io.IOException("Não foi possível criar o arquivo")
            }
            messages.emit("Backup salvo: ${manifest.counts["tarefas"] ?: 0} tarefas, ${manifest.counts["notas"] ?: 0} notas, ${manifest.counts["checklists"] ?: 0} checklists" + if (manifest.encrypted) " (protegido por senha)" else "")
        } catch (e: Exception) {
            messages.emit("Falha ao exportar: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            state.value = BackupUiState.Idle
        }
    }

    fun inspect(source: BackupSource, password: String? = null) = viewModelScope.launch {
        state.value = BackupUiState.Working("Verificando integridade…")
        try {
            val chars = password?.toCharArray()
            val preview = backup.inspect({ open(source) }, chars)
            state.value = if (preview.needsPassword) BackupUiState.NeedsPassword(source, wrong = false) else BackupUiState.Preview(source, preview, chars)
        } catch (e: BackupException.WrongPassword) {
            state.value = BackupUiState.NeedsPassword(source, wrong = true)
        } catch (e: BackupException) {
            state.value = BackupUiState.Idle
            messages.emit(e.message ?: "Backup inválido")
        } catch (e: Exception) {
            state.value = BackupUiState.Idle
            messages.emit("Arquivo ilegível: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun restore(source: BackupSource, password: CharArray?, mode: RestoreMode, sections: Set<BackupSection>) = viewModelScope.launch {
        state.value = BackupUiState.Working("Restaurando… não feche o aplicativo")
        try {
            val report = backup.restore({ open(source) }, password, mode, sections)
            sync.fullSync()
            preventive.value = backup.preventiveBackups()
            messages.emit("Restauração concluída. Backup preventivo criado: ${report.preventiveBackup}")
        } catch (e: Exception) {
            messages.emit("Restauração cancelada, nenhum dado foi alterado: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            state.value = BackupUiState.Idle
        }
    }

    fun cancel() { state.value = BackupUiState.Idle }
}

private fun sectionLabel(s: BackupSection) = when (s) {
    BackupSection.TASKS -> "Tarefas e projetos"
    BackupSection.NOTES -> "Notas e pastas"
    BackupSection.CHECKLISTS -> "Checklists, rotinas e histórico de execuções"
    BackupSection.HISTORY -> "Histórico de atividades"
    BackupSection.SETTINGS -> "Configurações"
    BackupSection.ATTACHMENTS -> "Anexos (arquivos e imagens)"
}

@Composable
fun BackupSettings() {
    val vm: BackupViewModel = hiltViewModel()
    val actions = LocalAppActions.current
    val state by vm.state.collectAsStateWithLifecycle()
    val preventive by vm.preventive.collectAsStateWithLifecycle()
    var sections by remember { mutableStateOf(BackupSection.entries.toSet()) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.messages.collect { actions.showMessage(it, null, null) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.export(it, sections, password.takeIf { usePassword && it.isNotEmpty() }) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.inspect(BackupSource.Document(it)) } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionHeader("Exportar")
        Text("O backup é um arquivo .zip com manifesto e verificação de integridade (SHA-256). Escolha o que incluir:", style = MaterialTheme.typography.bodySmall)
        BackupSection.entries.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(s in sections, { checked -> sections = if (checked) sections + s else sections - s })
                Text(sectionLabel(s))
            }
        }
        SwitchLine("Proteger com senha", usePassword, "Criptografia AES-256-GCM; sem a senha o backup não pode ser aberto") { usePassword = it }
        if (usePassword) {
            OutlinedTextField(password, { password = it }, label = { Text("Senha (mín. 8 caracteres)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirme a senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = confirm.isNotEmpty() && confirm != password, modifier = Modifier.fillMaxWidth())
        }
        val canExport = sections.any { it != BackupSection.ATTACHMENTS } && (!usePassword || (password.length >= 8 && password == confirm)) && state is BackupUiState.Idle
        Button(onClick = { exportLauncher.launch(vm.suggestedName()) }, enabled = canExport, modifier = Modifier.padding(top = 8.dp)) { Text("Exportar backup") }

        SectionHeader("Importar / restaurar")
        Text("O arquivo é validado por completo antes de qualquer alteração. Você verá o conteúdo e escolherá entre substituir ou mesclar.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = state is BackupUiState.Idle, modifier = Modifier.padding(top = 8.dp)) { Text("Escolher arquivo de backup") }

        SectionHeader("Backups preventivos")
        Text("Criados automaticamente antes de cada restauração (os 3 mais recentes ficam no aparelho).", style = MaterialTheme.typography.bodySmall)
        if (preventive.isEmpty()) Text("Nenhum ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        preventive.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodyMedium)
                    Text(Formats.fileSize(f.length()) + " · " + Formats.dateTime(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault(), LocalAppSettings.current.dateFormat, LocalAppSettings.current.timeFormat), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.inspect(BackupSource.Local(f)) }) { Text("Restaurar") }
            }
        }
        Spacer(Modifier.height(48.dp))
    }

    when (val st = state) {
        is BackupUiState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text(st.message) },
            text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } },
            confirmButton = {},
        )
        is BackupUiState.NeedsPassword -> {
            var pass by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { vm.cancel() },
                title = { Text("Backup protegido") },
                text = {
                    Column {
                        if (st.wrong) Text("Senha incorreta.", color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(pass, { pass = it }, label = { Text("Senha do backup") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    }
                },
                confirmButton = { TextButton(enabled = pass.isNotEmpty(), onClick = { vm.inspect(st.source, pass) }) { Text("Abrir") } },
                dismissButton = { TextButton(onClick = { vm.cancel() }) { Text("Cancelar") } },
            )
        }
        is BackupUiState.Preview -> RestorePreviewDialog(st, onRestore = { mode, chosen -> vm.restore(st.source, st.password, mode, chosen) }, onCancel = { vm.cancel() })
        BackupUiState.Idle -> Unit
    }
}

@Composable
private fun RestorePreviewDialog(st: BackupUiState.Preview, onRestore: (RestoreMode, Set<BackupSection>) -> Unit, onCancel: () -> Unit) {
    val settings = LocalAppSettings.current
    val manifest = st.preview.manifest
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var chosen by remember { mutableStateOf(manifest.sections.toSet()) }
    var confirmReplace by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Conteúdo do backup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Criado em " + Formats.dateTime(Instant.ofEpochMilli(manifest.createdAt), ZoneId.systemDefault(), settings.dateFormat, settings.timeFormat) + " · versão ${manifest.appVersion}", style = MaterialTheme.typography.bodySmall)
                Text("Integridade verificada ✓" + if (manifest.encrypted) " · criptografado" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                st.preview.counts.filter { it.value > 0 }.forEach { (k, v) -> Text("• $v $k") }
                SectionHeader("Importar")
                manifest.sections.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s in chosen, { c -> chosen = if (c) chosen + s else chosen - s })
                        Text(sectionLabel(s), style = MaterialTheme.typography.bodySmall)
                    }
                }
                SectionHeader("Modo")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(mode == RestoreMode.MERGE, { mode = RestoreMode.MERGE })
                    Text("Mesclar: mantém seus dados e adiciona o que falta", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(mode == RestoreMode.REPLACE, { mode = RestoreMode.REPLACE })
                    Text("Substituir: apaga os dados atuais e restaura o backup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text("Um backup preventivo dos dados atuais é criado antes de qualquer alteração.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = { if (mode == RestoreMode.REPLACE) confirmReplace = true else onRestore(mode, chosen) }) { Text("Restaurar") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
    if (confirmReplace) {
        ConfirmDialog(
            "Substituir todos os dados?",
            "Todas as tarefas, notas, checklists e históricos atuais serão substituídos pelo conteúdo do backup. Um backup preventivo será criado antes.",
            "Substituir", destructive = true,
            onConfirm = { onRestore(RestoreMode.REPLACE, chosen) }, onDismiss = { confirmReplace = false },
        )
    }
}
