/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal

/**
 * Auto-monitoramento da calibragem: a cada simulação, o erro entre a taxa
 * prevista (spot calibrado) e a observada (PTAX) é registrado; o MAPE dos
 * últimos sete dias classifica a qualidade da calibragem.
 */
enum class QualidadeBacktest { EXCELENTE, BOA, ATENCAO }

object Backtest {
    /** Erro absoluto relativo, com seis casas. `null` se a observada não for positiva. */
    fun erroPercentual(previsto: BigDecimal, observado: BigDecimal): BigDecimal? {
        if (observado.signum() <= 0) return null
        return (previsto - observado).abs().dividirComoTaxa(observado)
    }

    /** Média dos erros não negativos, com seis casas. `null` sem base válida. */
    fun mape(errosPercentuais: List<BigDecimal>): BigDecimal? {
        val validos = errosPercentuais.filter { it.signum() >= 0 }
        if (validos.isEmpty()) return null
        val soma = validos.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        return soma.dividirComoTaxa(BigDecimal(validos.size))
    }

    /** MAPE em porcentagem, com quatro casas. */
    fun mapePercent(mape: BigDecimal): BigDecimal = (mape * CEM).setScale(Escalas.MAPE_PERCENT, Escalas.MODO)

    fun classificar(
        mapePercent: BigDecimal,
        limiarBoaPercent: BigDecimal = Parametros.BACKTEST_MAPE_BOA_PERCENT_PADRAO,
        limiarAtencaoPercent: BigDecimal = Parametros.BACKTEST_MAPE_ATENCAO_PERCENT_PADRAO,
    ): QualidadeBacktest = when {
        mapePercent > limiarAtencaoPercent -> QualidadeBacktest.ATENCAO
        mapePercent > limiarBoaPercent -> QualidadeBacktest.BOA
        else -> QualidadeBacktest.EXCELENTE
    }
}
