package com.organizador.data.backup

import com.organizador.data.security.Crypto
import com.organizador.data.security.Crypto.hexToBytes
import com.organizador.data.security.Crypto.toHex
import com.organizador.data.security.WrongKeyException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Formato do pacote (ZIP):
 *   data.json            exportação lógica do banco (BackupData)
 *   settings.json        preferências (opcional)
 *   attachments/<id.ext> arquivos anexos (opcional)
 *   manifest.json        versão, contagens, SHA-256 de cada entrada e parâmetros de criptografia
 *
 * Com senha, cada entrada (exceto o manifesto) é cifrada com AES-256-GCM usando o caminho como AAD,
 * o que impede trocar conteúdos entre entradas. Lógica pura de JVM, testável sem Android.
 */
object BackupArchive {
    const val FORMAT = "organizador-backup"
    const val FORMAT_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val DATA = "data.json"
    const val SETTINGS = "settings.json"
    const val ATTACHMENTS_DIR = "attachments/"
    const val KDF = "PBKDF2WithHmacSHA256"
    const val KDF_ITERATIONS = 150_000
    private const val VERIFIER_PLAINTEXT = "organizador-backup-verifier"

    const val MAX_ENTRIES = 20_000
    const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_JSON_BYTES = 256L * 1024 * 1024
    const val MAX_ATTACHMENT_BYTES = 51L * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 8L * 1024 * 1024

    private val attachmentName = Regex("^attachments/[A-Za-z0-9-]{1,64}(\\.[A-Za-z0-9]{1,8})?$")

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    class Entry(val path: String, val content: () -> ByteArray)

    data class ReadResult(
        val manifest: BackupManifest,
        /** null quando cifrado e nenhuma senha foi informada (apenas inspeção). */
        val data: BackupData?,
        val settings: Map<String, String>?,
        /** caminho relativo do anexo -> arquivo já decifrado na área temporária. */
        val attachments: Map<String, File>,
    )

    fun write(out: OutputStream, header: BackupManifest, entries: List<Entry>, password: CharArray?): BackupManifest {
        val salt = password?.let { Crypto.randomBytes(16) }
        val key = password?.let { Crypto.pbkdf2(it, salt!!, KDF_ITERATIONS) }
        try {
            ZipOutputStream(out.buffered()).use { zip ->
                val files = entries.map { entry ->
                    require(isAllowedName(entry.path)) { "Nome de entrada inválido: ${entry.path}" }
                    val raw = entry.content()
                    val stored = key?.let { Crypto.encrypt(it, raw, entry.path.toByteArray()) } ?: raw
                    zip.putNextEntry(ZipEntry(entry.path))
                    zip.write(stored)
                    zip.closeEntry()
                    ManifestFile(entry.path, stored.size.toLong(), Crypto.sha256(stored))
                }
                val manifest = header.copy(
                    format = FORMAT,
                    formatVersion = FORMAT_VERSION,
                    encrypted = key != null,
                    kdf = key?.let { KDF },
                    kdfIterations = key?.let { KDF_ITERATIONS },
                    kdfSalt = salt?.toHex(),
                    verifier = key?.let { Crypto.encrypt(it, VERIFIER_PLAINTEXT.toByteArray(), MANIFEST.toByteArray()).toHex() },
                    files = files,
                )
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
                return manifest
            }
        } finally {
            key?.fill(0)
        }
    }

    /**
     * Lê e valida todo o pacote. Nenhum dado do usuário é alterado aqui.
     * @param staging se informado, anexos são gravados (decifrados) nesse diretório; senão apenas verificados.
     */
    fun read(input: InputStream, password: CharArray?, staging: File?): ReadResult {
        val seen = HashMap<String, Pair<Long, String>>() // caminho -> (tamanho, sha256)
        var manifestBytes: ByteArray? = null
        var dataBytes: ByteArray? = null
        var settingsBytes: ByteArray? = null
        val stagedRaw = HashMap<String, File>()
        var total = 0L

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory) throw BackupException.Malicious("diretório inesperado '$name'")
                if (!isAllowedName(name)) throw BackupException.Malicious("entrada não permitida '$name'")
                if (name in seen || (name == MANIFEST && manifestBytes != null)) throw BackupException.Malicious("entrada duplicada '$name'")
                if (seen.size >= MAX_ENTRIES) throw BackupException.Malicious("número excessivo de entradas")
                val limit = when {
                    name == MANIFEST -> MAX_MANIFEST_BYTES
                    name.startsWith(ATTACHMENTS_DIR) -> MAX_ATTACHMENT_BYTES
                    else -> MAX_JSON_BYTES
                }
                if (name.startsWith(ATTACHMENTS_DIR)) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val target = staging?.let { File(it, name.removePrefix(ATTACHMENTS_DIR) + ".raw") }
                    val size = copyLimited(zip, limit, digest, target?.outputStream())
                    total += size
                    seen[name] = size to digest.digest().toHex()
                    target?.let { stagedRaw[name] = it }
                } else {
                    val bytes = readLimited(zip, limit)
                    total += bytes.size
                    when (name) {
                        MANIFEST -> manifestBytes = bytes
                        DATA -> { dataBytes = bytes; seen[name] = bytes.size.toLong() to Crypto.sha256(bytes) }
                        SETTINGS -> { settingsBytes = bytes; seen[name] = bytes.size.toLong() to Crypto.sha256(bytes) }
                    }
                }
                if (total > MAX_TOTAL_BYTES) throw BackupException.Malicious("tamanho total excessivo")
            }
        }

        val manifest = try {
            json.decodeFromString(BackupManifest.serializer(), manifestBytes?.decodeToString() ?: throw BackupException.NotABackup())
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException.NotABackup()
        }
        if (manifest.format != FORMAT) throw BackupException.NotABackup()
        if (manifest.formatVersion > FORMAT_VERSION) throw BackupException.Incompatible("formato ${manifest.formatVersion} é mais novo que este aplicativo")

        // Integridade: tudo que o manifesto lista existe com o mesmo hash, e nada além disso.
        val listed = manifest.files.associateBy { it.path }
        for ((path, info) in seen) {
            val expected = listed[path] ?: throw BackupException.Corrupted("arquivo não listado no manifesto: $path")
            if (expected.size != info.first || !expected.sha256.equals(info.second, ignoreCase = true)) {
                throw BackupException.Corrupted("verificação de integridade falhou em $path")
            }
        }
        listed.keys.firstOrNull { it !in seen }?.let { throw BackupException.Corrupted("arquivo ausente: $it") }
        if (DATA !in seen) throw BackupException.Corrupted("dados ausentes")

        val key: ByteArray? = if (manifest.encrypted) {
            if (password == null) {
                return ReadResult(manifest, null, null, emptyMap())
            }
            val salt = manifest.kdfSalt?.hexToBytes() ?: throw BackupException.Corrupted("parâmetros de criptografia ausentes")
            val derived = Crypto.pbkdf2(password, salt, manifest.kdfIterations ?: KDF_ITERATIONS)
            val verifier = manifest.verifier?.hexToBytes() ?: throw BackupException.Corrupted("verificador ausente")
            try {
                Crypto.decrypt(derived, verifier, MANIFEST.toByteArray())
            } catch (e: WrongKeyException) {
                throw BackupException.WrongPassword()
            }
            derived
        } else {
            null
        }

        try {
            fun open(path: String, bytes: ByteArray): ByteArray = if (key == null) bytes else try {
                Crypto.decrypt(key, bytes, path.toByteArray())
            } catch (e: WrongKeyException) {
                throw BackupException.Corrupted("conteúdo adulterado em $path")
            }

            val data = try {
                json.decodeFromString(BackupData.serializer(), open(DATA, dataBytes!!).decodeToString())
            } catch (e: BackupException) {
                throw e
            } catch (e: Exception) {
                throw BackupException.Incompatible("não foi possível ler os dados (${e.javaClass.simpleName})")
            }
            val settings = settingsBytes?.let { bytes ->
                runCatching {
                    json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), open(SETTINGS, bytes).decodeToString())
                }.getOrElse { throw BackupException.Corrupted("preferências ilegíveis") }
            }
            val attachments = stagedRaw.mapValues { (path, raw) ->
                val plain = File(raw.parentFile, raw.name.removeSuffix(".raw"))
                if (key == null) {
                    raw.renameTo(plain)
                } else {
                    plain.writeBytes(open(path, raw.readBytes()))
                    raw.delete()
                }
                plain
            }
            return ReadResult(manifest, data, settings, attachments)
        } finally {
            key?.fill(0)
        }
    }

    fun isAllowedName(name: String): Boolean =
        name == MANIFEST || name == DATA || name == SETTINGS || attachmentName.matches(name)

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        copyLimited(input, limit, null, out)
        return out.toByteArray()
    }

    private fun copyLimited(input: InputStream, limit: Long, digest: MessageDigest?, out: OutputStream?): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                // Protege contra "zip bombs": o limite vale para o conteúdo descompactado.
                if (total > limit) throw BackupException.Malicious("entrada excede o tamanho permitido")
                digest?.update(buffer, 0, read)
                out?.write(buffer, 0, read)
            }
        } finally {
            if (out !is ByteArrayOutputStream) out?.close()
        }
        return total
    }
}
