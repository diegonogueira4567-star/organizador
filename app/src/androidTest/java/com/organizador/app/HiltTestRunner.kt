package com.organizador.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import dagger.hilt.android.testing.CustomTestApplication

/**
 * Base da aplicação de teste: o manifesto desativa a inicialização automática do WorkManager (o app real
 * fornece a configuração com a fábrica do Hilt), então os testes também precisam fornecê-la.
 */
open class TestApplicationBase : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}

@CustomTestApplication(TestApplicationBase::class)
interface OrganizadorTestApplication

/** Executa os testes instrumentados com a aplicação de teste do Hilt (permite substituir módulos). */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, OrganizadorTestApplication_Application::class.java.name, context)
}
