/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

object Moedas {
    /** Moedas com suporte completo no aplicativo. */
    val SUPORTADAS: List<String> = listOf("USD", "EUR", "GBP")

    /** Moedas que a conta global opera nativamente. */
    val CONTA_GLOBAL: List<String> = listOf("USD", "EUR")

    /** Moedas com PTAX diária no serviço Olinda do BCB; as demais vêm do CSV de fechamento. */
    val OLINDA: List<String> = listOf("USD", "EUR", "AUD", "CAD", "CHF", "DKK", "GBP", "JPY", "NOK", "SEK")

    private val SIMBOLOS: Map<String, String> = mapOf(
        "USD" to "US$",
        "EUR" to "€",
        "GBP" to "£",
        "BRL" to "R$",
    )

    fun isSuportada(codigo: String): Boolean = codigo in SUPORTADAS

    fun temContaGlobal(codigo: String): Boolean = codigo in CONTA_GLOBAL

    fun simbolo(codigo: String): String = SIMBOLOS[codigo] ?: codigo
}
