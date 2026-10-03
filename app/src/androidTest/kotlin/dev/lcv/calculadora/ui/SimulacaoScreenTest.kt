/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
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
            salvo = SavedStateHandle(),
        )
    }

    private fun montar() {
        val vm = viewModel()
        compose.setContent { CalculadoraTheme { SimulacaoScreen(vm) } }
    }

    @Test
    fun formularioApareceComOsControlesDoProdutoWeb() {
        montar()

        compose.onNodeWithTag(Marcas.DCC).assertIsDisplayed()
        compose.onNodeWithTag(Marcas.VALOR).assertIsDisplayed().assert(hasText("💵 Valor em USD"))
        compose.onNodeWithTag(Marcas.CALCULAR).assertIsDisplayed()
    }

    @Test
    fun calcularComValorValidoMostraOResultado() {
        montar()

        // Máscara de caixa (CALANDR-27): 100000 entra como 1.000,00.
        compose.onNodeWithTag(Marcas.VALOR).performTextInput("100000")
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
        // Máscara de caixa (CALANDR-27): 100000 entra como 1.000,00.
        compose.onNodeWithTag(Marcas.VALOR).performTextInput("100000")
        compose.onNodeWithTag(Marcas.CALCULAR).performClick()
        compose.waitUntil(TEMPO_LIMITE) {
            compose.onAllNodesWithTag(Marcas.CENARIOS).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag(Marcas.CENARIOS).assertIsDisplayed()
    }

    // Máscara de caixa nos campos numéricos e o padrão no lugar de "Auto" (decisões do operador, 02/10/2026, CALANDR-27).

    @Test
    fun cadaDigitoEntraPelaDireitaNoPadraoBrasileiro() {
        montar()

        val valor = compose.onNodeWithTag(Marcas.VALOR)
        valor.performTextInput("1")
        valor.assert(hasText("0,01"))
        valor.performTextInput("23456")
        valor.assert(hasText("1.234,56"))
        // O dígito seguinte vai para o fim, e não para onde o ponto de milhar empurrou o cursor.
        valor.performTextInput("7")
        valor.assert(hasText("12.345,67"))
    }

    @Test
    fun oVetUsaQuatroCasas() {
        montar()

        compose.onNodeWithTag(Marcas.VET_SALDO).performTextInput("57340")
        compose.onNodeWithTag(Marcas.VET_SALDO).assert(hasText("5,7340"))
    }

    @Test
    fun apagarAteSoSobraremZerosEsvaziaOCampo() {
        montar()

        val valor = compose.onNodeWithTag(Marcas.VALOR)
        valor.performTextInput("1")
        valor.assert(hasText("0,01"))
        // O teclado apaga o último caractere: "0,0" depois de "0,01".
        valor.performTextReplacement("0,0")
        valor.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun trocarOValorInteiroPorZeroDaZeroENaoOPadrao() {
        montar()

        val valor = compose.onNodeWithTag(Marcas.VALOR)
        valor.performTextInput("550")
        valor.assert(hasText("5,50"))
        // Selecionar e digitar 0 troca o texto inteiro, e não é apagar.
        valor.performTextReplacement("0")
        valor.assert(hasText("0,00"))
    }

    @Test
    fun selecionarTudoEDigitarTrocaOValor() {
        montar()

        val valor = compose.onNodeWithTag(Marcas.VALOR)
        valor.performTextInput("550")
        valor.assert(hasText("5,50"))
        // "Selecionar tudo" e depois uma tecla: a tecla troca o texto selecionado.
        valor.performTextInputSelection(TextRange(0, "5,50".length))
        valor.performTextInput("0")
        valor.assert(hasText("0,00"))
    }

    @Test
    fun umaTeclaQueNaoMudaONumeroMantemOResultado() {
        montar()

        compose.onNodeWithTag(Marcas.VALOR).performTextInput("100000")
        compose.onNodeWithTag(Marcas.CALCULAR).performClick()
        compose.waitUntil(TEMPO_LIMITE) {
            compose.onAllNodesWithTag(Marcas.RESULTADO).fetchSemanticsNodes().isNotEmpty()
        }
        // Uma letra de teclado físico: a máscara a descarta e o número continua 1.000,00.
        compose.onNodeWithTag(Marcas.VALOR).performTextInput("a")
        compose.onNodeWithTag(Marcas.VALOR).assert(hasText("1.000,00"))
        compose.onNodeWithTag(Marcas.RESULTADO).assertIsDisplayed()
    }

    @Test
    fun oParametroVazioMostraOPadraoQueVaiValer() {
        montar()

        compose.onNodeWithText("Personalizar parâmetros", substring = true).performClick()
        compose.onNodeWithTag(Marcas.SPREAD_CARTAO).performClick()
        compose.waitUntil(TEMPO_LIMITE) {
            compose.onAllNodesWithText("Padrão: 5,50%", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("Auto", useUnmergedTree = true).assertCountEquals(0)
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
