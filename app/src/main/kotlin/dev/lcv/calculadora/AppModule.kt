/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.lcv.calculadora.data.rede.IdentidadeAplicativo
import javax.inject.Singleton

/**
 * A única ligação que o `:core:data` deixou para o `:app`: a versão que vai no
 * `User-Agent` das fontes de cotação. Ela vem do manifesto do próprio
 * aplicativo, que é o que o módulo de dados não tem como conhecer.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun identidade(): IdentidadeAplicativo = IdentidadeAplicativo(BuildConfig.VERSION_NAME)
}
