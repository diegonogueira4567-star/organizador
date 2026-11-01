package com.organizador.app

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteView
import com.organizador.domain.repository.NoteRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Salvamento automático real do editor: sem botão salvar, o texto chega ao banco após o debounce. */
@HiltAndroidTest
class NoteAutosaveUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var notes: NoteRepository

    @Before
    fun setUp() = hilt.inject()

    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun savedNoteWith(text: String) = runBlocking {
        notes.observeNotes(NoteView.All, NoteSort.UPDATED).first().firstOrNull { it.preview.contains(text) }
    }

    @Test
    fun textoDigitadoEhSalvoSemAcaoDoUsuarioERascunhoELimpo() {
        waitForText("Menu")
        compose.onAllNodesWithText("Menu").onFirst().performClick()
        waitForText("Notas")
        compose.onAllNodesWithText("Notas").onFirst().performClick()
        waitForText("Nenhuma nota")
        compose.onNodeWithContentDescription("Criar").performClick()
        waitForText("Título")
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        // Último campo editável = primeiro bloco do corpo.
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("Ideia salva sozinha")

        // Nenhum clique em salvar: aguarda o debounce (1,5 s) e confere direto no banco.
        compose.waitUntil(8_000) { savedNoteWith("Ideia salva sozinha") != null }
        val saved = savedNoteWith("Ideia salva sozinha")!!
        waitForText("Salvo") // status exibido no topo do editor
        // Depois de salvo, o rascunho de recuperação é removido.
        compose.waitUntil(5_000) { runBlocking { notes.getDraft(saved.id) } == null }
        assertNull(runBlocking { notes.getDraft(saved.id) })
        assertEquals("Ideia salva sozinha", runBlocking { notes.getNote(saved.id)!!.plainText })
    }
}
