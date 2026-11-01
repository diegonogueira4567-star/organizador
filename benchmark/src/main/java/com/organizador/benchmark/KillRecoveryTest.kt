package com.organizador.benchmark

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Encerramento inesperado real: o processo do app é morto (force-stop, sem onStop/onCleared) depois do rascunho
 * (400 ms) e antes da gravação da nota (1,5 s). Ao reabrir a nota, o app deve oferecer recuperar o texto.
 */
@RunWith(AndroidJUnit4::class)
class KillRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun cleanState() {
        device.executeShellCommand("pm clear $TARGET")
    }

    private fun launch() {
        val intent = instrumentation.context.packageManager.getLaunchIntentForPackage(TARGET)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(TARGET).depth(0)), 10_000)
    }

    @Test
    fun textoDigitadoAntesDoProcessoMorrerEhRecuperado() {
        launch()
        device.clickText("Menu")
        device.clickText("Notas")
        device.clickDesc("Criar")
        device.waitText("Título")
        val fields = device.wait(Until.findObjects(By.clazz("android.widget.EditText")), 10_000)
        // [0] = título, [1] = primeiro bloco do corpo
        fields.last().text = "Texto que não pode se perder"
        Thread.sleep(800) // rascunho já gravado (400 ms), nota ainda não (1,5 s)
        device.executeShellCommand("am force-stop $TARGET")

        launch()
        device.clickText("Menu")
        device.clickText("Notas")
        device.clickText("Sem título")
        assertNotNull("o app deveria oferecer a recuperação do rascunho", device.wait(Until.findObject(By.text("Recuperar alterações?")), 10_000))
        device.clickText("Recuperar")
        assertNotNull(device.wait(Until.findObject(By.text("Texto que não pode se perder")), 10_000))
    }
}
