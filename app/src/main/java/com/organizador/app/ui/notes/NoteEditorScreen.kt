package com.organizador.app.ui.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle as ComposeSpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.ConfirmDialog
import com.organizador.app.ui.common.openAttachment
import com.organizador.app.ui.navigation.Routes
import com.organizador.domain.model.Attachment
import com.organizador.domain.model.ChecklistSummary
import com.organizador.domain.model.NoteDraft
import com.organizador.domain.note.BlockType
import com.organizador.domain.note.SpanStyle
import com.organizador.domain.note.TextSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val highlightColors = listOf("#FFF59D", "#C8E6C9", "#BBDEFB", "#F8BBD0", "#FFE0B2")

/** Desenha os spans do documento sobre o texto puro sem alterar posições (OffsetMapping identidade). */
private class SpanTransformation(private val spans: List<TextSpan>, private val linkColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        val length = text.text.length
        spans.forEach { span ->
            val start = span.start.coerceIn(0, length)
            val end = span.end.coerceIn(start, length)
            if (end <= start) return@forEach
            val style = when (span.style) {
                SpanStyle.BOLD -> ComposeSpanStyle(fontWeight = FontWeight.Bold)
                SpanStyle.ITALIC -> ComposeSpanStyle(fontStyle = FontStyle.Italic)
                SpanStyle.UNDERLINE -> ComposeSpanStyle(textDecoration = TextDecoration.Underline)
                SpanStyle.STRIKE -> ComposeSpanStyle(textDecoration = TextDecoration.LineThrough)
                SpanStyle.HIGHLIGHT -> ComposeSpanStyle(background = parseColor(span.value) ?: Color(0xFFFFF59D), color = Color(0xFF202020))
                SpanStyle.LINK -> ComposeSpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            }
            builder.addStyle(style, start, end)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is SpanTransformation && other.spans == spans && other.linkColor == linkColor
    override fun hashCode() = spans.hashCode() * 31 + linkColor.hashCode()
}

private fun parseColor(hex: String?): Color? = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/** Miniatura com amostragem (inSampleSize) para não carregar imagens grandes inteiras na memória. */
@Composable
private fun rememberThumbnail(file: File?, maxWidth: Int = 1080): Bitmap? {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) {
            if (file == null || !file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: String?, folderId: String?) {
    val vm: NoteEditorViewModel = hiltViewModel()
    LaunchedEffect(noteId) { vm.load(noteId, folderId) }
    val actions = LocalAppActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateValue by vm.state.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val links by vm.links.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val noteTags by vm.noteTags.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val checklists by vm.checklists.collectAsStateWithLifecycle()
    val allNotes by vm.allNotes.collectAsStateWithLifecycle()
    val focusRequesters = remember { HashMap<String, FocusRequester>() }

    var menu by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<NoteDraft?>(null) }
    var conflictCopy by remember { mutableStateOf<String?>(null) }
    var linkDialog by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var folderSheet by remember { mutableStateOf(false) }
    var tagSheet by remember { mutableStateOf(false) }
    var projectSheet by remember { mutableStateOf(false) }
    var checklistSheet by remember { mutableStateOf(false) }
    var noteRefSheet by remember { mutableStateOf(false) }
    var linksSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.addAttachment(it, asImage = true) } }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.addAttachment(it, asImage = false) } }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is NoteEvent.Message -> actions.showMessage(e.text, null, null)
                is NoteEvent.Conflict -> conflictCopy = e.copyId
                is NoteEvent.DraftAvailable -> draft = e.draft
                is NoteEvent.OpenTask -> actions.navigate(Routes.task(e.taskId))
                NoteEvent.Closed -> actions.back()
            }
        }
    }
    // Ao sair do app (ON_STOP) o conteúdo pendente é gravado imediatamente.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) scope.launch { vm.flush() } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = stateValue ?: return
    LaunchedEffect(state.focused, state.blocks.size) {
        state.blocks.getOrNull(state.focused)?.let { block -> focusRequesters[block.id]?.let { runCatching { it.requestFocus() } } }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = {
                Text(
                    when (state.status) {
                        SaveStatus.SAVED -> "Salvo"
                        SaveStatus.PENDING, SaveStatus.SAVING -> "Salvando…"
                        SaveStatus.ERROR -> "Erro ao salvar"
                    },
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            navigationIcon = { IconButton(onClick = { scope.launch { vm.flush(); actions.back() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = {
                IconButton(onClick = { vm.toggleFavorite() }) {
                    Icon(if (state.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, "Favorita", tint = if (state.favorite) Color(0xFFF2B01E) else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mais") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(if (state.pinned) "Desafixar" else "Fixar no topo") }, onClick = { menu = false; vm.togglePinned() })
                    DropdownMenuItem(text = { Text("Mover para pasta") }, onClick = { menu = false; folderSheet = true })
                    DropdownMenuItem(text = { Text("Etiquetas") }, onClick = { menu = false; tagSheet = true })
                    DropdownMenuItem(text = { Text("Vincular a projeto") }, onClick = { menu = false; projectSheet = true })
                    DropdownMenuItem(text = { Text("Vínculos e referências") }, onClick = { menu = false; linksSheet = true })
                    DropdownMenuItem(text = { Text("Transformar em tarefa") }, onClick = { menu = false; vm.convertNoteToTask() })
                    DropdownMenuItem(text = { Text("Itens da lista → tarefas") }, onClick = { menu = false; vm.convertItemsToTasks() })
                    DropdownMenuItem(text = { Text(if (state.archived) "Desarquivar" else "Arquivar") }, onClick = { menu = false; vm.toggleArchived() })
                    if (state.deleted) {
                        DropdownMenuItem(text = { Text("Restaurar da lixeira") }, onClick = { menu = false; vm.restore() })
                    } else {
                        DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            item(key = "title") {
                BasicTextField(
                    value = state.title,
                    onValueChange = vm::setTitle,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box { if (state.title.isEmpty()) Text("Título", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                if (noteTags.isNotEmpty()) {
                    Text(noteTags.joinToString("  ") { "#${it.name}" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            itemsIndexed(state.blocks, key = { _, b -> b.id }) { index, block ->
                val requester = focusRequesters.getOrPut(block.id) { FocusRequester() }
                BlockView(
                    index = index,
                    block = block,
                    numberedIndex = numberFor(state.blocks, index),
                    requester = requester,
                    attachment = block.attachmentId?.let { id -> attachments.firstOrNull { it.id == id } },
                    attachmentFile = vm::attachmentFile,
                    checklist = block.refId?.let { id -> checklists.firstOrNull { it.template.id == id } },
                    refNoteTitle = block.refId?.let { id -> allNotes.firstOrNull { it.id == id }?.title?.ifBlank { "Sem título" } },
                    vm = vm,
                    onOpenRoute = { scope.launch { vm.flush(); actions.navigate(it) } },
                    onError = { actions.showMessage(it, null, null) },
                )
            }
            item(key = "end") {
                // Toque no espaço final cria/foca o último parágrafo.
                Spacer(Modifier.fillMaxWidth().height(160.dp).clickable { vm.focus(state.blocks.lastIndex) })
            }
        }
        EditorToolbar(
            vm = vm,
            onLink = { linkDialog = true },
            onHighlight = { colorMenu = true },
            onImage = { imagePicker.launch("image/*") },
            onFile = { filePicker.launch(arrayOf("*/*")) },
            onChecklist = { checklistSheet = true },
            onNoteRef = { noteRefSheet = true },
            onOpenLink = {
                vm.linkAtCursor()?.let { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { actions.showMessage("Não foi possível abrir o link", null, null) }
                }
            },
        )
        DropdownMenu(colorMenu, { colorMenu = false }) {
            highlightColors.forEach { hex ->
                DropdownMenuItem(
                    text = { Box(Modifier.size(width = 80.dp, height = 20.dp).background(parseColor(hex)!!, RoundedCornerShape(4.dp))) },
                    onClick = { colorMenu = false; vm.applyStyle(SpanStyle.HIGHLIGHT, hex) },
                )
            }
            DropdownMenuItem(text = { Text("Remover destaque") }, onClick = { colorMenu = false; vm.applyStyle(SpanStyle.HIGHLIGHT, null) })
        }
    }

    draft?.let { d ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Recuperar alterações?") },
            text = { Text("Encontramos alterações desta nota que não foram salvas (o app pode ter sido fechado inesperadamente). Deseja recuperá-las?") },
            confirmButton = { TextButton(onClick = { vm.recoverDraft(d); draft = null }) { Text("Recuperar") } },
            dismissButton = { TextButton(onClick = { vm.discardDraft(); draft = null }) { Text("Descartar") } },
        )
    }
    conflictCopy?.let { copy ->
        AlertDialog(
            onDismissRequest = { conflictCopy = null },
            title = { Text("Conflito de edição") },
            text = { Text("Esta nota foi alterada em outro lugar enquanto você editava. Nada foi sobrescrito: suas alterações foram salvas em uma cópia.") },
            confirmButton = { TextButton(onClick = { conflictCopy = null; actions.navigate(Routes.note(copy)) }) { Text("Abrir cópia") } },
            dismissButton = { TextButton(onClick = { conflictCopy = null }) { Text("Fechar") } },
        )
    }
    if (linkDialog) {
        var url by remember { mutableStateOf(vm.linkAtCursor() ?: "https://") }
        AlertDialog(
            onDismissRequest = { linkDialog = false },
            title = { Text("Link") },
            text = { OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Endereço") }) },
            confirmButton = { TextButton(onClick = { vm.applyStyle(SpanStyle.LINK, url.trim()); linkDialog = false }) { Text("Aplicar") } },
            dismissButton = { TextButton(onClick = { vm.applyStyle(SpanStyle.LINK, null); linkDialog = false }) { Text("Remover link") } },
        )
    }
    if (folderSheet) {
        PickerSheet("Mover para pasta", onDismiss = { folderSheet = false }) {
            ListItem(headlineContent = { Text("Sem pasta") }, modifier = Modifier.clickable { vm.moveToFolder(null); folderSheet = false })
            folders.forEach { f ->
                ListItem(headlineContent = { Text(folderPath(f.id, folders)) }, modifier = Modifier.clickable { vm.moveToFolder(f.id); folderSheet = false })
            }
        }
    }
    if (tagSheet) {
        var selected by remember { mutableStateOf(noteTags.map { it.id }.toSet()) }
        PickerSheet("Etiquetas", onDismiss = { vm.setTags(selected); tagSheet = false }) {
            if (tags.isEmpty()) Text("Crie etiquetas em Etiquetas e categorias.", modifier = Modifier.padding(16.dp))
            tags.forEach { t ->
                ListItem(
                    headlineContent = { Text("#${t.name}") },
                    leadingContent = { Checkbox(t.id in selected, null) },
                    modifier = Modifier.clickable { selected = if (t.id in selected) selected - t.id else selected + t.id },
                )
            }
            TextButton(onClick = { vm.setTags(selected); tagSheet = false }, modifier = Modifier.padding(16.dp)) { Text("Aplicar") }
        }
    }
    if (projectSheet) {
        PickerSheet("Vincular a projeto", onDismiss = { projectSheet = false }) {
            ListItem(headlineContent = { Text("Sem projeto") }, modifier = Modifier.clickable { vm.setProject(null); projectSheet = false })
            projects.forEach { p -> ListItem(headlineContent = { Text(p.name) }, modifier = Modifier.clickable { vm.setProject(p.id); projectSheet = false }) }
        }
    }
    if (checklistSheet) {
        PickerSheet("Inserir checklist", onDismiss = { checklistSheet = false }) {
            if (checklists.isEmpty()) Text("Nenhum checklist criado ainda.", modifier = Modifier.padding(16.dp))
            checklists.forEach { c -> ListItem(headlineContent = { Text(c.template.title) }, modifier = Modifier.clickable { vm.insertChecklistRef(c.template.id); checklistSheet = false }) }
        }
    }
    if (noteRefSheet) {
        PickerSheet("Referenciar nota", onDismiss = { noteRefSheet = false }) {
            allNotes.filter { it.id != state.noteId }.forEach { n ->
                ListItem(headlineContent = { Text(n.title.ifBlank { "Sem título" }) }, modifier = Modifier.clickable { vm.insertNoteRef(n.id); noteRefSheet = false })
            }
        }
    }
    if (linksSheet) {
        PickerSheet("Vínculos", onDismiss = { linksSheet = false }) {
            if (links.isEmpty()) Text("Nenhum vínculo. Converta itens em tarefas ou referencie outras notas.", modifier = Modifier.padding(16.dp))
            links.forEach { l ->
                val other = if (l.fromId == state.noteId) l.toId else l.fromId
                val incoming = l.toId == state.noteId
                val label = when (l.type) {
                    com.organizador.domain.model.LinkType.NOTE_TASK -> "Tarefa vinculada"
                    com.organizador.domain.model.LinkType.NOTE_NOTE -> if (incoming) "Referenciada pela nota: " + (allNotes.firstOrNull { it.id == other }?.title ?: "") else "Referencia a nota: " + (allNotes.firstOrNull { it.id == other }?.title ?: "")
                    com.organizador.domain.model.LinkType.NOTE_CHECKLIST -> "Checklist"
                }
                ListItem(
                    headlineContent = { Text(label) },
                    modifier = Modifier.clickable {
                        linksSheet = false
                        when (l.type) {
                            com.organizador.domain.model.LinkType.NOTE_TASK -> actions.navigate(Routes.task(other))
                            com.organizador.domain.model.LinkType.NOTE_NOTE -> actions.navigate(Routes.note(other))
                            com.organizador.domain.model.LinkType.NOTE_CHECKLIST -> actions.navigate(Routes.checklistRun(other))
                        }
                    },
                )
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Excluir nota?", "A nota irá para a lixeira e poderá ser recuperada.", "Excluir", destructive = true, onConfirm = { vm.delete() }, onDismiss = { confirmDelete = false })
    }
}

fun folderPath(id: String?, folders: List<com.organizador.domain.model.NoteFolder>): String {
    val parts = ArrayList<String>()
    var current = folders.firstOrNull { it.id == id }
    var guard = 0
    while (current != null && guard++ < 32) {
        parts.add(0, current.name)
        current = folders.firstOrNull { it.id == current!!.parentId }
    }
    return parts.joinToString(" / ")
}

private fun numberFor(blocks: List<EditorBlock>, index: Int): Int {
    var n = 1
    var i = index - 1
    while (i >= 0 && blocks[i].type == BlockType.NUMBERED) { n++; i-- }
    return n
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Column(Modifier.heightIn(max = 480.dp).padding(bottom = 24.dp)) {
            LazyColumn { item { content() } }
        }
    }
}

@Composable
private fun BlockView(
    index: Int,
    block: EditorBlock,
    numberedIndex: Int,
    requester: FocusRequester,
    attachment: Attachment?,
    attachmentFile: (Attachment) -> File,
    checklist: ChecklistSummary?,
    refNoteTitle: String?,
    vm: NoteEditorViewModel,
    onOpenRoute: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    when (block.type) {
        BlockType.DIVIDER -> RemovableRow(onRemove = { vm.removeBlock(index) }) { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        BlockType.IMAGE -> RemovableRow(onRemove = { vm.removeBlock(index) }) {
            val file = attachment?.let(attachmentFile)
            val bitmap = rememberThumbnail(file)
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(), contentDescription = attachment?.fileName, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                        val a = attachment ?: return@clickable
                        if (!openAttachment(context, attachmentFile(a), a.mimeType)) onError("Nenhum app para abrir a imagem")
                    },
                )
            } else {
                Text("Imagem indisponível", color = colors.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
        }
        BlockType.ATTACHMENT -> RemovableRow(onRemove = { vm.removeBlock(index) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                    val a = attachment ?: return@clickable onError("Anexo não encontrado")
                    if (!openAttachment(context, attachmentFile(a), a.mimeType)) onError("Nenhum app para abrir este arquivo")
                },
            ) {
                Icon(Icons.Outlined.AttachFile, null)
                Spacer(Modifier.width(8.dp))
                Text(attachment?.fileName ?: block.value.text, color = colors.primary)
            }
        }
        BlockType.TABLE -> RemovableRow(onRemove = { vm.removeBlock(index) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                val rows = block.rows.orEmpty()
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    rows.forEachIndexed { r, row ->
                        Row {
                            row.forEachIndexed { c, cell ->
                                BasicTextField(
                                    value = cell, onValueChange = { vm.editCell(index, r, c, it) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface, fontWeight = if (r == 0) FontWeight.SemiBold else null),
                                    cursorBrush = SolidColor(colors.primary),
                                    modifier = Modifier.width(120.dp).background(if (r == 0) colors.surfaceVariant else colors.surface).padding(6.dp),
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Row {
                    TextButton(onClick = { vm.addRow(index) }) { Text("+ linha") }
                    TextButton(onClick = { vm.addColumn(index) }) { Text("+ coluna") }
                    TextButton(onClick = { vm.removeRow(index) }) { Text("− linha") }
                }
            }
        }
        BlockType.CHECKLIST_REF -> RemovableRow(onRemove = { vm.removeBlock(index) }) {
            Surface(
                shape = MaterialTheme.shapes.medium, color = colors.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { block.refId?.let { onOpenRoute(Routes.checklistRun(it)) } },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Checklist, null, tint = colors.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(checklist?.template?.title ?: "Checklist removido", style = MaterialTheme.typography.titleSmall)
                    }
                    checklist?.current?.let {
                        LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text("${it.checkedItems}/${it.totalItems} concluídos", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        BlockType.NOTE_REF -> RemovableRow(onRemove = { vm.removeBlock(index) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { block.refId?.let { onOpenRoute(Routes.note(it)) } },
            ) {
                Icon(Icons.AutoMirrored.Outlined.EventNote, null, tint = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text(refNoteTitle ?: "Nota removida", color = colors.primary, textDecoration = TextDecoration.Underline)
            }
        }
        else -> TextBlock(index, block, numberedIndex, requester, vm, onOpenRoute)
    }
}

@Composable
private fun RemovableRow(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { content() }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remover bloco", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TextBlock(index: Int, block: EditorBlock, number: Int, requester: FocusRequester, vm: NoteEditorViewModel, onOpenRoute: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val base: TextStyle = when (block.type) {
        BlockType.HEADING1 -> typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        BlockType.HEADING2 -> typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        BlockType.CODE -> typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        BlockType.QUOTE -> typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
        else -> typography.bodyLarge
    }
    val textColor = if (block.type == BlockType.TODO && block.checked) colors.onSurfaceVariant else colors.onSurface
    val style = base.copy(color = textColor, textDecoration = if (block.type == BlockType.TODO && block.checked) TextDecoration.LineThrough else null)
    val transformation = remember(block.spans, colors.primary) { SpanTransformation(block.spans, colors.primary) }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        when (block.type) {
            BlockType.BULLET -> Text("•", style = style, modifier = Modifier.padding(end = 8.dp, top = 2.dp))
            BlockType.NUMBERED -> Text("$number.", style = style, modifier = Modifier.padding(end = 8.dp, top = 2.dp))
            BlockType.TODO -> Checkbox(checked = block.checked, onCheckedChange = { vm.toggleChecked(index) }, modifier = Modifier.size(32.dp))
            BlockType.QUOTE -> Box(Modifier.width(3.dp).height(24.dp).background(colors.primary))
            else -> Unit
        }
        if (block.type == BlockType.QUOTE) Spacer(Modifier.width(8.dp))
        val fieldModifier = Modifier
            .weight(1f)
            .padding(vertical = if (block.type == BlockType.HEADING1) 8.dp else 4.dp)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) vm.focus(index) }
            .onPreviewKeyEvent { event ->
                // Backspace com o cursor no início junta o bloco ao anterior.
                if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && block.value.selection.collapsed && block.value.selection.start == 0) {
                    vm.mergeWithPrevious(index)
                } else {
                    false
                }
            }
        val field = @Composable {
            BasicTextField(
                value = block.value,
                onValueChange = { vm.updateText(index, it) },
                textStyle = style,
                visualTransformation = transformation,
                cursorBrush = SolidColor(colors.primary),
                modifier = fieldModifier,
            )
        }
        if (block.type == BlockType.CODE) {
            Box(Modifier.weight(1f).background(colors.surfaceVariant, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp)) {
                BasicTextField(
                    value = block.value, onValueChange = { vm.updateText(index, it) }, textStyle = style,
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).focusRequester(requester).onFocusChanged { if (it.isFocused) vm.focus(index) },
                )
            }
        } else {
            field()
        }
        block.taskId?.let { taskId ->
            IconButton(onClick = { onOpenRoute(Routes.task(taskId)) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.OpenInNew, "Abrir tarefa vinculada", tint = colors.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    vm: NoteEditorViewModel,
    onLink: () -> Unit,
    onHighlight: () -> Unit,
    onImage: () -> Unit,
    onFile: () -> Unit,
    onChecklist: () -> Unit,
    onNoteRef: () -> Unit,
    onOpenLink: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            ToolButton(Icons.Outlined.Title, "Título") { vm.setType(BlockType.HEADING1) }
            ToolButton(Icons.Outlined.Title, "Subtítulo", small = true) { vm.setType(BlockType.HEADING2) }
            ToolButton(Icons.Outlined.FormatBold, "Negrito") { vm.applyStyle(SpanStyle.BOLD) }
            ToolButton(Icons.Outlined.FormatItalic, "Itálico") { vm.applyStyle(SpanStyle.ITALIC) }
            ToolButton(Icons.Outlined.FormatUnderlined, "Sublinhado") { vm.applyStyle(SpanStyle.UNDERLINE) }
            ToolButton(Icons.Outlined.FormatStrikethrough, "Tachado") { vm.applyStyle(SpanStyle.STRIKE) }
            ToolButton(Icons.Outlined.FormatColorFill, "Destaque", onClick = onHighlight)
            ToolButton(Icons.Outlined.Link, "Link", onClick = onLink)
            ToolButton(Icons.Outlined.OpenInNew, "Abrir link", onClick = onOpenLink)
            ToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Lista") { vm.setType(BlockType.BULLET) }
            ToolButton(Icons.Outlined.FormatListNumbered, "Lista numerada") { vm.setType(BlockType.NUMBERED) }
            ToolButton(Icons.Outlined.CheckBox, "Caixa de seleção") { vm.setType(BlockType.TODO) }
            ToolButton(Icons.Outlined.FormatQuote, "Citação") { vm.setType(BlockType.QUOTE) }
            ToolButton(Icons.Outlined.Code, "Código") { vm.setType(BlockType.CODE) }
            ToolButton(Icons.Outlined.HorizontalRule, "Divisória") { vm.insertDivider() }
            ToolButton(Icons.Outlined.TableChart, "Tabela") { vm.insertTable() }
            ToolButton(Icons.Outlined.Image, "Imagem", onClick = onImage)
            ToolButton(Icons.Outlined.AttachFile, "Anexo", onClick = onFile)
            ToolButton(Icons.Outlined.Checklist, "Checklist", onClick = onChecklist)
            ToolButton(Icons.AutoMirrored.Outlined.EventNote, "Referenciar nota", onClick = onNoteRef)
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, small: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label, modifier = Modifier.size(if (small) 18.dp else 24.dp)) }
}
