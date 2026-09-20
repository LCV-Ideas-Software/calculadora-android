/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import dev.lcv.calculadora.calc.CotacaoSpotBruta
import dev.lcv.calculadora.calc.ErroEntrada
import dev.lcv.calculadora.calc.FonteSpot
import dev.lcv.calculadora.calc.Modalidade
import dev.lcv.calculadora.calc.MotivoIndisponibilidade
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * O ViewModel roda na JVM contra um `Simulador` real montado sobre DAOs e
 * provedores em memória: o que se prova aqui é a ligação entre o formulário e o
 * motor, não a aritmética, que é do `:core:calc`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulacaoViewModelTest {

    // Sexta-feira, 18/09/2026, 12:00 em Brasília: dia útil, dentro do mercado.
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneOffset.UTC)
    private val despachante = StandardTestDispatcher()

    private var ptaxDaFonte: BigDecimal? = BigDecimal("5.4000")
    private var spotDaFonte: CotacaoSpotBruta? = CotacaoSpotBruta(BigDecimal("5.3800"), FonteSpot.AWESOME_API)
    private var explodir = false

    private val ptaxCache = PtaxCacheEmMemoria()
    private val ultimoSpot = UltimoSpotEmMemoria()
    private val backtestDao = BacktestEmMemoria()

    private fun viewModel(): SimulacaoViewModel {
        val cotacoes = CotacoesRepository(
            provedorPtax = ProvedorPtax { _, _ ->
                if (explodir) throw java.io.IOException("sem rede") else ptaxDaFonte
            },
            provedorSpot = ProvedorSpot { spotDaFonte },
            ptaxCache = ptaxCache,
            ultimoSpotDao = ultimoSpot,
            relogio = relogio,
        )
        return SimulacaoViewModel(
            simulador = Simulador(cotacoes, BacktestRepository(backtestDao, relogio), relogio),
            relogio = relogio,
        )
    }

    @BeforeTest
    fun ligarDespachante() {
        Dispatchers.setMain(despachante)
    }

    @AfterTest
    fun desligarDespachante() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a data inicial e hoje em Brasilia, nao no fuso do aparelho`() {
        assertEquals(LocalDate.of(2026, 9, 18), viewModel().estado.value.dataCompra)
    }

    @Test
    fun `valor vazio nao chama o motor e acusa o erro do formulario`() = runTest(despachante) {
        val vm = viewModel()
        vm.calcular()
        assertEquals(ErroEntrada.VALOR_INVALIDO, vm.estado.value.erro)
        assertNull(vm.estado.value.simulacao)
    }

    @Test
    fun `valor negativo tambem e recusado`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarValor("-10")
        vm.calcular()
        assertEquals(ErroEntrada.VALOR_INVALIDO, vm.estado.value.erro)
    }

    @Test
    fun `simulacao valida produz as duas modalidades e registra o backtest`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarValor("100")
        vm.calcular()
        assertTrue(vm.estado.value.carregando, "o estado de carregamento aparece antes da resposta")
        testScheduler.advanceUntilIdle()

        val estado = vm.estado.value
        assertEquals(false, estado.carregando)
        val resultado = assertNotNull(estado.simulacao)
        assertTrue(resultado.simulacao.cartao is Modalidade.Suportada)
        assertTrue(resultado.simulacao.global is Modalidade.Suportada)
        assertEquals(1, backtestDao.linhas.size, "a observação do backtest foi gravada")
        assertNotNull(resultado.backtest, "e o resumo volta para a tela")
    }

    @Test
    fun `valor em formato brasileiro e aceito`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarValor("1.234,56")
        vm.calcular()
        testScheduler.advanceUntilIdle()
        val resultado = assertNotNull(vm.estado.value.simulacao)
        assertEquals(BigDecimal("1234.56"), resultado.simulacao.entrada.valorOriginal)
    }

    @Test
    fun `sem PTAX a modalidade volta indisponivel, sem falha de tela`() = runTest(despachante) {
        ptaxDaFonte = null
        val vm = viewModel()
        vm.mudarValor("100")
        vm.calcular()
        testScheduler.advanceUntilIdle()

        val resultado = assertNotNull(vm.estado.value.simulacao)
        val cartao = resultado.simulacao.cartao
        assertTrue(cartao is Modalidade.Indisponivel)
        assertEquals(MotivoIndisponibilidade.PTAX_INDISPONIVEL, cartao.motivo)
        assertEquals(false, vm.estado.value.falhou)
    }

    @Test
    fun `moeda sem conta global mostra o motivo, e nao um numero`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarMoeda("GBP")
        vm.mudarValor("100")
        vm.calcular()
        testScheduler.advanceUntilIdle()

        val global = assertNotNull(vm.estado.value.simulacao).simulacao.global
        assertTrue(global is Modalidade.Indisponivel)
        assertEquals(MotivoIndisponibilidade.MOEDA_SEM_CONTA_GLOBAL, global.motivo)
    }

    @Test
    fun `falha inesperada vira estado de falha, e nao derruba a tela`() = runTest(despachante) {
        explodir = true
        val vm = viewModel()
        vm.mudarValor("100")
        vm.calcular()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.estado.value.falhou)
        assertNull(vm.estado.value.simulacao)
        assertEquals(false, vm.estado.value.carregando)
    }

    @Test
    fun `editar qualquer entrada limpa o resultado anterior`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarValor("100")
        vm.calcular()
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.estado.value.simulacao)

        vm.mudarValor("200")
        assertNull(vm.estado.value.simulacao, "um resultado de outro valor ao lado do formulário mentiria")
        assertNull(vm.estado.value.erro)
    }

    @Test
    fun `modo cobrado em reais dispensa rede e devolve os tres cenarios`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.mudarValor("1000")
        vm.calcular()

        val analise = assertNotNull(vm.estado.value.compraEmReais)
        assertEquals(BigDecimal("1000.00"), analise.adquirenciaLocal.totalBrl)
        assertEquals(BigDecimal("1035.00"), analise.dccPura.totalBrl, "1000 × (1 + IOF 3,5%)")
        assertNull(analise.diagnostico, "sem valor de fatura não há diagnóstico reverso")
        assertNull(vm.estado.value.simulacao, "o modo em reais não chama o motor de câmbio")
    }

    @Test
    fun `o valor da fatura produz o diagnostico reverso`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.mudarValor("1000")
        vm.mudarValorFatura("1035")
        vm.calcular()

        val diagnostico = assertNotNull(assertNotNull(vm.estado.value.compraEmReais).diagnostico)
        assertEquals(BigDecimal("1035.00"), diagnostico.valorFaturaBrl)
    }

    @Test
    fun `trocar de modo limpa o resultado do modo anterior`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarValor("100")
        vm.calcular()
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.estado.value.simulacao)

        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        assertNull(vm.estado.value.simulacao)
        assertNull(vm.estado.value.compraEmReais)
    }
}

/** Os DAOs são interfaces do Room; em memória, provam a ligação sem banco. */
private class PtaxCacheEmMemoria : PtaxCacheDao {
    val linhas = mutableMapOf<Pair<String, LocalDate>, PtaxCacheEntity>()

    override suspend fun buscar(moeda: String, data: LocalDate): PtaxCacheEntity? = linhas[moeda to data]

    override suspend fun guardar(entidade: PtaxCacheEntity) {
        linhas[entidade.moeda to entidade.data] = entidade
    }
}

private class UltimoSpotEmMemoria : UltimoSpotDao {
    val linhas = mutableMapOf<String, UltimoSpotEntity>()

    override suspend fun buscar(moeda: String): UltimoSpotEntity? = linhas[moeda]

    override suspend fun guardar(entidade: UltimoSpotEntity) {
        linhas[entidade.moeda] = entidade
    }
}

private class BacktestEmMemoria : BacktestDao {
    val linhas = mutableListOf<ObservacaoBacktestEntity>()

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
