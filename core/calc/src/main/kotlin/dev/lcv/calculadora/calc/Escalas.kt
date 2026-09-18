/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Escalas e arredondamento usados por todo o motor. Cada operação fixa a sua
 * escala explicitamente; nada herda o padrão da linguagem.
 */
internal object Escalas {
    /** Valores em reais: centavos. */
    const val REAIS = 2

    /** VET (valor efetivo total) e erro percentual do backtest. */
    const val TAXA = 6

    /** MAPE expresso em porcentagem. */
    const val MAPE_PERCENT = 4

    /** Precisão das divisões intermediárias, antes do arredondamento de saída. */
    val DIVISAO: MathContext = MathContext(34, RoundingMode.HALF_UP)

    val MODO: RoundingMode = RoundingMode.HALF_UP
}

internal fun BigDecimal.emReais(): BigDecimal = setScale(Escalas.REAIS, Escalas.MODO)

internal fun BigDecimal.comoTaxa(): BigDecimal = setScale(Escalas.TAXA, Escalas.MODO)

/** Divisão intermediária, com precisão alta; o arredondamento de saída vem depois. */
internal fun BigDecimal.dividirPor(divisor: BigDecimal): BigDecimal = divide(divisor, Escalas.DIVISAO)

/** Divisão cujo resultado é a própria saída: arredonda uma única vez, direto na escala de taxa. */
internal fun BigDecimal.dividirComoTaxa(divisor: BigDecimal): BigDecimal = divide(divisor, Escalas.TAXA, Escalas.MODO)

internal val CEM: BigDecimal = BigDecimal(100)
