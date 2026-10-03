/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
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
import kotlin.test.assertFalse
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
 *
 * Os campos numéricos guardam dígitos crus (CALANDR-27): os testes escrevem
 * neles pelo `edit` público do `TextFieldState`, que não passa pela
 * transformação da tela, e por isso escrevem só o que ela deixaria passar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulacaoViewModelTest {

    // Sexta-feira, 18/09/2026, 12:00 em Brasília: dia útil, dentro do mercado.
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneOffset.UTC)
    private val despachante = StandardTestDispatcher()

    private var ptaxDaFonte: BigDecimal? = BigDecimal("5.4000")
    private var spotDaFonte: CotacaoSpotBruta? = CotacaoSpotBruta(BigDecimal("5.3800"), FonteSpot.AWESOME_API, relogio.instant())
    private var explodir = false

    private val ptaxCache = PtaxCacheEmMemoria()
    private val ultimoSpot = UltimoSpotEmMemoria()
    private val backtestDao = BacktestEmMemoria()

    private fun viewModel(salvo: SavedStateHandle = SavedStateHandle()): SimulacaoViewModel {
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
            salvo = salvo,
        )
    }

    /** O estado que a tela mostra: o guardado, conferido contra os campos de agora. */
    private val SimulacaoViewModel.visivel: EstadoTela
        get() = estado.value.visivelCom(entradasAtuais())

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
        assertEquals(ErroEntrada.VALOR_INVALIDO, vm.visivel.erro)
        assertNull(vm.visivel.simulacao)
    }

    @Test
    fun `valor zero explicito tambem e recusado`() = runTest(despachante) {
        val vm = viewModel()
        vm.campoValor.setTextAndPlaceCursorAtEnd("0")
        vm.calcular()
        assertEquals(ErroEntrada.VALOR_INVALIDO, vm.visivel.erro)
    }

    @Test
    fun `simulacao valida produz as duas modalidades e registra o backtest`() = runTest(despachante) {
        val vm = viewModel()
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.calcular()
        assertTrue(vm.visivel.carregando, "o estado de carregamento aparece antes da resposta")
        testScheduler.advanceUntilIdle()

        val estado = vm.visivel
        assertEquals(false, estado.carregando)
        val resultado = assertNotNull(estado.simulacao)
        assertTrue(resultado.simulacao.cartao is Modalidade.Suportada)
        assertTrue(resultado.simulacao.global is Modalidade.Suportada)
        assertEquals(1, backtestDao.linhas.size, "a observação do backtest foi gravada")
        assertNotNull(resultado.backtest, "e o resumo volta para a tela")
    }

    @Test
    fun `o cru do campo entra no motor com as casas dele`() = runTest(despachante) {
        val vm = viewModel()
        vm.campoValor.setTextAndPlaceCursorAtEnd("123456")
        vm.calcular()
        testScheduler.advanceUntilIdle()
        val resultado = assertNotNull(vm.visivel.simulacao)
        assertEquals(BigDecimal("1234.56"), resultado.simulacao.entrada.valorOriginal)
    }

    @Test
    fun `sem PTAX a modalidade volta indisponivel, sem falha de tela`() = runTest(despachante) {
        ptaxDaFonte = null
        val vm = viewModel()
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.calcular()
        testScheduler.advanceUntilIdle()

        val resultado = assertNotNull(vm.visivel.simulacao)
        val cartao = resultado.simulacao.cartao
        assertTrue(cartao is Modalidade.Indisponivel)
        assertEquals(MotivoIndisponibilidade.PTAX_INDISPONIVEL, cartao.motivo)
        assertEquals(false, vm.visivel.falhou)
    }

    @Test
    fun `moeda sem conta global mostra o motivo, e nao um numero`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarMoeda("GBP")
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.calcular()
        testScheduler.advanceUntilIdle()

        val global = assertNotNull(vm.visivel.simulacao).simulacao.global
        assertTrue(global is Modalidade.Indisponivel)
        assertEquals(MotivoIndisponibilidade.MOEDA_SEM_CONTA_GLOBAL, global.motivo)
    }

    @Test
    fun `falha inesperada vira estado de falha, e nao derruba a tela`() = runTest(despachante) {
        explodir = true
        val vm = viewModel()
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.calcular()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.visivel.falhou)
        assertNull(vm.visivel.simulacao)
        assertEquals(false, vm.visivel.carregando)
    }

    @Test
    fun `modo cobrado em reais dispensa rede e devolve os tres cenarios`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.campoValor.setTextAndPlaceCursorAtEnd("100000")
        vm.calcular()

        val analise = assertNotNull(vm.visivel.compraEmReais)
        assertEquals(BigDecimal("1000.00"), analise.adquirenciaLocal.totalBrl)
        assertEquals(BigDecimal("1035.00"), analise.dccPura.totalBrl, "1000 × (1 + IOF 3,5%)")
        assertNull(analise.diagnostico, "sem valor de fatura não há diagnóstico reverso")
        assertNull(vm.visivel.simulacao, "o modo em reais não chama o motor de câmbio")
    }

    @Test
    fun `o zero explicito no IOF vale zero, e nao o padrao`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.campoValor.setTextAndPlaceCursorAtEnd("100000")
        vm.campoIof.setTextAndPlaceCursorAtEnd("0")
        vm.calcular()
        assertEquals(BigDecimal("1000.00"), assertNotNull(vm.visivel.compraEmReais).dccPura.totalBrl)
    }

    @Test
    fun `o valor da fatura produz o diagnostico reverso`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.campoValor.setTextAndPlaceCursorAtEnd("100000")
        vm.campoFatura.setTextAndPlaceCursorAtEnd("103500")
        vm.calcular()

        val diagnostico = assertNotNull(assertNotNull(vm.visivel.compraEmReais).diagnostico)
        assertEquals(BigDecimal("1035.00"), diagnostico.valorFaturaBrl)
    }

    @Test
    fun `no modo cobrado em reais os spreads da conta global nao contam`() = runTest(despachante) {
        val vm = viewModel()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.campoValor.setTextAndPlaceCursorAtEnd("100000")
        // 999,99% seria recusado no modo de simulação; aqui o campo nem aparece e o motor o ignora.
        vm.campoSpreadAberto.setTextAndPlaceCursorAtEnd("99999")
        vm.calcular()
        assertNull(vm.visivel.erro)
        assertNotNull(vm.visivel.compraEmReais)
    }

    @Test
    fun `um percentual acima de cem e recusado`() = runTest(despachante) {
        val vm = viewModel()
        vm.campoValor.setTextAndPlaceCursorAtEnd("100000")
        vm.campoSpreadCartao.setTextAndPlaceCursorAtEnd("10000")
        vm.calcular()
        assertNull(vm.visivel.erro, "100,00% é o limite aceito")
        vm.campoSpreadCartao.setTextAndPlaceCursorAtEnd("10001")
        vm.calcular()
        assertEquals(ErroEntrada.PARAMETRO_INVALIDO, vm.visivel.erro)
    }

    @Test
    fun `o opcional zero e recusado`() = runTest(despachante) {
        val vm = viewModel()
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.campoVet.setTextAndPlaceCursorAtEnd("0")
        vm.calcular()
        assertEquals(ErroEntrada.OPCIONAL_INVALIDO, vm.visivel.erro)
    }

    @Test
    fun `trocar de modo limpa o resultado do modo anterior`() = runTest(despachante) {
        val vm = viewModel()
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.calcular()
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.visivel.simulacao)

        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        assertNull(vm.visivel.simulacao)
        assertNull(vm.visivel.compraEmReais)
        vm.mudarModo(Modo.SIMULACAO)
        assertNull(vm.visivel.simulacao, "data, modo e moeda apagam o resultado de vez")
    }

    // A21 — a validade do resultado acompanha os campos, em qualquer momento (decisão 13 do operador).
    @Test
    fun `o resultado acompanha os campos em qualquer momento`() = runTest(despachante) {
        val vm = viewModel()
        vm.campoValor.edit { replace(0, length, "100000") }
        vm.calcular()
        testScheduler.advanceUntilIdle()
        val resultadoA = assertNotNull(vm.visivel.simulacao, "o resultado do primeiro cálculo aparece")
        assertEquals(BigDecimal("1000.00"), resultadoA.simulacao.entrada.valorOriginal)

        vm.campoValor.edit { replace(0, length, "200000") }
        assertNull(vm.visivel.simulacao, "outro número esconde o resultado")
        vm.calcular()
        testScheduler.advanceUntilIdle()
        val resultadoB = assertNotNull(vm.visivel.simulacao, "o resultado do segundo cálculo aparece")
        assertEquals(BigDecimal("2000.00"), resultadoB.simulacao.entrada.valorOriginal)

        // Nenhuma notificação de aplicação foi bombeada até aqui: a comparação não depende dela.
        vm.campoValor.edit { replace(0, length, "100000") }
        assertNull(vm.visivel.simulacao, "voltar ao número do primeiro cálculo não mostra o resultado do segundo")
        Snapshot.sendApplyNotifications()
        testScheduler.advanceUntilIdle()
        assertNull(vm.visivel.simulacao, "e continua escondido depois de bombear")

        vm.campoValor.edit { replace(0, length, "200000") }
        assertEquals(resultadoB, vm.visivel.simulacao, "voltar ao número calculado mostra o resultado, sem calcular")
    }

    @Test
    fun `o erro do formulario tambem acompanha os campos`() = runTest(despachante) {
        val vm = viewModel()
        vm.calcular()
        assertEquals(ErroEntrada.VALOR_INVALIDO, vm.visivel.erro)
        vm.campoValor.edit { replace(0, length, "1") }
        assertNull(vm.visivel.erro, "o erro é do número que foi calculado")
        vm.campoValor.edit { replace(0, length, "") }
        assertEquals(ErroEntrada.VALOR_INVALIDO, vm.visivel.erro, "voltar a ele mostra o erro de novo")
    }

    @Test
    fun `editar um numero durante o calculo nao cancela, e o resultado chega marcado com os numeros dele`() =
        runTest(despachante) {
            val vm = viewModel()
            vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
            vm.calcular()
            vm.campoValor.setTextAndPlaceCursorAtEnd("20000")
            assertFalse(vm.visivel.carregando, "o carregamento é do número antigo")
            testScheduler.advanceUntilIdle()
            assertNull(vm.visivel.simulacao)
            vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
            assertEquals(BigDecimal("100.00"), assertNotNull(vm.visivel.simulacao).simulacao.entrada.valorOriginal)
        }

    // Decisão 6 do operador: as chaves da 1.0.1 guardavam texto livre; são ignoradas e apagadas.
    @Test
    fun `as chaves da versao anterior sao ignoradas e apagadas`() {
        val salvo = SavedStateHandle(mapOf("valor" to "100", "iof" to "12.345678", "data" to "2026-09-17"))
        val vm = viewModel(salvo)
        assertEquals("", vm.campoValor.text.toString())
        assertEquals("", vm.campoIof.text.toString())
        assertFalse("valor" in salvo)
        assertFalse("iof" in salvo)
        assertEquals(LocalDate.of(2026, 9, 17), vm.estado.value.dataCompra, "a data continua na chave dela")
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
