/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import kotlin.test.Test
import kotlin.test.assertNull

class CotacaoCsvTest {
    // Layout real do CSV de fechamento do BCB:
    // Data;CodMoeda;Tipo(A/B);Sigla;TaxaCompra;TaxaVenda;ParidadeCompra;ParidadeVenda
    private val csv = listOf(
        "08/07/2026;741;A;MXN;0,29230000;0,29250000;17,62310000;17,63360000",
        "08/07/2026;978;B;ARS;0,00460000;0,00461000;1234,00000000;1235,00000000",
        "08/07/2026;790;A;GBP;7,40000000;7,41000000;1,35000000;1,35100000",
    ).joinToString("\n")

    @Test
    fun `extrai a taxa de venda pela sigla`() {
        assertDecimal("0.2925", CotacaoCsv.taxaVenda(csv, "MXN"))
        assertDecimal("7.41", CotacaoCsv.taxaVenda(csv, "GBP"))
    }

    @Test
    fun `funciona para moeda tipo B`() {
        assertDecimal("0.00461", CotacaoCsv.taxaVenda(csv, "ARS"))
    }

    @Test
    fun `nulo para moeda ausente, CSV vazio ou valor ilegivel`() {
        assertNull(CotacaoCsv.taxaVenda(csv, "JPY"))
        assertNull(CotacaoCsv.taxaVenda("", "USD"))
        assertNull(CotacaoCsv.taxaVenda("08/07/2026;220;A;USD;x;y;z;w", "USD"))
    }

    @Test
    fun `aceita quebras de linha CRLF`() {
        assertDecimal("7.41", CotacaoCsv.taxaVenda(csv.replace("\n", "\r\n"), "GBP"))
    }
}
