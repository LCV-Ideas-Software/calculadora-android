/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParametrosTest {
    @Test
    fun `padroes reproduzem os parametros vigentes do produto web`() {
        val p = Parametros.PADRAO
        assertDecimal("0.035", p.iofCartao)
        assertDecimal("0.035", p.iofGlobal)
        assertDecimal("0.055", p.spreadCartao)
        assertDecimal("0.0078", p.spreadGlobalAberto)
        assertDecimal("0.0118", p.spreadGlobalFechado)
        assertDecimal("0.99934", p.fatorCalibragemGlobal)
        assertDecimal("1.0", p.backtestMapeBoaPercent)
        assertDecimal("2.0", p.backtestMapeAtencaoPercent)
    }

    @Test
    fun `spread da conta global depende do plantao`() {
        assertDecimal("0.0078", Parametros.PADRAO.spreadGlobal(plantao = false))
        assertDecimal("0.0118", Parametros.PADRAO.spreadGlobal(plantao = true))
    }

    @Test
    fun `sobreposicoes em porcentagem viram fracao`() {
        val p = Parametros.PADRAO.comSobreposicoes(spreadCartaoPercent = dec("3.5"), spreadGlobalAbertoPercent = dec("0.5"))
        assertDecimal("0.035", p.spreadCartao)
        assertDecimal("0.005", p.spreadGlobalAberto)
        // O que não foi informado fica no padrão.
        assertDecimal("0.0118", p.spreadGlobalFechado)
        assertDecimal("0.035", p.iofCartao)
    }

    @Test
    fun `um unico IOF informado vale para cartao e conta global`() {
        val p = Parametros.PADRAO.comSobreposicoes(iofPercent = dec("1.1"))
        assertDecimal("0.011", p.iofCartao)
        assertDecimal("0.011", p.iofGlobal)
    }

    // A tela mostra um único padrão de IOF "(cartão e Conta Global)", lido de IOF_CARTAO_PADRAO; se as duas
    // constantes se separarem, esse texto passa a mentir sobre a Conta Global (decisão 11, CALANDR-27).
    @Test
    fun `o padrao de IOF e o mesmo para cartao e conta global`() {
        assertEquals(Parametros.IOF_CARTAO_PADRAO, Parametros.IOF_GLOBAL_PADRAO)
    }

    @Test
    fun `limiares do backtest ja sao porcentagens e nao se dividem`() {
        val p = Parametros.PADRAO.comSobreposicoes(backtestMapeBoaPercent = dec("1.25"), backtestMapeAtencaoPercent = dec("2.75"))
        assertDecimal("1.25", p.backtestMapeBoaPercent)
        assertDecimal("2.75", p.backtestMapeAtencaoPercent)
    }

    @Test
    fun `fator de calibragem precisa ser positivo`() {
        assertFailsWith<IllegalArgumentException> { Parametros(fatorCalibragemGlobal = dec("0")) }
        assertFailsWith<IllegalArgumentException> { Parametros(fatorCalibragemGlobal = dec("-0.5")) }
    }
}
