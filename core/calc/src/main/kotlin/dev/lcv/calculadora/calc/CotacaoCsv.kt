/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal

/**
 * Leitura do CSV de fechamento do Banco Central.
 *
 * Layout: `Data;CodMoeda;Tipo(A/B);Sigla;TaxaCompra;TaxaVenda;ParidadeCompra;ParidadeVenda`.
 * A sigla fica na coluna 3 e a taxa de venda na coluna 5, com vírgula decimal.
 */
object CotacaoCsv {
    /** Taxa de venda em reais da [moeda], ou `null` se ausente ou ilegível. */
    fun taxaVenda(texto: String, moeda: String): BigDecimal? {
        for (linha in texto.lineSequence()) {
            val colunas = linha.split(';')
            if (colunas.size >= 6 && colunas[3].trim() == moeda) {
                return colunas[5].trim().replace(',', '.').toBigDecimalOrNull()
            }
        }
        return null
    }
}
