/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Compara pelo valor numérico, indiferente à escala (`1.50` == `1.5`). */
internal fun assertDecimal(esperado: String, obtido: BigDecimal?, mensagem: String? = null) {
    assertNotNull(obtido, mensagem ?: "esperado $esperado, obtido null")
    assertTrue(
        BigDecimal(esperado).compareTo(obtido) == 0,
        (mensagem?.let { "$it: " } ?: "") + "esperado $esperado, obtido ${obtido.toPlainString()}",
    )
}

internal fun dec(valor: String): BigDecimal = BigDecimal(valor)
