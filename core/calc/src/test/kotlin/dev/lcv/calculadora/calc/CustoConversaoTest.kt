/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CustoConversaoTest {
    @Test
    fun `cartao - 100 USD a 5,00 com spread 5,5 por cento e IOF 3,5 por cento`() {
        // base 500,00; spread 27,50; base com spread 527,50; IOF 18,4625 → 18,46;
        // total 545,9625 → 545,96; VET 5,459625.
        val custo = calcularCusto(dec("100"), dec("5"), Parametros.SPREAD_CARTAO_PADRAO, Parametros.IOF_CARTAO_PADRAO)
        assertDecimal("500.00", custo.baseBrl)
        assertDecimal("27.50", custo.valorSpread)
        assertDecimal("18.46", custo.valorIof)
        assertDecimal("545.96", custo.valorTotalBrl)
        assertDecimal("5.459625", custo.vet)
    }

    @Test
    fun `IOF incide sobre a base ja acrescida do spread, nao sobre a base pura`() {
        val custo = calcularCusto(dec("1000"), dec("1"), dec("0.10"), dec("0.035"))
        // Sobre a base pura seria 35,00; sobre 1.100,00 é 38,50.
        assertDecimal("38.50", custo.valorIof)
        assertDecimal("1138.50", custo.valorTotalBrl)
    }

    @Test
    fun `sem spread e sem IOF o total e a base e o VET e a taxa`() {
        val custo = calcularCusto(dec("100"), dec("5"), dec("0"), dec("0"))
        assertDecimal("500.00", custo.valorTotalBrl)
        assertDecimal("5", custo.vet)
    }

    @Test
    fun `escalas de saida sao fixas - reais com duas casas e VET com seis`() {
        val custo = calcularCusto(dec("3"), dec("5.1234567"), dec("0.055"), dec("0.035"))
        assertEquals(2, custo.baseBrl.scale())
        assertEquals(2, custo.valorTotalBrl.scale())
        assertEquals(6, custo.vet.scale())
    }

    @Test
    fun `arredonda meio para cima na saida`() {
        // 1,005 × 1 = 1,005 → 1,01 (o ponto flutuante binário daria 1,00).
        val custo = calcularCusto(dec("1.005"), dec("1"), dec("0"), dec("0"))
        assertDecimal("1.01", custo.baseBrl)
    }

    @Test
    fun `valor original precisa ser positivo`() {
        assertFailsWith<IllegalArgumentException> { calcularCusto(dec("0"), dec("5"), dec("0.055"), dec("0.035")) }
        assertFailsWith<IllegalArgumentException> { calcularCusto(dec("-1"), dec("5"), dec("0.055"), dec("0.035")) }
    }
}
