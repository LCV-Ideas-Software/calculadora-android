/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal

/**
 * Converte a entrada digitada em número, aceitando o formato brasileiro e o
 * norte-americano: o último separador presente é o decimal e o outro, se
 * houver, é o de milhar. `"1.234,56"`, `"1,234.56"`, `"5,5"` e `"5.5"` são
 * todos válidos. Devolve `null` para entrada vazia ou ilegível.
 */
fun parseNumeroLocalizado(texto: String): BigDecimal? {
    val s = texto.trim()
    if (s.isEmpty()) return null
    val ultimoPonto = s.lastIndexOf('.')
    val ultimaVirgula = s.lastIndexOf(',')
    val normalizado = when {
        ultimoPonto >= 0 && ultimaVirgula >= 0 ->
            if (ultimaVirgula > ultimoPonto) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        ultimaVirgula >= 0 -> s.replace(',', '.')
        else -> s
    }
    return normalizado.toBigDecimalOrNull()
}
