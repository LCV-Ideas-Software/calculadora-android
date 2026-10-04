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

/** Sexta-feira, 18/09/2026, 23:00 em Brasília — fora da janela do mercado: a conta global opera em plantão. */
internal val RELOGIO_DE_PLANTAO: Clock = Clock.fixed(Instant.parse("2026-09-19T02:00:00Z"), ZoneOffset.UTC)

/** A spot de sempre dos testes: a AwesomeAPI responde. */
private val SPOT_RESPONDE = ProvedorSpot { CotacaoSpotBruta(BigDecimal("5.3800"), FonteSpot.AWESOME_API) }

/** Nenhuma fonte de spot responde e não há último spot salvo: o motor cai na PTAX de contingência. */
internal val SPOT_FORA_DO_AR = ProvedorSpot { null }

/**
 * A AwesomeAPI responde com o instante da cotação, uma hora antes do relógio de teste: o mesmo dia da PTAX e da compra,
 * a condição para o backtest registrar a observação e mostrar a qualidade. Contra a PTAX de 5,4000 e o fator de
 * calibragem padrão (0,99934), 5,3800 erra 0,44% (excelente), 5,3200 erra 1,55% (boa) e 5,2500 erra 2,84% (atenção).
 */
internal fun spotComInstante(taxa: String) = ProvedorSpot {
    CotacaoSpotBruta(BigDecimal(taxa), FonteSpot.AWESOME_API, Instant.parse("2026-09-18T14:00:00Z"))
}

/**
 * O `SimulacaoViewModel` construído à mão sobre fontes e DAOs em memória, sem Hilt e sem rede: o grafo de injeção
 * do aplicativo não é o objeto dos testes instrumentados, e depender da rede tornaria o resultado dependente do dia.
 */
internal fun viewModelEmMemoria(
    salvo: SavedStateHandle = SavedStateHandle(),
    relogio: Clock = RELOGIO_DE_TESTE,
    provedorSpot: ProvedorSpot = SPOT_RESPONDE,
): SimulacaoViewModel {
    val cotacoes = CotacoesRepository(
        provedorPtax = ProvedorPtax { _, _ -> BigDecimal("5.4000") },
        provedorSpot = provedorSpot,
        ptaxCache = PtaxCacheEmMemoria(),
        ultimoSpotDao = UltimoSpotEmMemoria(),
        relogio = relogio,
    )
    return SimulacaoViewModel(
        simulador = Simulador(cotacoes, BacktestRepository(BacktestEmMemoria(), relogio), relogio),
        relogio = relogio,
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
