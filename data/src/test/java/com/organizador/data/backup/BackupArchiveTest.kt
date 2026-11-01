package com.organizador.data.backup

import com.organizador.data.local.entity.ProjectEntity
import com.organizador.data.local.entity.TaskEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupArchiveTest {
    private val data = BackupData(
        projects = listOf(ProjectEntity("p1", "Casa", "", 1L, "folder", 0, false, 1, 1)),
        tasks = listOf(TaskEntity("t1", "Comprar pão", "", "", 20000, null, 4, "p1", null, "OPEN", false, null, null, null, 0, 1, 1, null)),
    )
    private val attachment = "attachments/0f1e2d3c-aaaa-bbbb-cccc-000000000001.png"
    private val attachmentBytes = ByteArray(5000) { (it % 251).toByte() }

    private fun header() = BackupManifest(appVersion = "1.0", schemaVersion = 1, createdAt = 0, sections = BackupSection.entries, counts = data.counts())

    private fun build(password: String?): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchive.write(
            out, header(),
            listOf(
                BackupArchive.Entry(BackupArchive.DATA) { BackupArchive.json.encodeToString(BackupData.serializer(), data).toByteArray() },
                BackupArchive.Entry(attachment) { attachmentBytes },
            ),
            password?.toCharArray(),
        )
        return out.toByteArray()
    }

    private fun staging(): File = Files.createTempDirectory("staging").toFile()

    @Test
    fun `backup sem senha ida e volta`() {
        val dir = staging()
        val result = BackupArchive.read(ByteArrayInputStream(build(null)), null, dir)
        assertEquals(data, result.data)
        assertArrayEquals(attachmentBytes, result.attachments.getValue(attachment).readBytes())
        dir.deleteRecursively()
    }

    @Test
    fun `backup com senha ida e volta e conteudo realmente cifrado`() {
        val zip = build("senha forte")
        // O texto da tarefa não pode aparecer em claro dentro do pacote.
        assertTrue(!String(unzipAll(zip).getValue(BackupArchive.DATA)).contains("Comprar"))
        val dir = staging()
        val result = BackupArchive.read(ByteArrayInputStream(zip), "senha forte".toCharArray(), dir)
        assertEquals(data, result.data)
        assertArrayEquals(attachmentBytes, result.attachments.getValue(attachment).readBytes())
        dir.deleteRecursively()
    }

    @Test
    fun `inspecao de backup cifrado sem senha retorna apenas manifesto`() {
        val result = BackupArchive.read(ByteArrayInputStream(build("x")), null, null)
        assertNull(result.data)
        assertTrue(result.manifest.encrypted)
        assertEquals(1, result.manifest.counts["tarefas"])
    }

    @Test(expected = BackupException.WrongPassword::class)
    fun `senha errada e detectada antes de restaurar`() {
        BackupArchive.read(ByteArrayInputStream(build("certa")), "errada".toCharArray(), null)
    }

    @Test
    fun `entrada adulterada falha na verificacao de integridade`() {
        val entries = unzipAll(build(null)).toMutableMap()
        val tampered = entries.getValue(BackupArchive.DATA).copyOf()
        tampered[10] = (tampered[10] + 1).toByte()
        entries[BackupArchive.DATA] = tampered
        expect<BackupException.Corrupted> { BackupArchive.read(ByteArrayInputStream(rezip(entries)), null, null) }
    }

    @Test
    fun `arquivo ausente e detectado`() {
        val entries = unzipAll(build(null)).toMutableMap()
        entries.remove(attachment)
        expect<BackupException.Corrupted> { BackupArchive.read(ByteArrayInputStream(rezip(entries)), null, null) }
    }

    @Test
    fun `entradas com path traversal sao rejeitadas`() {
        listOf("../../etc/passwd", "/data/data/x", "attachments/../../shared_prefs/x.xml", "attachments/sub/dir.png", "outro.txt").forEach { name ->
            val entries = unzipAll(build(null)).toMutableMap()
            entries[name] = "x".toByteArray()
            expect<BackupException.Malicious> { BackupArchive.read(ByteArrayInputStream(rezip(entries)), null, null) }
        }
    }

    @Test
    fun `arquivo que nao e backup e recusado`() {
        expect<BackupException.NotABackup> { BackupArchive.read(ByteArrayInputStream(rezip(mapOf("data.json" to "{}".toByteArray()))), null, null) }
        expect<BackupException> { BackupArchive.read(ByteArrayInputStream("não é zip".toByteArray()), null, null) }
    }

    @Test
    fun `formato futuro e recusado`() {
        val entries = unzipAll(build(null)).toMutableMap()
        val manifest = String(entries.getValue(BackupArchive.MANIFEST)).replace("\"formatVersion\":1", "\"formatVersion\":99")
        entries[BackupArchive.MANIFEST] = manifest.toByteArray()
        expect<BackupException.Incompatible> { BackupArchive.read(ByteArrayInputStream(rezip(entries)), null, null) }
    }

    private inline fun <reified T : Throwable> expect(block: () -> Unit) {
        try {
            block()
            fail("esperava ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) throw AssertionError("esperava ${T::class.simpleName}, obteve $e", e)
        }
    }

    private fun unzipAll(bytes: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                map[e.name] = zip.readBytes()
            }
        }
        return map
    }

    private fun rezip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
