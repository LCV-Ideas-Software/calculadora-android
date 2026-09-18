/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal

/**
 * Custo de converter [valorOriginal] em reais a uma taxa de câmbio, com spread
 * e IOF. O IOF incide sobre a base já acrescida do spread.
 *
 * A cadeia é calculada com precisão total e arredondada apenas na saída:
 * reais com duas casas, VET com seis.
 */
data class CustoConversao(
    val valorOriginal: BigDecimal,
    val taxaCambio: BigDecimal,
    val spread: BigDecimal,
    val iof: BigDecimal,
    val baseBrl: BigDecimal,
    val valorSpread: BigDecimal,
    val valorIof: BigDecimal,
    val valorTotalBrl: BigDecimal,
    /** Valor efetivo total: reais pagos por unidade da moeda. */
    val vet: BigDecimal,
)

fun calcularCusto(
    valorOriginal: BigDecimal,
    taxaCambio: BigDecimal,
    spread: BigDecimal,
    iof: BigDecimal,
): CustoConversao {
    require(valorOriginal.signum() > 0) { "O valor original deve ser positivo." }
    val base = valorOriginal * taxaCambio
    val valorSpread = base * spread
    val baseComSpread = base + valorSpread
    val valorIof = baseComSpread * iof
    val total = baseComSpread + valorIof
    return CustoConversao(
        valorOriginal = valorOriginal,
        taxaCambio = taxaCambio,
        spread = spread,
        iof = iof,
        baseBrl = base.emReais(),
        valorSpread = valorSpread.emReais(),
        valorIof = valorIof.emReais(),
        valorTotalBrl = total.emReais(),
        vet = total.dividirComoTaxa(valorOriginal),
    )
}
