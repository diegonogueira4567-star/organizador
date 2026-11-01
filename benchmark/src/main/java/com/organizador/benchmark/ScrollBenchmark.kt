package com.organizador.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fluidez (meta: 60 FPS ≈ 16,7 ms por quadro). FrameTimingMetric registra frameDurationCpuMs e
 * frameOverrunMs (quanto cada quadro passou do prazo; negativo = dentro do prazo) em percentis.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun scroll(openScreen: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            device.seedData()
            pressHome()
            startActivityAndWait()
            openScreen()
        },
    ) {
        val list = device.mainScrollable()
        list.setGestureMargin(device.displayWidth / 5)
        repeat(3) { list.fling(Direction.DOWN) }
        repeat(3) { list.fling(Direction.UP) }
    }

    @Test
    fun rolagemDaListaHoje() = scroll {
        device.clickText("Hoje")
        device.waitTextStarting("Atrasadas (")
    }

    @Test
    fun rolagemDaListaDeNotas() = scroll {
        device.clickText("Menu")
        device.clickText("Notas")
        device.waitText("Nota de carga 299")
    }

    @Test
    fun rolagemDoPainel() = scroll {
        device.waitText("Progresso de hoje")
    }
}
