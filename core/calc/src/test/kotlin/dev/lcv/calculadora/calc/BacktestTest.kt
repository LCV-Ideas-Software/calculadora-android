/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BacktestTest {
    @Test
    fun `erro percentual e o erro absoluto relativo a observada`() {
        assertDecimal("0.02", Backtest.erroPercentual(dec("5.1"), dec("5.0")))
        assertDecimal("0.02", Backtest.erroPercentual(dec("4.9"), dec("5.0")))
    }

    @Test
    fun `erro percentual e nulo com observada nao positiva`() {
        assertNull(Backtest.erroPercentual(dec("5.1"), dec("0")))
        assertNull(Backtest.erroPercentual(dec("5.1"), dec("-1")))
    }

    @Test
    fun `MAPE e a media dos erros validos`() {
        assertDecimal("0.02", Backtest.mape(listOf(dec("0.01"), dec("0.03"), dec("0.02"))))
    }

    @Test
    fun `MAPE ignora negativos e e nulo sem base`() {
        assertNull(Backtest.mape(emptyList()))
        assertNull(Backtest.mape(listOf(dec("-0.01"))))
        assertDecimal("0.03", Backtest.mape(listOf(dec("-0.01"), dec("0.03"))))
    }

    @Test
    fun `MAPE arredonda uma unica vez, meio para cima, na sexta casa`() {
        // (0,000001 + 0,000002) / 2 = 0,0000015 → 0,000002
        assertDecimal("0.000002", Backtest.mape(listOf(dec("0.000001"), dec("0.000002"))))
    }

    @Test
    fun `MAPE em porcentagem com quatro casas`() {
        assertDecimal("2.0000", Backtest.mapePercent(dec("0.02")))
        assertEquals(4, Backtest.mapePercent(dec("0.02")).scale())
    }

    @Test
    fun `classificacao respeita as faixas padrao`() {
        assertEquals(QualidadeBacktest.EXCELENTE, Backtest.classificar(dec("0.8")))
        assertEquals(QualidadeBacktest.EXCELENTE, Backtest.classificar(dec("1.0")))
        assertEquals(QualidadeBacktest.BOA, Backtest.classificar(dec("1.4")))
        assertEquals(QualidadeBacktest.BOA, Backtest.classificar(dec("2.0")))
        assertEquals(QualidadeBacktest.ATENCAO, Backtest.classificar(dec("2.6")))
    }

    @Test
    fun `classificacao respeita faixas customizadas`() {
        assertEquals(QualidadeBacktest.EXCELENTE, Backtest.classificar(dec("1.4"), dec("1.5"), dec("2.5")))
        assertEquals(QualidadeBacktest.BOA, Backtest.classificar(dec("2.0"), dec("1.5"), dec("2.5")))
        assertEquals(QualidadeBacktest.ATENCAO, Backtest.classificar(dec("3.0"), dec("1.5"), dec("2.5")))
    }
}
