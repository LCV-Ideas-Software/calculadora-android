package dev.lcv.calculadora.ui

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

/** Audit-only regression specifications. Failing assertions expose current defects. */
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

    @Test fun editsDuringRequestMustNotRestoreOldResult() = runTest(dispatcher) {
        val vm = vm()
        vm.mudarValor("100")
        vm.calcular()
        runCurrent()
        vm.mudarValor("200")
        advanceUntilIdle()
        println("PROOF_RACE form=${vm.estado.value.valor} result=${vm.estado.value.simulacao?.simulacao?.entrada?.valorOriginal}")
        assertNull(vm.estado.value.simulacao, "A response for 100 must not be attached to the edited 200 form")
    }

    @Test fun modeChangeDuringRequestMustNotRestoreForeignCurrencyResult() = runTest(dispatcher) {
        val vm = vm()
        vm.mudarValor("100")
        vm.calcular()
        runCurrent()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        advanceUntilIdle()
        println("PROOF_MODE_RACE mode=${vm.estado.value.modo} foreignResult=${vm.estado.value.simulacao != null}")
        assertNull(vm.estado.value.simulacao)
    }

    @Test fun extremeExponentMustBeRejectedBeforeArithmetic() {
        val vm = vm()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.mudarValor("1e2147483647")
        val error = runCatching { vm.calcular() }.exceptionOrNull()
        println("PROOF_EXPONENT exception=${error?.javaClass?.name} message=${error?.message}")
        assertNull(error, "An editable/pasteable number must not escape the UI callback as an exception")
        assertNotNull(vm.estado.value.erro)
    }

    @Test fun negativeIofMustNotProduceNegativeBill() {
        val vm = vm()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.mudarValor("1000")
        vm.mudarIof("-200")
        vm.calcular()
        println("PROOF_NEGATIVE_IOF total=${vm.estado.value.compraEmReais?.dccPura?.totalBrl} error=${vm.estado.value.erro}")
        assertNotNull(vm.estado.value.erro, "A negative tax outside the domain must be rejected")
    }

    @Test fun malformedOptionalValueMustNotSilentlyUseDefaults() {
        val vm = vm()
        vm.mudarModo(Modo.COBRADO_EM_REAIS)
        vm.mudarValor("1000")
        vm.mudarIof("3,,5")
        vm.calcular()
        println("PROOF_BAD_OPTIONAL iof=3,,5 total=${vm.estado.value.compraEmReais?.dccPura?.totalBrl} error=${vm.estado.value.erro}")
        assertNotNull(vm.estado.value.erro, "Nonempty malformed overrides differ from blank defaults")
    }

    @Test fun restoreInputsWithoutRestoringResultsOrLoading() = runTest(dispatcher) {
        val handle = androidx.lifecycle.SavedStateHandle()
        val first = vm(handle)
        first.mudarValor("123,45")
        first.mudarIof("4,2")
        first.mudarData(LocalDate.of(2026, 9, 18))
        first.calcular()
        val snapshot = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = vm(androidx.lifecycle.SavedStateHandle(snapshot))
        assertEquals("123,45", restored.estado.value.valor)
        assertEquals("4,2", restored.estado.value.iofPercent)
        assertEquals(LocalDate.of(2026, 9, 18), restored.estado.value.dataCompra)
        assertFalse(restored.estado.value.carregando)
        assertNull(restored.estado.value.simulacao)
        first.mudarValor("")
    }

    @Test fun futureDateAndInvalidOptionalAreRejected() {
        val vm = vm()
        vm.mudarValor("100")
        vm.mudarData(LocalDate.of(2026, 9, 22))
        vm.calcular()
        assertEquals(ErroEntrada.DATA_FUTURA, vm.estado.value.erro)
        vm.mudarData(LocalDate.of(2026, 9, 21))
        vm.mudarVetSaldo("abc")
        vm.calcular()
        assertEquals(ErroEntrada.OPCIONAL_INVALIDO, vm.estado.value.erro)
    }

    @Test fun secondCalculationWinsAfterEditingPendingRequest() = runTest(dispatcher) {
        val vm = vm()
        vm.mudarValor("100")
        vm.calcular()
        runCurrent()
        vm.mudarValor("200")
        assertFalse(vm.estado.value.carregando)
        vm.calcular()
        advanceUntilIdle()
        assertEquals(BigDecimal("200"), vm.estado.value.simulacao!!.simulacao.entrada.valorOriginal)
    }
}
