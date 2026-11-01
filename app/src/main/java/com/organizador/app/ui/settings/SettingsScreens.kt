package com.organizador.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizador.app.BuildConfig
import com.organizador.app.core.notifications.AlarmScheduler
import com.organizador.app.core.notifications.NotificationHelper
import com.organizador.app.core.sync.SyncCoordinator
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.ColorPickerRow
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.SectionHeader
import com.organizador.app.ui.common.SettingLine
import com.organizador.app.ui.common.TimePickerModal
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.domain.model.AppSettings
import com.organizador.domain.model.DashboardCard
import com.organizador.domain.model.DateFormatPref
import com.organizador.domain.model.Density
import com.organizador.domain.model.NavItem
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.StartDestination
import com.organizador.domain.model.TaskSort
import com.organizador.domain.model.ThemeMode
import com.organizador.domain.model.TimeFormatPref
import com.organizador.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    val scheduler: AlarmScheduler,
    val notifications: NotificationHelper,
    private val sync: SyncCoordinator,
) : ViewModel() {
    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settings.update(transform)
        sync.requestSync() // preferências afetam horários de rotinas e avisos
    }
}

private data class Section(val key: String, val title: String, val subtitle: String, val icon: ImageVector)

private val sections = listOf(
    Section("appearance", "Aparência", "Tema, cores, tamanho do texto e densidade", Icons.Outlined.Palette),
    Section("behavior", "Comportamento", "Semana, data e hora, rotinas, exclusões", Icons.Outlined.Tune),
    Section("dashboard", "Painel inicial", "Cartões visíveis e ordem", Icons.Outlined.Dashboard),
    Section("navigation", "Menu e navegação", "Ordem dos menus e tela inicial", Icons.Outlined.Menu),
    Section("notifications", "Notificações", "Lembretes, avisos e horário silencioso", Icons.Outlined.Notifications),
    Section("security", "Segurança e privacidade", "Bloqueio por PIN e biometria", Icons.Outlined.Lock),
    Section("backup", "Backup e restauração", "Exportar, importar e backups preventivos", Icons.Outlined.Backup),
    Section("about", "Sobre", "Versão e armazenamento", Icons.Outlined.Info),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val actions = LocalAppActions.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Configurações") }, navigationIcon = { IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            sections.forEach { s ->
                ListItem(
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(s.subtitle) },
                    leadingContent = { Icon(s.icon, null) },
                    modifier = Modifier.clickable { actions.navigate(Routes.settings(s.key)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(section: String) {
    val actions = LocalAppActions.current
    val title = sections.firstOrNull { it.key == section }?.title ?: "Configurações"
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } })
        when (section) {
            "security" -> SecuritySettings()
            "backup" -> BackupSettings()
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                when (section) {
                    "appearance" -> AppearanceSettings()
                    "behavior" -> BehaviorSettings()
                    "dashboard" -> DashboardSettings()
                    "navigation" -> NavigationSettings()
                    "notifications" -> NotificationSettings()
                    else -> AboutSettings()
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun SwitchLine(label: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, text) -> FilterChip(selected == value, { onSelect(value) }, { Text(text) }) }
    }
}

@Composable
private fun AppearanceSettings() {
    val vm: SettingsViewModel = hiltViewModel()
    val s = LocalAppSettings.current
    ChoiceRow("Tema", listOf(ThemeMode.SYSTEM to "Automático", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro"), s.themeMode) { m -> vm.update { it.copy(themeMode = m) } }
    SectionHeader("Cor de destaque")
    ColorPickerRow(s.accentColor, { c -> vm.update { it.copy(accentColor = c, dynamicColor = false) } }, AppSettings.ACCENTS)
    if (Build.VERSION.SDK_INT >= 31) {
        SwitchLine("Cores do sistema (Material You)", s.dynamicColor, "Usa a paleta do papel de parede") { v -> vm.update { it.copy(dynamicColor = v) } }
    }
    SectionHeader("Tamanho do texto: ${(s.fontScale * 100).toInt()}%")
    Slider(value = s.fontScale, onValueChange = { v -> vm.update { it.copy(fontScale = (Math.round(v * 20) / 20f)) } }, valueRange = 0.85f..1.3f, steps = 8)
    ChoiceRow("Densidade visual", listOf(Density.COMPACT to "Compacta", Density.COMFORTABLE to "Confortável", Density.SPACIOUS to "Espaçosa"), s.density) { d -> vm.update { it.copy(density = d) } }
}

@Composable
private fun BehaviorSettings() {
    val vm: SettingsViewModel = hiltViewModel()
    val s = LocalAppSettings.current
    var pickReset by remember { mutableStateOf(false) }
    ChoiceRow("Primeiro dia da semana", listOf(DayOfWeek.SUNDAY to "Domingo", DayOfWeek.MONDAY to "Segunda", DayOfWeek.SATURDAY to "Sábado"), s.firstDayOfWeek) { d -> vm.update { it.copy(firstDayOfWeek = d) } }
    val sample = LocalDate.of(2026, 12, 31)
    ChoiceRow("Formato de data", DateFormatPref.entries.map { it to Formats.date(sample, it) }, s.dateFormat) { f -> vm.update { it.copy(dateFormat = f) } }
    ChoiceRow("Formato de hora", listOf(TimeFormatPref.H24 to "24 horas", TimeFormatPref.H12 to "12 horas"), s.timeFormat) { f -> vm.update { it.copy(timeFormat = f) } }
    SectionHeader("Rotinas")
    SettingLine("Horário de reinicialização", Formats.time(s.routineResetTime, s.timeFormat)) { pickReset = true }
    Text(
        "Antes deste horário, rotinas e checklists diários ainda contam como o dia anterior (útil para quem dorme tarde).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SectionHeader("Listas")
    ChoiceRow(
        "Ordenação padrão de tarefas",
        listOf(TaskSort.DUE_DATE to "Prazo", TaskSort.PRIORITY to "Prioridade", TaskSort.MANUAL to "Manual", TaskSort.CREATED to "Criação", TaskSort.TITLE to "Título"),
        s.defaultTaskSort,
    ) { v -> vm.update { it.copy(defaultTaskSort = v) } }
    ChoiceRow("Ordenação padrão de notas", listOf(NoteSort.UPDATED to "Modificação", NoteSort.CREATED to "Criação", NoteSort.TITLE to "Título"), s.defaultNoteSort) { v -> vm.update { it.copy(defaultNoteSort = v) } }
    SwitchLine("Confirmar antes de excluir", s.confirmDeletion) { v -> vm.update { it.copy(confirmDeletion = v) } }
    if (pickReset) TimePickerModal(s.routineResetTime, { t -> t?.let { vm.update { s2 -> s2.copy(routineResetTime = it) } } }, { pickReset = false }, allowClear = false)
}

private fun cardLabel(card: DashboardCard) = when (card) {
    DashboardCard.TODAY_TASKS -> "Tarefas de hoje"
    DashboardCard.OVERDUE -> "Tarefas atrasadas"
    DashboardCard.UPCOMING -> "Próximas atividades"
    DashboardCard.CHECKLISTS_TODAY -> "Checklists do dia"
    DashboardCard.ROUTINES -> "Rotinas"
    DashboardCard.RECENT_NOTES -> "Notas recentes"
    DashboardCard.DAILY_PROGRESS -> "Progresso diário"
    DashboardCard.WEEKLY_SUMMARY -> "Resumo semanal"
    DashboardCard.SHORTCUTS -> "Atalhos"
}

@Composable
private fun DashboardSettings() {
    val vm: SettingsViewModel = hiltViewModel()
    val s = LocalAppSettings.current
    Text("Escolha os cartões e a ordem em que aparecem no painel.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
    s.dashboardCards.forEachIndexed { index, config ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cardLabel(config.card), modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.update { it.copy(dashboardCards = it.dashboardCards.moved(index, -1)) } }, enabled = index > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Subir") }
            IconButton(onClick = { vm.update { it.copy(dashboardCards = it.dashboardCards.moved(index, 1)) } }, enabled = index < s.dashboardCards.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "Descer") }
            Switch(checked = config.visible, onCheckedChange = { v -> vm.update { st -> st.copy(dashboardCards = st.dashboardCards.map { if (it.card == config.card) it.copy(visible = v) else it }) } })
        }
    }
}

private fun navLabel(item: NavItem) = when (item) {
    NavItem.DASHBOARD -> "Painel"
    NavItem.TODAY -> "Hoje"
    NavItem.UPCOMING -> "Próximos dias"
    NavItem.INBOX -> "Caixa de entrada"
    NavItem.CHECKLISTS -> "Checklists"
    NavItem.ROUTINES -> "Rotinas"
    NavItem.NOTES -> "Notas"
    NavItem.PROJECTS -> "Projetos"
    NavItem.CALENDAR -> "Calendário"
    NavItem.STATISTICS -> "Estatísticas"
    NavItem.SEARCH -> "Pesquisar"
}

@Composable
private fun NavigationSettings() {
    val vm: SettingsViewModel = hiltViewModel()
    val s = LocalAppSettings.current
    ChoiceRow(
        "Tela inicial",
        listOf(
            StartDestination.DASHBOARD to "Painel", StartDestination.TODAY to "Hoje", StartDestination.INBOX to "Entrada",
            StartDestination.UPCOMING to "Próximos", StartDestination.CHECKLISTS to "Checklists", StartDestination.ROUTINES to "Rotinas",
            StartDestination.NOTES to "Notas", StartDestination.CALENDAR to "Calendário",
        ),
        s.startDestination,
    ) { d -> vm.update { it.copy(startDestination = d) } }
    Text("A tela inicial é aplicada na próxima abertura do aplicativo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionHeader("Ordem dos menus")
    Text("Os 4 primeiros itens (exceto Pesquisar) aparecem na barra inferior.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    s.navOrder.forEachIndexed { index, item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}. ${navLabel(item)}", modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.update { it.copy(navOrder = it.navOrder.moved(index, -1)) } }, enabled = index > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Subir") }
            IconButton(onClick = { vm.update { it.copy(navOrder = it.navOrder.moved(index, 1)) } }, enabled = index < s.navOrder.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "Descer") }
        }
    }
}

@Composable
private fun NotificationSettings() {
    val vm: SettingsViewModel = hiltViewModel()
    val s = LocalAppSettings.current
    val n = s.notifications
    val context = LocalContext.current
    var permission by remember { mutableStateOf(vm.notifications.canPost()) }
    var exact by remember { mutableStateOf(vm.scheduler.canScheduleExact()) }
    var pickSummary by remember { mutableStateOf(false) }
    var pickQuietStart by remember { mutableStateOf(false) }
    var pickQuietEnd by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = vm.notifications.canPost() }

    SectionHeader("Permissões do Android")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (permission) "Notificações permitidas" else "Notificações bloqueadas", modifier = Modifier.weight(1f), color = if (permission) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        if (!permission) {
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Permitir") }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(if (exact) "Alarmes no horário exato: permitidos" else "Alarmes exatos: não permitidos")
            Text(
                if (exact) "Lembretes disparam no minuto definido." else "Sem esta permissão o Android pode atrasar lembretes alguns minutos para economizar bateria.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!exact && Build.VERSION.SDK_INT >= 31) {
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
                exact = vm.scheduler.canScheduleExact()
            }) { Text("Abrir") }
        }
    }

    SectionHeader("Avisos")
    SwitchLine("Notificações do aplicativo", n.enabled) { v -> vm.update { it.copy(notifications = it.notifications.copy(enabled = v)) } }
    SwitchLine("Lembretes de tarefas", n.taskReminders, enabled = n.enabled) { v -> vm.update { it.copy(notifications = it.notifications.copy(taskReminders = v)) } }
    SwitchLine("Avisos de tarefas atrasadas", n.overdueAlerts, "No resumo diário", enabled = n.enabled) { v -> vm.update { it.copy(notifications = it.notifications.copy(overdueAlerts = v)) } }
    SwitchLine("Prazos próximos", n.upcomingDeadlines, "Tarefas de hoje e amanhã no resumo diário", enabled = n.enabled) { v -> vm.update { it.copy(notifications = it.notifications.copy(upcomingDeadlines = v)) } }
    SwitchLine("Avisos de checklists", n.checklistAlerts, "No horário de execução definido", enabled = n.enabled) { v -> vm.update { it.copy(notifications = it.notifications.copy(checklistAlerts = v)) } }
    SwitchLine("Avisos de rotinas", n.routineAlerts, enabled = n.enabled) { v -> vm.update { it.copy(notifications = it.notifications.copy(routineAlerts = v)) } }
    SettingLine("Horário do resumo diário", Formats.time(n.dailySummaryTime, s.timeFormat)) { pickSummary = true }
    Text("Adiar lembretes por", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5, 10, 15, 30, 60).forEach { m ->
            FilterChip(n.defaultSnoozeMinutes == m, { vm.update { it.copy(notifications = it.notifications.copy(defaultSnoozeMinutes = m)) } }, { Text("$m min") })
        }
    }

    SectionHeader("Horário silencioso")
    SwitchLine("Ativar horário silencioso", n.quietHours.enabled, "Lembretes nesse período são adiados para o fim dele") { v ->
        vm.update { it.copy(notifications = it.notifications.copy(quietHours = it.notifications.quietHours.copy(enabled = v))) }
    }
    SettingLine("Início", Formats.time(n.quietHours.start, s.timeFormat)) { pickQuietStart = true }
    SettingLine("Fim", Formats.time(n.quietHours.end, s.timeFormat)) { pickQuietEnd = true }

    if (pickSummary) TimePickerModal(n.dailySummaryTime, { t -> t?.let { vm.update { st -> st.copy(notifications = st.notifications.copy(dailySummaryTime = it)) } } }, { pickSummary = false }, allowClear = false)
    if (pickQuietStart) TimePickerModal(n.quietHours.start, { t -> t?.let { vm.update { st -> st.copy(notifications = st.notifications.copy(quietHours = st.notifications.quietHours.copy(start = it))) } } }, { pickQuietStart = false }, allowClear = false)
    if (pickQuietEnd) TimePickerModal(n.quietHours.end, { t -> t?.let { vm.update { st -> st.copy(notifications = st.notifications.copy(quietHours = st.notifications.quietHours.copy(end = it))) } } }, { pickQuietEnd = false }, allowClear = false)
}

@Composable
private fun AboutSettings() {
    val context = LocalContext.current
    SectionHeader("Organizador ${BuildConfig.VERSION_NAME}")
    Text("Aplicativo de produtividade 100% offline. Não exige conta, não usa internet e não envia dados a servidores.")
    Spacer(Modifier.height(8.dp))
    Text("Seus dados ficam no armazenamento privado do aplicativo:", style = MaterialTheme.typography.bodySmall)
    Text(context.filesDir.parent.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Text("Para não perder dados ao trocar de aparelho, use Backup e restauração.", style = MaterialTheme.typography.bodySmall)
}

fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}
