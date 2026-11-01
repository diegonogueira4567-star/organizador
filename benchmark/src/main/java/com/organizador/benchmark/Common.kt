package com.organizador.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

const val TARGET = "com.organizador.app.benchmark"
private const val TIMEOUT = 10_000L

fun UiDevice.waitText(text: String): UiObject2 =
    wait(Until.findObject(By.text(text)), TIMEOUT) ?: error("Texto não encontrado na tela: $text")

fun UiDevice.waitTextStarting(prefix: String): UiObject2 =
    wait(Until.findObject(By.textStartsWith(prefix)), TIMEOUT) ?: error("Texto não encontrado na tela: $prefix…")

fun UiDevice.clickText(text: String) = waitText(text).click()

fun UiDevice.clickDesc(desc: String) =
    (wait(Until.findObject(By.desc(desc)), TIMEOUT) ?: error("Elemento não encontrado: $desc")).click()

/** Garante o volume de dados de teste (idempotente) usando a tela de carga da variante benchmark. */
fun UiDevice.seedData() {
    executeShellCommand("am start -W -n $TARGET/com.organizador.app.BenchmarkSeedActivity")
    // A tela se fecha sozinha ao terminar; aguarda o pacote sair do primeiro plano.
    wait(Until.gone(By.pkg(TARGET).depth(0)), TIMEOUT)
}

fun UiDevice.mainScrollable(): UiObject2 =
    wait(Until.findObject(By.scrollable(true)), TIMEOUT) ?: error("Lista rolável não encontrada")
