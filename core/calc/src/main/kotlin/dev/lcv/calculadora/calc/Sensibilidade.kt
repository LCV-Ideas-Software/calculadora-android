/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal

/**
 * Bandas determinísticas de sensibilidade para decisão rápida: o mesmo custo
 * recalculado com a taxa de câmbio, o spread e o IOF deslocados para os dois
 * lados. Taxas que ficariam negativas após o delta são fixadas em zero.
 */
data class BandasSensibilidade(
    val otimista: CustoConversao,
    val base: CustoConversao,
    val pessimista: CustoConversao,
) {
    companion object {
        /** ±1,00 % sobre a taxa de câmbio. */
        val DELTA_TAXA_CAMBIO: BigDecimal = BigDecimal("0.01")

        /** ±0,30 ponto percentual sobre o spread. */
        val DELTA_SPREAD: BigDecimal = BigDecimal("0.003")

        /** ±0,30 ponto percentual sobre o IOF. */
        val DELTA_IOF: BigDecimal = BigDecimal("0.003")
    }
}

fun calcularBandasSensibilidade(
    valorOriginal: BigDecimal,
    taxaCambio: BigDecimal,
    spread: BigDecimal,
    iof: BigDecimal,
): BandasSensibilidade {
    val um = BigDecimal.ONE
    return BandasSensibilidade(
        otimista = calcularCusto(
            valorOriginal,
            (taxaCambio * (um - BandasSensibilidade.DELTA_TAXA_CAMBIO)).semNegativo(),
            (spread - BandasSensibilidade.DELTA_SPREAD).semNegativo(),
            (iof - BandasSensibilidade.DELTA_IOF).semNegativo(),
        ),
        base = calcularCusto(valorOriginal, taxaCambio, spread, iof),
        pessimista = calcularCusto(
            valorOriginal,
            (taxaCambio * (um + BandasSensibilidade.DELTA_TAXA_CAMBIO)).semNegativo(),
            (spread + BandasSensibilidade.DELTA_SPREAD).semNegativo(),
            (iof + BandasSensibilidade.DELTA_IOF).semNegativo(),
        ),
    )
}

private fun BigDecimal.semNegativo(): BigDecimal = max(BigDecimal.ZERO)
