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
    fun taxaVenda(texto: String, moeda: String, data: java.time.LocalDate? = null): BigDecimal? {
        for (linha in texto.lineSequence()) {
            val colunas = linha.split(';')
            if (colunas.size >= 6 && colunas[3].trim() == moeda) {
                if (data != null && colunas[0].trim() != data.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))) return null
                return parseNumeroLocalizado(colunas[5])?.takeIf { it.signum() > 0 }
            }
        }
        return null
    }
}
