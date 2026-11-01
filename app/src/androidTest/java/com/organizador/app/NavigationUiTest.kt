package com.organizador.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Testes de interface e navegação sobre a Activity real (com banco em memória). */
@HiltAndroidTest
class NavigationUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hilt.inject()

    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun adicionarTarefaRapidaApareceEmHoje() {
        waitForText("Hoje")
        compose.onAllNodesWithText("Hoje").onFirst().performClick()
        waitForText("Tudo em dia!")
        compose.onNodeWithContentDescription("Criar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Comprar café")
        compose.onNodeWithContentDescription("Adicionar").performClick()
        compose.activity.onBackPressedDispatcher.let { compose.runOnUiThread { it.onBackPressed() } }
        waitForText("Comprar café")
        compose.onNodeWithText("Comprar café").assertIsDisplayed()
    }

    @Test
    fun menuLateralNavegaParaNotasEAbreEditor() {
        waitForText("Menu")
        compose.onAllNodesWithText("Menu").onFirst().performClick()
        waitForText("Notas")
        compose.onAllNodesWithText("Notas").onFirst().performClick()
        waitForText("Nenhuma nota")
        compose.onNodeWithContentDescription("Criar").performClick()
        waitForText("Título")
        compose.onNodeWithText("Título").assertIsDisplayed()
    }

    @Test
    fun criarRotinaPeloMenuDeRotinas() {
        waitForText("Menu")
        compose.onAllNodesWithText("Menu").onFirst().performClick()
        waitForText("Rotinas")
        compose.onAllNodesWithText("Rotinas").onFirst().performClick()
        waitForText("Nenhuma rotina")
        compose.onNodeWithContentDescription("Criar").performClick()
        waitForText("Nova rotina")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Rotina de sábado")
        compose.onNodeWithText("Sábados").performClick()
        compose.onNodeWithText("Salvar").performClick()
        // Após salvar, abre a execução (ou a mensagem de próxima execução) do checklist criado.
        waitForText("Rotina de sábado")
    }
}
