/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.backtest

import dev.lcv.calculadora.calc.Backtest
import dev.lcv.calculadora.calc.Parametros
import dev.lcv.calculadora.calc.QualidadeBacktest
import dev.lcv.calculadora.data.persistencia.BacktestDao
import dev.lcv.calculadora.data.persistencia.ObservacaoBacktestEntity
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** O que o produto web devolve em `backtest`: a janela de sete dias resumida. */
data class ResumoBacktest(
    val observacoes: Int,
    /** MAPE como fração (`0.0123`), `null` sem observação. */
    val mape: BigDecimal?,
    /** MAPE em porcentagem com quatro casas, `null` sem observação. */
    val mapePercent: BigDecimal?,
    /** `null` sem observação. */
    val qualidade: QualidadeBacktest?,
    val ultimas: List<ObservacaoBacktestEntity>,
)

/**
 * Série do backtest no aparelho (spec, seção 3): alimentada pelas próprias
 * simulações, resumida sobre os últimos sete dias e podada aos trinta.
 */
@Singleton
class BacktestRepository @Inject constructor(
    private val dao: BacktestDao,
    private val relogio: Clock,
) {
    suspend fun registrar(
        moeda: String,
        dataCompra: LocalDate,
        taxaPrevista: BigDecimal,
        taxaObservada: BigDecimal,
        erroPercentual: BigDecimal,
    ) {
        dao.inserir(
            ObservacaoBacktestEntity(
                criadoEm = relogio.millis(),
                moeda = moeda,
                dataCompra = dataCompra,
                taxaPrevista = taxaPrevista,
                taxaObservada = taxaObservada,
                erroPercentual = erroPercentual,
            ),
        )
    }

    /** Resumo dos últimos sete dias (até [LIMITE_OBSERVACOES] observações), classificado pelos limiares de [parametros]. */
    suspend fun resumo(parametros: Parametros = Parametros.PADRAO): ResumoBacktest {
        val desde = relogio.millis() - JANELA.toMillis()
        val observacoes = dao.desde(desde, LIMITE_OBSERVACOES)
        val mape = Backtest.mape(observacoes.map { it.erroPercentual })
        val mapePercent = mape?.let(Backtest::mapePercent)
        return ResumoBacktest(
            observacoes = observacoes.size,
            mape = mape,
            mapePercent = mapePercent,
            qualidade = mapePercent?.let {
                Backtest.classificar(it, parametros.backtestMapeBoaPercent, parametros.backtestMapeAtencaoPercent)
            },
            ultimas = observacoes.take(ULTIMAS),
        )
    }

    /** Apaga as observações com mais de [RETENCAO]. Devolve quantas saíram. */
    suspend fun podar(): Int = dao.apagarAnterioresA(relogio.millis() - RETENCAO.toMillis())

    companion object {
        val JANELA: Duration = Duration.ofDays(7)
        val RETENCAO: Duration = Duration.ofDays(30)
        const val LIMITE_OBSERVACOES = 200
        const val ULTIMAS = 20
    }
}
