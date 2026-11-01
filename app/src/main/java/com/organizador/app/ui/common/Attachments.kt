package com.organizador.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.organizador.domain.model.Attachment
import java.io.File

/** Lê nome e tipo de um conteúdo escolhido pelo seletor de arquivos do sistema. */
fun queryFileMeta(context: Context, uri: Uri): Pair<String, String> {
    var name = "anexo"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0)?.let { name = it }
    }
    val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
    return name to type
}

/** Abre um anexo em outro app com permissão de leitura temporária (FileProvider). */
fun openAttachment(context: Context, file: File, mimeType: String): Boolean {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(Intent.createChooser(intent, "Abrir com").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

@Composable
fun AttachmentList(
    attachments: List<Attachment>,
    fileFor: (Attachment) -> File,
    onAdd: (Uri) -> Unit,
    onRemove: (Attachment) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Attachment?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onAdd) }
    Column(modifier) {
        attachments.forEach { a ->
            ListItem(
                headlineContent = { Text(a.fileName, maxLines = 1) },
                supportingContent = { Text(Formats.fileSize(a.sizeBytes)) },
                leadingContent = { Icon(if (a.isImage) Icons.Outlined.Image else Icons.Outlined.AttachFile, null) },
                trailingContent = { IconButton(onClick = { confirm = a }) { Icon(Icons.Outlined.Close, "Remover anexo") } },
                modifier = Modifier.clickable {
                    val file = fileFor(a)
                    if (!file.exists()) onError("Arquivo do anexo não encontrado") else if (!openAttachment(context, file, a.mimeType)) onError("Nenhum app disponível para abrir este arquivo")
                },
            )
        }
        TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
            Icon(Icons.Outlined.AttachFile, null)
            Text(" Adicionar anexo")
        }
    }
    confirm?.let { a ->
        ConfirmDialog("Remover anexo?", "\"${a.fileName}\" será apagado do aplicativo.", "Remover", destructive = true, onConfirm = { onRemove(a) }, onDismiss = { confirm = null })
    }
}

