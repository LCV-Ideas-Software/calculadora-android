/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import kotlin.test.Test
import kotlin.test.assertTrue

class SensibilidadeTest {
    @Test
    fun `otimista custa menos que a base e pessimista custa mais`() {
        val bandas = calcularBandasSensibilidade(dec("100"), dec("5"), dec("0.01"), dec("0.035"))
        assertTrue(bandas.otimista.valorTotalBrl < bandas.base.valorTotalBrl)
        assertTrue(bandas.pessimista.valorTotalBrl > bandas.base.valorTotalBrl)
    }

    @Test
    fun `deltas aplicados - 1 por cento na taxa e 0,3 ponto no spread e no IOF`() {
        val bandas = calcularBandasSensibilidade(dec("100"), dec("5"), dec("0.01"), dec("0.035"))
        assertDecimal("4.95", bandas.otimista.taxaCambio)
        assertDecimal("5.05", bandas.pessimista.taxaCambio)
        assertDecimal("0.007", bandas.otimista.spread)
        assertDecimal("0.013", bandas.pessimista.spread)
        assertDecimal("0.032", bandas.otimista.iof)
        assertDecimal("0.038", bandas.pessimista.iof)
    }

    @Test
    fun `taxas que ficariam negativas apos o delta sao fixadas em zero`() {
        val bandas = calcularBandasSensibilidade(dec("100"), dec("5"), dec("0.001"), dec("0.001"))
        assertDecimal("0", bandas.otimista.spread)
        assertDecimal("0", bandas.otimista.iof)
    }
}
