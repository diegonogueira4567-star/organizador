package com.organizador.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anexos ficam em filesDir/attachments (armazenamento privado do app, inacessível a outros apps).
 * Nomes físicos são UUIDs; o nome original é guardado apenas no banco, evitando path traversal.
 */
@Singleton
class AttachmentStorage @Inject constructor(@ApplicationContext private val context: Context) {
    val root: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun resolve(relativePath: String): File {
        val file = File(root, relativePath).canonicalFile
        if (!file.path.startsWith(root.canonicalPath + File.separator)) throw IOException("Caminho de anexo inválido")
        return file
    }

    /** Copia para um arquivo temporário e renomeia ao final, para não deixar anexos truncados. */
    fun write(name: String, source: InputStream, maxBytes: Long = MAX_BYTES): Long {
        val target = resolve(name)
        val tmp = File(root, "$name.part")
        var total = 0L
        try {
            source.use { input ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IOException("Anexo excede o limite de ${maxBytes / (1024 * 1024)} MB")
                        out.write(buffer, 0, read)
                    }
                }
            }
            if (!tmp.renameTo(target)) throw IOException("Falha ao gravar anexo")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        return total
    }

    fun delete(relativePath: String) {
        runCatching { resolve(relativePath) }.getOrNull()?.delete()
    }

    fun clearAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    companion object {
        const val DIR = "attachments"
        const val MAX_BYTES = 50L * 1024 * 1024
    }
}
