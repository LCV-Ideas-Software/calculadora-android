/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.lcv.calculadora.data.cotacoes.ProvedorPtax
import dev.lcv.calculadora.data.cotacoes.ProvedorPtaxBcb
import dev.lcv.calculadora.data.cotacoes.ProvedorSpot
import dev.lcv.calculadora.data.cotacoes.ProvedorSpotWeb
import dev.lcv.calculadora.data.persistencia.BacktestDao
import dev.lcv.calculadora.data.persistencia.CalculadoraDatabase
import dev.lcv.calculadora.data.persistencia.PtaxCacheDao
import dev.lcv.calculadora.data.persistencia.UltimoSpotDao
import dev.lcv.calculadora.data.rede.AwesomeApi
import dev.lcv.calculadora.data.rede.BcbFechamento
import dev.lcv.calculadora.data.rede.BcbOlinda
import dev.lcv.calculadora.data.rede.IdentidadeAplicativo
import dev.lcv.calculadora.data.rede.YahooFinance
import java.time.Clock
import java.time.Duration
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Ligações do módulo. O `:app` fornece [IdentidadeAplicativo] (a versão vem do
 * seu manifesto); tudo o mais nasce aqui. Retrofit sem conversor: as fontes
 * devolvem `ResponseBody`, e a leitura é dos leitores deste módulo.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    /** Tempo total por chamada, como no produto web (`fetchComTimeout`, 4 s). */
    private val TEMPO_LIMITE: Duration = Duration.ofSeconds(4)

    @Provides
    @Singleton
    fun relogio(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun okHttp(identidade: IdentidadeAplicativo): OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(TEMPO_LIMITE)
            .addInterceptor { cadeia ->
                cadeia.proceed(cadeia.request().newBuilder().header("User-Agent", identidade.userAgent).build())
            }
            .build()

    @Provides
    @Singleton
    fun olinda(cliente: OkHttpClient): BcbOlinda = retrofit(cliente, BcbOlinda.URL_BASE).create(BcbOlinda::class.java)

    @Provides
    @Singleton
    fun fechamento(cliente: OkHttpClient): BcbFechamento =
        retrofit(cliente, BcbFechamento.URL_BASE).create(BcbFechamento::class.java)

    @Provides
    @Singleton
    fun awesome(cliente: OkHttpClient): AwesomeApi = retrofit(cliente, AwesomeApi.URL_BASE).create(AwesomeApi::class.java)

    @Provides
    @Singleton
    fun yahoo(cliente: OkHttpClient): YahooFinance =
        retrofit(cliente, YahooFinance.URL_BASE).create(YahooFinance::class.java)

    @Provides
    @Singleton
    fun bancoDeDados(@ApplicationContext contexto: Context): CalculadoraDatabase =
        Room.databaseBuilder(contexto, CalculadoraDatabase::class.java, CalculadoraDatabase.NOME).build()

    @Provides
    fun ptaxCache(db: CalculadoraDatabase): PtaxCacheDao = db.ptaxCache()

    @Provides
    fun ultimoSpot(db: CalculadoraDatabase): UltimoSpotDao = db.ultimoSpot()

    @Provides
    fun backtest(db: CalculadoraDatabase): BacktestDao = db.backtest()

    private fun retrofit(cliente: OkHttpClient, urlBase: String): Retrofit =
        Retrofit.Builder().client(cliente).baseUrl(urlBase).build()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProvedoresModule {
    @Binds
    abstract fun ptax(impl: ProvedorPtaxBcb): ProvedorPtax

    @Binds
    abstract fun spot(impl: ProvedorSpotWeb): ProvedorSpot
}
