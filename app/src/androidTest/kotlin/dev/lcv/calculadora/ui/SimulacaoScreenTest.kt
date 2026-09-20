/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O que se prova aqui é o que só um aparelho prova: que a tela compõe, que o
 * formulário aceita o toque e o texto, e que o resultado chega à árvore. A
 * aritmética é do `:core:calc` e a ligação formulário↔motor é do teste de JVM
 * ao lado; nada disso é reencenado.
 *
 * O `SimulacaoViewModel` é construído à mão sobre fontes e DAOs em memória, sem
 * Hilt e sem rede: o grafo de injeção do aplicativo não é o objeto deste teste,
 * e depender da rede tornaria o resultado do teste dependente do dia.
 */
@RunWith(AndroidJUnit4::class)
class SimulacaoScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // Sexta-feira, 18/09/2026, 12:00 em Brasília — dia útil, mercado aberto.
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneOffset.UTC)

    private fun viewModel(): SimulacaoViewModel {
        val cotacoes = CotacoesRepository(
            provedorPtax = ProvedorPtax { _, _ -> BigDecimal("5.4000") },
            provedorSpot = ProvedorSpot {
                CotacaoSpotBruta(BigDecimal("5.3800"), FonteSpot.AWESOME_API)
            },
            ptaxCache = PtaxCacheEmMemoria(),
            ultimoSpotDao = UltimoSpotEmMemoria(),
            relogio = relogio,
        )
        return SimulacaoViewModel(
            simulador = Simulador(cotacoes, BacktestRepository(BacktestEmMemoria(), relogio), relogio),
            relogio = relogio,
        )
    }

    private fun montar() {
        compose.setContent { CalculadoraTheme { SimulacaoScreen(viewModel()) } }
    }

    @Test
    fun formularioApareceComOsControlesDoProdutoWeb() {
        montar()

        compose.onNodeWithTag(Marcas.DCC).assertIsDisplayed()
        compose.onNodeWithTag(Marcas.VALOR).assertIsDisplayed()
        compose.onNodeWithTag(Marcas.CALCULAR).assertIsDisplayed()
    }

    @Test
    fun calcularComValorValidoMostraOResultado() {
        montar()

        compose.onNodeWithTag(Marcas.VALOR).performTextInput("1000")
        compose.onNodeWithTag(Marcas.CALCULAR).performClick()
        compose.waitUntil(TEMPO_LIMITE) {
            compose.onAllNodesWithTag(Marcas.RESULTADO).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag(Marcas.RESULTADO).assertIsDisplayed()
    }

    @Test
    fun marcarDccTrocaOFormularioParaOsCenariosEmReais() {
        montar()

        compose.onNodeWithTag(Marcas.DCC).performClick()
        compose.onNodeWithTag(Marcas.VALOR).performTextInput("1000")
        compose.onNodeWithTag(Marcas.CALCULAR).performClick()
        compose.waitUntil(TEMPO_LIMITE) {
            compose.onAllNodesWithTag(Marcas.CENARIOS).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag(Marcas.CENARIOS).assertIsDisplayed()
    }
}

/** Cinco segundos cobrem a composição e o `viewModelScope` sem rede. */
private const val TEMPO_LIMITE = 5_000L

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
