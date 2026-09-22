package dev.lcv.calculadora.data

import dev.lcv.calculadora.calc.*
import dev.lcv.calculadora.data.cotacoes.*
import dev.lcv.calculadora.data.persistencia.*
import dev.lcv.calculadora.data.rede.*
import dev.lcv.calculadora.data.backtest.BacktestRepository
import dev.lcv.calculadora.data.simulacao.Simulador
import java.math.BigDecimal
import java.time.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** Audit-only contracts against real repositories and readers, fake transport/DAO. */
class AuditDataTest {
    private val day = LocalDate.of(2026, 9, 21)
    private val clock = Clock.fixed(Instant.parse("2026-09-21T15:00:00Z"), ZoneOffset.UTC)
    private fun repo(ptax: ProvedorPtax, spot: UltimoSpotEmMemoria = UltimoSpotEmMemoria()) =
        CotacoesRepository(ptax, ProvedorSpot { null }, PtaxCacheEmMemoria(), spot, clock)

    @Test fun provisionalEuroMustNotBecomePermanentClosingRate() = runTest {
        var body = """{"value":[{"cotacaoVenda":5.9,"tipoBoletim":"Abertura","dataHoraCotacao":"2026-09-21 10:00:00.0"}]}"""
        var calls = 0
        val olinda = object : BcbOlinda {
            override suspend fun cotacaoDolarDia(dataCotacao: String): Response<ResponseBody> = error("unused")
            override suspend fun cotacaoMoedaDia(moeda: String, dataCotacao: String): Response<ResponseBody> {
                calls++
                return Response.success(body.toResponseBody())
            }
        }
        val csv = object : BcbFechamento {
            override suspend fun csv(data: String): Response<ResponseBody> = Response.error(404, "".toResponseBody())
        }
        val repo = repo(ProvedorPtaxBcb(olinda, csv))
        val first = repo.ptax("EUR", day)
        body = """{"value":[{"cotacaoVenda":6.2,"tipoBoletim":"Fechamento","dataHoraCotacao":"2026-09-21 13:10:00.0"}]}"""
        val second = repo.ptax("EUR", day)
        println("PROOF_FROZEN_PTAX first=${first?.taxa} afterClosing=${second?.taxa} providerCalls=$calls availableClosing=6.2")
        assertEquals(0, BigDecimal("6.2").compareTo(second!!.taxa), "An opening bulletin must not be retained as closing PTAX")
    }

    @Test fun negativeCsvMustBeRejectedLikeNegativeJson() = runTest {
        val olinda = object : BcbOlinda {
            override suspend fun cotacaoDolarDia(dataCotacao: String): Response<ResponseBody> = Response.error(503, "".toResponseBody())
            override suspend fun cotacaoMoedaDia(moeda: String, dataCotacao: String): Response<ResponseBody> = cotacaoDolarDia(dataCotacao)
        }
        val csv = object : BcbFechamento {
            override suspend fun csv(data: String): Response<ResponseBody> = Response.success("21/09/2026;220;A;USD;5,0;-5,0;1;1".toResponseBody())
        }
        val quote = repo(ProvedorPtaxBcb(olinda, csv)).ptax("USD", day)
        println("PROOF_NEGATIVE_CSV quote=$quote")
        assertNull(quote, "Invalid fallback data must not become a permanent cached exchange rate")
    }

    @Test fun yearOldSavedSpotMustNotBeUsedWithoutFreshnessLimit() = runTest {
        val dao = UltimoSpotEmMemoria()
        dao.guardar(UltimoSpotEntity("USD", BigDecimal("3.0"), Instant.parse("2025-09-21T15:00:00Z").toEpochMilli()))
        val stale = repo(ProvedorPtax { _, _ -> null }, dao).ultimoSpotCalibrado("USD")
        println("PROOF_STALE_SPOT ageDays=365 returned=$stale")
        assertNull(stale, "Expired quotes need an explicit stale-data policy")
    }

    @Test fun futurePurchaseMustNotBeCalculatedAsObservedHistoricalPtax() = runTest {
        val quotes = repo(ProvedorPtax { _, date -> if (date == day) BigDecimal("5") else null })
        val result = Simulador(quotes, BacktestRepository(BacktestEmMemoria(), clock), clock)
            .simular(EntradaSimulacao("USD", BigDecimal("100"), day.plusDays(1)))
        println("PROOF_FUTURE date=${result.simulacao.entrada.dataCompra} quote=${(result.simulacao.cartao as? Modalidade.Suportada)?.dataCotacao}")
        assertTrue(result.simulacao.cartao is Modalidade.Indisponivel, "Future purchases must be rejected or explicitly modeled as projections")
    }

    @Test fun currentSpotMustNotGradeCalibrationAgainstYearsOldPtax() = runTest {
        val quotes = CotacoesRepository(
            ProvedorPtax { _, _ -> BigDecimal("2") },
            ProvedorSpot { CotacaoSpotBruta(BigDecimal("5"), FonteSpot.AWESOME_API) },
            PtaxCacheEmMemoria(), UltimoSpotEmMemoria(), clock,
        )
        val result = Simulador(quotes, BacktestRepository(BacktestEmMemoria(), clock), clock)
            .simular(EntradaSimulacao("USD", BigDecimal("100"), LocalDate.of(2010, 1, 4)))
        println("PROOF_BACKTEST date=2010-01-04 spotNow=5 historicalPtax=2 mapePercent=${result.backtest?.mapePercent} quality=${result.backtest?.qualidade}")
        assertNull(result.backtest, "Different time periods do not measure calibration error")
    }
}
