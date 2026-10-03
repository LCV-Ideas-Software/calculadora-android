/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formatação numérica no padrão brasileiro: ponto de milhar, vírgula decimal. */
object Formatacao {
    private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    private const val AUSENTE = "—"

    private fun formatador(casas: Int): DecimalFormat {
        val padrao = "#,##0." + "0".repeat(casas)
        return DecimalFormat(padrao, DecimalFormatSymbols.getInstance(PT_BR)).apply {
            roundingMode = Escalas.MODO
        }
    }

    /** Duas casas: `1234.56` → `"1.234,56"`. */
    fun reais(valor: BigDecimal?): String = valor?.let { formatador(2).format(it) } ?: AUSENTE

    /** Quatro casas: `5.734` → `"5,7340"`. */
    fun taxa(valor: BigDecimal?): String = valor?.let { formatador(4).format(it) } ?: AUSENTE

    /** Fração como porcentagem com duas casas: `0.0638` → `"6,38%"`. */
    fun percentual(fracao: BigDecimal?): String =
        fracao?.let { formatador(2).format(it * CEM) + "%" } ?: AUSENTE
}
