package com.organizador.domain.note

import com.organizador.domain.model.Ids
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Formato interno das notas: documento de blocos estruturados, versionado. Não usa HTML.
 * Versão 1: lista de blocos com texto + intervalos de estilo (spans).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NoteDocument(
    // Sempre gravada: sem ela o documento seria interpretado como formato legado (BUG-001).
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) @SerialName("v") val version: Int = CURRENT_VERSION,
    val blocks: List<NoteBlock> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        fun empty(): NoteDocument = NoteDocument(blocks = listOf(NoteBlock(Ids.new(), BlockType.PARAGRAPH)))
    }
}

@Serializable
enum class BlockType {
    PARAGRAPH, HEADING1, HEADING2, BULLET, NUMBERED, TODO, QUOTE, CODE, DIVIDER, IMAGE, ATTACHMENT, TABLE,

    /** Referência a um checklist inserido na nota. */
    CHECKLIST_REF,

    /** Referência a outra nota. */
    NOTE_REF;

    val isTextual: Boolean get() = this in setOf(PARAGRAPH, HEADING1, HEADING2, BULLET, NUMBERED, TODO, QUOTE, CODE)
}

@Serializable
data class NoteBlock(
    val id: String,
    val type: BlockType = BlockType.PARAGRAPH,
    val text: String = "",
    val spans: List<TextSpan> = emptyList(),
    val checked: Boolean = false,
    /** Anexo (imagem/arquivo) referenciado por IMAGE/ATTACHMENT. */
    val attachmentId: String? = null,
    /** Células da tabela (linhas x colunas) para TABLE. */
    val rows: List<List<String>>? = null,
    /** Entidade referenciada por CHECKLIST_REF/NOTE_REF. */
    val refId: String? = null,
    /** Tarefa criada a partir deste item (preserva o vínculo da conversão). */
    val taskId: String? = null,
)

@Serializable
enum class SpanStyle { BOLD, ITALIC, UNDERLINE, STRIKE, HIGHLIGHT, LINK }

/** Intervalo [start, end) com estilo. [value] = cor (HIGHLIGHT, ARGB hex) ou URL (LINK). */
@Serializable
data class TextSpan(val start: Int, val end: Int, val style: SpanStyle, val value: String? = null)

object NoteDocumentCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun encode(document: NoteDocument): String = json.encodeToString(NoteDocument.serializer(), document)

    /**
     * Decodifica tolerando campos desconhecidos (documentos de versões futuras) e migrando versões antigas.
     * Conteúdo ilegível nunca é descartado: vira um parágrafo com o texto bruto para que o usuário não perca dados.
     */
    fun decode(raw: String): NoteDocument {
        if (raw.isBlank()) return NoteDocument.empty()
        return try {
            val element = json.parseToJsonElement(raw)
            val obj = element as JsonObject
            // Documentos sem "v" mas com "blocks" são da versão 1 gravados sem o campo de versão.
            val version = obj["v"]?.jsonPrimitive?.intOrNull ?: if (obj.containsKey("blocks")) 1 else 0
            migrate(version, obj)
        } catch (e: Exception) {
            NoteDocument(blocks = listOf(NoteBlock(Ids.new(), BlockType.PARAGRAPH, raw)))
        }
    }

    private fun migrate(version: Int, element: JsonObject): NoteDocument = when {
        version >= 1 -> json.decodeFromJsonElement(NoteDocument.serializer(), element)
        // Versão 0 (pré-lançamento): {"text": "..."} -> parágrafos.
        else -> fromPlainText(element["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    fun fromPlainText(text: String): NoteDocument {
        val lines = text.split('\n')
        return NoteDocument(blocks = lines.map { NoteBlock(Ids.new(), BlockType.PARAGRAPH, it) }.ifEmpty { NoteDocument.empty().blocks })
    }

    /** Texto puro para pesquisa e prévias. */
    fun plainText(document: NoteDocument): String = buildString {
        for (block in document.blocks) {
            val line = when (block.type) {
                BlockType.TABLE -> block.rows.orEmpty().joinToString("\n") { it.joinToString(" | ") }
                BlockType.DIVIDER, BlockType.IMAGE, BlockType.ATTACHMENT, BlockType.CHECKLIST_REF, BlockType.NOTE_REF -> block.text
                else -> block.text
            }
            if (line.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append(line)
            }
        }
    }
}
