package com.organizador.app

import android.content.Context
import androidx.room.Room
import com.organizador.data.di.DatabaseModule
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.domain.model.SystemTimeProvider
import com.organizador.domain.model.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Banco em memória nos testes do app: não toca nos dados reais do aparelho. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): OrganizadorDatabase =
        Room.inMemoryDatabaseBuilder(context, OrganizadorDatabase::class.java).build()

    @Provides
    @Singleton
    fun timeProvider(): TimeProvider = SystemTimeProvider()
}
