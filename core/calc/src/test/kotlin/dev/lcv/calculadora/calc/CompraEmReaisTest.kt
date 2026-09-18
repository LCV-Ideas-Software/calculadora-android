/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CompraEmReaisTest {
    private val valor = dec("100")
    private val iof = dec("0.035")
    private val spread = dec("0.055")

    @Test
    fun `adquirencia local - sem IOF e sem spread`() {
        val r = analisarCompraEmReais(valor, iof, spread)
        assertDecimal("100.00", r.adquirenciaLocal.totalBrl)
        assertDecimal("0", r.adquirenciaLocal.custoAdicionalBrl)
        assertDecimal("0", r.adquirenciaLocal.custoAdicionalPercent)
    }

    @Test
    fun `DCC pura - so IOF sobre o valor em reais`() {
        val r = analisarCompraEmReais(valor, iof, spread)
        assertDecimal("103.50", r.dccPura.totalBrl)
        assertDecimal("3.50", r.dccPura.custoAdicionalBrl)
        assertDecimal("3.50", r.dccPura.custoAdicionalPercent)
    }

    @Test
    fun `dupla conversao - spread do emissor composto com IOF`() {
        // 100 × 1,055 × 1,035 = 109,1925 → 109,19
        val r = analisarCompraEmReais(valor, iof, spread)
        assertDecimal("109.19", r.duplaConversao.totalBrl)
        assertDecimal("9.19", r.duplaConversao.custoAdicionalBrl)
        assertDecimal("9.19", r.duplaConversao.custoAdicionalPercent)
    }

    @Test
    fun `diagnostico reverso classifica cada cenario pela fatura`() {
        assertEquals(
            CenarioCompraEmReais.ADQUIRENCIA_LOCAL,
            analisarCompraEmReais(valor, iof, spread, valorFaturaBrl = dec("100")).diagnostico?.cenarioProvavel,
        )
        val dcc = analisarCompraEmReais(valor, iof, spread, valorFaturaBrl = dec("103.5")).diagnostico
        assertNotNull(dcc)
        assertEquals(CenarioCompraEmReais.DCC_PURA, dcc.cenarioProvavel)
        assertDecimal("3.50", dcc.markupImplicitoPercent)
        assertEquals(
            CenarioCompraEmReais.DUPLA_CONVERSAO,
            analisarCompraEmReais(valor, iof, spread, valorFaturaBrl = dec("109.19")).diagnostico?.cenarioProvavel,
        )
    }

    @Test
    fun `diagnostico reverso e indeterminado fora das faixas`() {
        assertEquals(
            CenarioCompraEmReais.INDETERMINADO,
            analisarCompraEmReais(valor, iof, spread, valorFaturaBrl = dec("106")).diagnostico?.cenarioProvavel,
        )
    }

    @Test
    fun `tolerancia do diagnostico e meio ponto percentual`() {
        // 3,5 % + 0,5 p.p. = 4,0 % ainda é DCC; 4,01 % já não.
        assertEquals(
            CenarioCompraEmReais.DCC_PURA,
            analisarCompraEmReais(valor, iof, spread, valorFaturaBrl = dec("104.00")).diagnostico?.cenarioProvavel,
        )
        assertEquals(
            CenarioCompraEmReais.INDETERMINADO,
            analisarCompraEmReais(valor, iof, spread, valorFaturaBrl = dec("104.01")).diagnostico?.cenarioProvavel,
        )
    }

    @Test
    fun `custo adicional vem da expressao exata, nao do total ja arredondado`() {
        // 1,005 com IOF e spread zero: total 1,01 (arredondado), mas o acréscimo é 0,00 —
        // subtrair o principal do total arredondado daria 0,01 sem markup algum.
        val semTaxas = analisarCompraEmReais(dec("1.005"), dec("0"), dec("0"))
        assertDecimal("0.00", semTaxas.dccPura.custoAdicionalBrl)
        assertDecimal("0.00", semTaxas.duplaConversao.custoAdicionalBrl)
        // 100,005 × 3,5 % = 3,500175 → 3,50; pelo total arredondado (103,51 − 100,005) daria 3,51.
        val comIof = analisarCompraEmReais(dec("100.005"), dec("0.035"), dec("0"))
        assertDecimal("3.50", comIof.dccPura.custoAdicionalBrl)
        assertDecimal("103.51", comIof.dccPura.totalBrl)
    }

    @Test
    fun `sem fatura nao ha diagnostico`() {
        assertNull(analisarCompraEmReais(valor, iof, spread).diagnostico)
        assertNull(analisarCompraEmReais(valor, iof, spread, valorFaturaBrl = dec("0")).diagnostico)
    }

    @Test
    fun `valor em reais precisa ser positivo`() {
        assertFailsWith<IllegalArgumentException> { analisarCompraEmReais(dec("0"), iof, spread) }
        assertFailsWith<IllegalArgumentException> { analisarCompraEmReais(dec("-5"), iof, spread) }
    }
}
