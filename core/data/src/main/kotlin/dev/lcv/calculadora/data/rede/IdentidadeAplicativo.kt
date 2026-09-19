/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.rede

/**
 * Como o aplicativo se apresenta às fontes de cotação (spec, seção 5): o que
 * ele é, em que versão, e onde o dono da fonte descobre quem somos. O `:app`
 * fornece a versão a partir do próprio manifesto; este módulo não a conhece.
 */
data class IdentidadeAplicativo(val versao: String) {
    /** Valor do cabeçalho `User-Agent` (decisão do operador, 19/09/2026). */
    val userAgent: String
        get() = "calculadora-android/$versao (Android; +https://calculadora.lcv.dev)"
}
