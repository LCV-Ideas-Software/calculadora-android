/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal

/**
 * Decimal sem expoente: até 12 algarismos inteiros e 8 decimais.
 * Aceita vírgula ou ponto decimal; agrupamento exige grupos de três e ambos
 * os separadores (1.234,56 ou 1,234.56). Um separador único é sempre decimal.
 */
fun parseNumeroLocalizado(texto: String): BigDecimal? {
    if (texto.length > 40) return null
    val s = texto.trim()
    val simples = Regex("[+-]?[0-9]{1,12}([.,][0-9]{1,8})?")
    val brasileiro = Regex("[+-]?[0-9]{1,3}(\\.[0-9]{3}){1,3},[0-9]{1,8}")
    val americano = Regex("[+-]?[0-9]{1,3}(,[0-9]{3}){1,3}\\.[0-9]{1,8}")
    val normalizado = when {
        simples.matches(s) -> s.replace(',', '.')
        brasileiro.matches(s) -> s.replace(".", "").replace(',', '.')
        americano.matches(s) -> s.replace(",", "")
        else -> return null
    }
    return normalizado.toBigDecimalOrNull()
}
