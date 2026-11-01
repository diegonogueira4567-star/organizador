package com.organizador.domain.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocumentTest {

    @Test
    fun `codifica e decodifica sem perda`() {
        val doc = NoteDocument(
            blocks = listOf(
                NoteBlock("1", BlockType.HEADING1, "Título"),
                NoteBlock("2", BlockType.PARAGRAPH, "Olá mundo", spans = listOf(TextSpan(0, 3, SpanStyle.BOLD))),
                NoteBlock("3", BlockType.TODO, "Comprar pão", checked = true),
                NoteBlock("4", BlockType.TABLE, rows = listOf(listOf("a", "b"), listOf("c", "d"))),
                NoteBlock("5", BlockType.PARAGRAPH, "site", spans = listOf(TextSpan(0, 4, SpanStyle.LINK, "https://exemplo.org"))),
            ),
        )
        assertEquals(doc, NoteDocumentCodec.decode(NoteDocumentCodec.encode(doc)))
    }

    @Test
    fun `campos e tipos desconhecidos de versoes futuras sao tolerados`() {
        val raw = """{"v":2,"blocks":[{"id":"x","type":"KANBAN","text":"abc","novo":1}]}"""
        val doc = NoteDocumentCodec.decode(raw)
        assertEquals("abc", doc.blocks.single().text)
        assertEquals(BlockType.PARAGRAPH, doc.blocks.single().type)
    }

    @Test
    fun `conteudo corrompido nao e descartado`() {
        val doc = NoteDocumentCodec.decode("{texto quebrado")
        assertEquals("{texto quebrado", doc.blocks.single().text)
    }

    @Test
    fun `versao 0 e migrada`() {
        val doc = NoteDocumentCodec.decode("""{"text":"linha 1\nlinha 2"}""")
        assertEquals(listOf("linha 1", "linha 2"), doc.blocks.map { it.text })
        assertEquals(NoteDocument.CURRENT_VERSION, doc.version)
    }

    @Test
    fun `texto puro inclui tabelas e ignora blocos vazios`() {
        val doc = NoteDocument(
            blocks = listOf(
                NoteBlock("1", BlockType.PARAGRAPH, "a"),
                NoteBlock("2", BlockType.DIVIDER),
                NoteBlock("3", BlockType.TABLE, rows = listOf(listOf("x", "y"))),
            ),
        )
        assertEquals("a\nx | y", NoteDocumentCodec.plainText(doc))
    }
}

class SpanEditingTest {
    private val bold = TextSpan(6, 11, SpanStyle.BOLD) // "Hello world" -> "world"

    @Test
    fun `insercao antes do span desloca`() {
        val result = SpanEditing.adjust("Hello world", "Oh Hello world", listOf(bold))
        assertEquals(listOf(TextSpan(9, 14, SpanStyle.BOLD)), result)
    }

    @Test
    fun `digitar no fim do span estende o estilo`() {
        val result = SpanEditing.adjust("Hello world", "Hello worlds", listOf(bold))
        assertEquals(listOf(TextSpan(6, 12, SpanStyle.BOLD)), result)
    }

    @Test
    fun `digitar no inicio do span nao herda o estilo`() {
        val result = SpanEditing.adjust("Hello world", "Hello Xworld", listOf(bold))
        assertEquals(listOf(TextSpan(7, 12, SpanStyle.BOLD)), result)
    }

    @Test
    fun `apagar o trecho remove o span`() {
        val result = SpanEditing.adjust("Hello world", "Hello ", listOf(bold))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `apagar dentro do span encolhe`() {
        val result = SpanEditing.adjust("Hello world", "Hello wld", listOf(bold))
        assertEquals(listOf(TextSpan(6, 9, SpanStyle.BOLD)), result)
    }

    @Test
    fun `alternar estilo aplica e remove`() {
        val applied = SpanEditing.toggle(emptyList(), 0, 5, SpanStyle.ITALIC)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.ITALIC)), applied)
        assertTrue(SpanEditing.toggle(applied, 0, 5, SpanStyle.ITALIC).isEmpty())
        val partial = SpanEditing.toggle(applied, 0, 2, SpanStyle.ITALIC)
        assertEquals(listOf(TextSpan(2, 5, SpanStyle.ITALIC)), partial)
    }

    @Test
    fun `normalizar une spans adjacentes`() {
        val merged = SpanEditing.normalize(listOf(TextSpan(0, 2, SpanStyle.BOLD), TextSpan(2, 4, SpanStyle.BOLD), TextSpan(1, 3, SpanStyle.ITALIC)))
        assertEquals(listOf(TextSpan(0, 4, SpanStyle.BOLD), TextSpan(1, 3, SpanStyle.ITALIC)), merged)
    }
}
