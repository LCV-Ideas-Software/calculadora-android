package dev.lcv.calculadora.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import dev.lcv.calculadora.calc.*
import dev.lcv.calculadora.data.backtest.BacktestRepository
import dev.lcv.calculadora.data.cotacoes.*
import dev.lcv.calculadora.data.persistencia.*
import dev.lcv.calculadora.data.simulacao.Simulador
import java.math.BigDecimal
import java.time.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

/**
 * Audit-only regression specifications. Failing assertions expose current defects.
 *
 * Since CALANDR-27 the numeric fields hold raw digits, so text such as "-200", "3,,5" or "1e2147483647" can only
 * reach them through the field's input transformation ([digitarComoUsuario]); each test keeps its original intent
 * against what that transformation lets through. The process-death restore of the fields is the device test A16.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-09-21T15:00:00Z"), ZoneOffset.UTC)
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun vm(salvo: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()): SimulacaoViewModel {
        val cache = object : PtaxCacheDao {
            override suspend fun buscar(moeda: String, data: LocalDate): PtaxCacheEntity? = null
            override suspend fun guardar(entidade: PtaxCacheEntity) {}
        }
        val spot = object : UltimoSpotDao {
            override suspend fun buscar(moeda: String): UltimoSpotEntity? = null
            override suspend fun guardar(entidade: UltimoSpotEntity) {}
        }
        val backtest = object : BacktestDao {
            override suspend fun inserir(observacao: ObservacaoBacktestEntity) {}
            override suspend fun desde(desde: Long, limite: Int) = emptyList<ObservacaoBacktestEntity>()
            override suspend fun apagarAnterioresA(antesDe: Long) = 0
        }
        val quotes = CotacoesRepository(
            ProvedorPtax { _, _ -> delay(1000); BigDecimal("5") },
            ProvedorSpot { CotacaoSpotBruta(BigDecimal("5"), FonteSpot.AWESOME_API) },
            cache, spot, clock,
        )
        return SimulacaoViewModel(Simulador(quotes, BacktestRepository(backtest, clock), clock), clock, salvo)
    }

    /** What the screen shows: the stored state checked against the current fields. */
    private val SimulacaoViewModel.shown: EstadoTela get() = estado.value.visivelCom(entradasAtuais())

    @Test fun editsDuringRequestMustNotRestoreOldResult() = runTest(dispatcher) {
        val vm = vm()
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.calcular()
        runCurrent()
        vm.campoValor.setTextAndPlaceCursorAtEnd("20000")
        advanceUntilIdle()
        println("PROOF_RACE form=${vm.campoValor.text} result=${vm.shown.simulacao?.simulacao?.entrada?.valorOriginal}")
        assertNull(vm.shown.simulacao, "A response for 100 must not be attached to the edited 200 form")
    }

    @Test fun modeChangeDuringRequestMustNotRestoreForeignCurrencyResult() = runTest(dispatcher) {
        val vm = vm()
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.calcular()
        runCurrent()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        advanceUntilIdle()
        println("PROOF_MODE_RACE mode=${vm.estado.value.modo} foreignResult=${vm.shown.simulacao != null}")
        assertNull(vm.shown.simulacao)
    }

    @Test fun extremeExponentCannotReachArithmetic() {
        val vm = vm()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.campoValor.digitarComoUsuario("1e2147483647")
        assertEquals("12147483647", vm.campoValor.text.toString(), "only the digits reach the field")
        val error = runCatching { vm.calcular() }.exceptionOrNull()
        println("PROOF_EXPONENT exception=${error?.javaClass?.name} message=${error?.message}")
        assertNull(error, "An editable/pasteable number must not escape the UI callback as an exception")
        assertNotNull(vm.shown.compraEmReais)
    }

    @Test fun negativeIofMustNotProduceNegativeBill() {
        val vm = vm()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.campoValor.setTextAndPlaceCursorAtEnd("100000")
        vm.campoIof.digitarComoUsuario("-200", TipoNumerico.PERCENTUAL)
        assertEquals("200", vm.campoIof.text.toString(), "the sign never reaches the field")
        vm.calcular()
        val total = vm.shown.compraEmReais?.dccPura?.totalBrl
        println("PROOF_NEGATIVE_IOF total=$total error=${vm.shown.erro}")
        assertEquals(BigDecimal("1020.00"), total, "2,00% of IOF, never a negative bill")
    }

    @Test fun malformedOptionalValueMustNotSilentlyUseDefaults() {
        val vm = vm()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.campoValor.setTextAndPlaceCursorAtEnd("100000")
        vm.campoIof.digitarComoUsuario("3,,5", TipoNumerico.PERCENTUAL)
        vm.calcular()
        val total = vm.shown.compraEmReais?.dccPura?.totalBrl
        println("PROOF_BAD_OPTIONAL iof=3,,5 total=$total error=${vm.shown.erro}")
        // Digits only (operator decision 1): "3,,5" is 0,35%, a defined value that differs from the 3,5% default.
        assertEquals(BigDecimal("1003.50"), total, "Nonempty overrides differ from blank defaults")
    }

    @Test fun restoreInputsWithoutRestoringResultsOrLoading() = runTest(dispatcher) {
        val handle = androidx.lifecycle.SavedStateHandle()
        val first = vm(handle)
        first.campoValor.setTextAndPlaceCursorAtEnd("12345")
        first.mudarData(LocalDate.of(2026, 9, 18))
        first.calcular()
        val snapshot = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = vm(androidx.lifecycle.SavedStateHandle(snapshot))
        assertEquals(LocalDate.of(2026, 9, 18), restored.estado.value.dataCompra)
        assertFalse(restored.shown.carregando)
        assertNull(restored.shown.simulacao)
    }

    @Test fun futureDateAndInvalidOptionalAreRejected() {
        val vm = vm()
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.mudarData(LocalDate.of(2026, 9, 22))
        vm.calcular()
        assertEquals(ErroEntrada.DATA_FUTURA, vm.shown.erro)
        vm.mudarData(LocalDate.of(2026, 9, 21))
        vm.campoVet.digitarComoUsuario("abc0", TipoNumerico.TAXA)
        vm.calcular()
        assertEquals(ErroEntrada.OPCIONAL_INVALIDO, vm.shown.erro)
    }

    @Test fun secondCalculationWinsAfterEditingPendingRequest() = runTest(dispatcher) {
        val vm = vm()
        vm.campoValor.setTextAndPlaceCursorAtEnd("10000")
        vm.calcular()
        runCurrent()
        vm.campoValor.setTextAndPlaceCursorAtEnd("20000")
        assertFalse(vm.shown.carregando)
        vm.calcular()
        advanceUntilIdle()
        assertEquals(BigDecimal("200.00"), assertNotNull(vm.shown.simulacao).simulacao.entrada.valorOriginal)
    }
}
