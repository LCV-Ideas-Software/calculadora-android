/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NumeroLocalizadoTest {
    @Test
    fun `formato brasileiro`() {
        assertDecimal("1234.56", parseNumeroLocalizado("1.234,56"))
        assertDecimal("5.5", parseNumeroLocalizado("5,5"))
        assertDecimal("1234567.89", parseNumeroLocalizado("1.234.567,89"))
    }

    @Test
    fun `formato norte-americano`() {
        assertDecimal("1234.56", parseNumeroLocalizado("1,234.56"))
        assertDecimal("5.5", parseNumeroLocalizado("5.5"))
    }

    @Test
    fun `sem separador e com espacos`() {
        assertDecimal("100", parseNumeroLocalizado(" 100 "))
    }

    @Test
    fun `entrada vazia ou ilegivel e nula`() {
        assertNull(parseNumeroLocalizado(""))
        assertNull(parseNumeroLocalizado("   "))
        assertNull(parseNumeroLocalizado("abc"))
        assertNull(parseNumeroLocalizado("1,2,3"))
    }

    @Test
    fun `formatacao brasileira`() {
        assertEquals("1.234,56", Formatacao.reais(dec("1234.56")))
        assertEquals("1.234,57", Formatacao.reais(dec("1234.565")))
        assertEquals("5,7340", Formatacao.taxa(dec("5.734")))
        assertEquals("6,38%", Formatacao.percentual(dec("0.0638")))
        assertEquals("—", Formatacao.reais(null))
    }
}
