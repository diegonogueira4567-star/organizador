package com.organizador.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Inicialização a frio (meta: até 2 s) com e sem compilação por perfil. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun inicializacaoFrioSemCompilacao() = startup(CompilationMode.None())

    @Test
    fun inicializacaoFrioComPerfil() = startup(CompilationMode.Partial())
}
