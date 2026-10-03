/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.lifecycle.SavedStateHandle
import dev.lcv.calculadora.calc.CotacaoSpotBruta
import dev.lcv.calculadora.calc.FonteSpot
import dev.lcv.calculadora.data.backtest.BacktestRepository
import dev.lcv.calculadora.data.cotacoes.CotacoesRepository
import dev.lcv.calculadora.data.cotacoes.ProvedorPtax
import dev.lcv.calculadora.data.cotacoes.ProvedorSpot
import dev.lcv.calculadora.data.persistencia.BacktestDao
import dev.lcv.calculadora.data.persistencia.ObservacaoBacktestEntity
import dev.lcv.calculadora.data.persistencia.PtaxCacheDao
import dev.lcv.calculadora.data.persistencia.PtaxCacheEntity
import dev.lcv.calculadora.data.persistencia.UltimoSpotDao
import dev.lcv.calculadora.data.persistencia.UltimoSpotEntity
import dev.lcv.calculadora.data.simulacao.Simulador
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Sexta-feira, 18/09/2026, 12:00 em Brasília — dia útil, mercado aberto. */
internal val RELOGIO_DE_TESTE: Clock = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneOffset.UTC)

/**
 * O `SimulacaoViewModel` construído à mão sobre fontes e DAOs em memória, sem Hilt e sem rede: o grafo de injeção
 * do aplicativo não é o objeto dos testes instrumentados, e depender da rede tornaria o resultado dependente do dia.
 */
internal fun viewModelEmMemoria(salvo: SavedStateHandle = SavedStateHandle()): SimulacaoViewModel {
    val cotacoes = CotacoesRepository(
        provedorPtax = ProvedorPtax { _, _ -> BigDecimal("5.4000") },
        provedorSpot = ProvedorSpot {
            CotacaoSpotBruta(BigDecimal("5.3800"), FonteSpot.AWESOME_API)
        },
        ptaxCache = PtaxCacheEmMemoria(),
        ultimoSpotDao = UltimoSpotEmMemoria(),
        relogio = RELOGIO_DE_TESTE,
    )
    return SimulacaoViewModel(
        simulador = Simulador(cotacoes, BacktestRepository(BacktestEmMemoria(), RELOGIO_DE_TESTE), RELOGIO_DE_TESTE),
        relogio = RELOGIO_DE_TESTE,
        salvo = salvo,
    )
}

/** Os DAOs são interfaces do Room; em memória, provam a ligação sem banco. */
private class PtaxCacheEmMemoria : PtaxCacheDao {
    private val linhas = mutableMapOf<Pair<String, LocalDate>, PtaxCacheEntity>()

    override suspend fun buscar(moeda: String, data: LocalDate): PtaxCacheEntity? = linhas[moeda to data]

    override suspend fun guardar(entidade: PtaxCacheEntity) {
        linhas[entidade.moeda to entidade.data] = entidade
    }
}

private class UltimoSpotEmMemoria : UltimoSpotDao {
    private val linhas = mutableMapOf<String, UltimoSpotEntity>()

    override suspend fun buscar(moeda: String): UltimoSpotEntity? = linhas[moeda]

    override suspend fun guardar(entidade: UltimoSpotEntity) {
        linhas[entidade.moeda] = entidade
    }
}

private class BacktestEmMemoria : BacktestDao {
    private val linhas = mutableListOf<ObservacaoBacktestEntity>()

    override suspend fun inserir(observacao: ObservacaoBacktestEntity) {
        linhas += observacao.copy(id = (linhas.size + 1).toLong())
    }

    override suspend fun desde(desde: Long, limite: Int): List<ObservacaoBacktestEntity> =
        linhas.filter { it.criadoEm >= desde }.sortedByDescending { it.criadoEm }.take(limite)

    override suspend fun apagarAnterioresA(antesDe: Long): Int {
        val antes = linhas.size
        linhas.removeAll { it.criadoEm < antesDe }
        return antes - linhas.size
    }
}
