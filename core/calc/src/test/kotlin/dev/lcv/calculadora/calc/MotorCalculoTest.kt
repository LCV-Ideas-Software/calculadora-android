/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MotorCalculoTest {
    private val diaUtilAberto = ContextoOperacional.em(Instant.parse("2026-03-23T15:30:00Z"))
    private val domingo = ContextoOperacional.em(Instant.parse("2026-03-22T15:30:00Z"))
    private val dataCompra = LocalDate.of(2026, 3, 23)
    private val ptax = CotacaoPtax(dec("5"), dataCompra)

    private fun entrada(moeda: String = "USD", vetSaldo: String? = null) =
        EntradaSimulacao(moeda, dec("100"), dataCompra, vetSaldo?.let(::dec))

    @Test
    fun `cartao usa a PTAX com spread e IOF do cartao`() {
        val s = MotorCalculo.simular(entrada(), diaUtilAberto, ptax, null, null)
        val cartao = assertIs<Modalidade.Suportada>(s.cartao)
        assertDecimal("5", cartao.taxaUtilizada)
        assertEquals(dataCompra, cartao.dataCotacao)
        assertDecimal("545.96", cartao.custo.valorTotalBrl)
        assertDecimal("5.459625", cartao.custo.vet)
    }

    @Test
    fun `conta global calibra a spot bruta e usa o spread de mercado aberto`() {
        val s = MotorCalculo.simular(entrada(), diaUtilAberto, ptax, CotacaoSpotBruta(dec("5"), FonteSpot.AWESOME_API), null)
        val global = assertIs<Modalidade.Suportada>(s.global)
        // 5 × 0,99934 = 4,9967
        assertDecimal("4.9967", global.taxaUtilizada)
        assertEquals(FonteSpot.AWESOME_API, global.fonteSpot)
        assertEquals(false, global.plantao)
        assertDecimal("0.0078", global.custo.spread)
        // base 499,67; spread 3,897426; base com spread 503,567426; IOF 17,62485991; total 521,19228591 → 521,19
        assertDecimal("521.19", global.custo.valorTotalBrl)
    }

    @Test
    fun `em plantao a conta global usa o spread fechado`() {
        val s = MotorCalculo.simular(entrada(), domingo, ptax, CotacaoSpotBruta(dec("5"), FonteSpot.YAHOO_FINANCE), null)
        val global = assertIs<Modalidade.Suportada>(s.global)
        assertEquals(true, global.plantao)
        assertDecimal("0.0118", global.custo.spread)
        assertEquals(FonteSpot.YAHOO_FINANCE, global.fonteSpot)
    }

    @Test
    fun `spot bruta da fonte vence o ultimo spot salvo`() {
        val s = MotorCalculo.simular(entrada(), diaUtilAberto, ptax, CotacaoSpotBruta(dec("5.1"), FonteSpot.AWESOME_API), dec("4.9967"))
        val global = assertIs<Modalidade.Suportada>(s.global)
        // 5,1 × 0,99934 = 5,096634
        assertDecimal("5.096634", global.taxaUtilizada)
        assertEquals(FonteSpot.AWESOME_API, global.fonteSpot)
    }

    @Test
    fun `sem spot, usa o ultimo spot salvo, ja calibrado, sem calibrar de novo`() {
        val s = MotorCalculo.simular(entrada(), diaUtilAberto, ptax, null, dec("4.9967"))
        val global = assertIs<Modalidade.Suportada>(s.global)
        assertDecimal("4.9967", global.taxaUtilizada)
        assertEquals(FonteSpot.ULTIMO_SPOT_SALVO, global.fonteSpot)
    }

    @Test
    fun `sem spot e sem spot salvo, a PTAX e a contingencia da conta global`() {
        val s = MotorCalculo.simular(entrada(), diaUtilAberto, ptax, null, null)
        val global = assertIs<Modalidade.Suportada>(s.global)
        assertDecimal("5", global.taxaUtilizada)
        assertEquals(FonteSpot.PTAX_CONTINGENCIA, global.fonteSpot)
    }

    @Test
    fun `sem cotacao alguma, cartao e conta global ficam indisponiveis`() {
        val s = MotorCalculo.simular(entrada(), diaUtilAberto, null, null, null)
        assertEquals(Modalidade.Indisponivel(MotivoIndisponibilidade.PTAX_INDISPONIVEL), s.cartao)
        assertEquals(Modalidade.Indisponivel(MotivoIndisponibilidade.CAMBIO_GLOBAL_INDISPONIVEL), s.global)
        assertNull(s.sensibilidadeCartao)
        assertNull(s.erroBacktest)
    }

    @Test
    fun `conta global so opera em dolar e euro`() {
        val s = MotorCalculo.simular(entrada("GBP", vetSaldo = "6.5"), diaUtilAberto, ptax, CotacaoSpotBruta(dec("5"), FonteSpot.AWESOME_API), null)
        assertEquals(Modalidade.Indisponivel(MotivoIndisponibilidade.MOEDA_SEM_CONTA_GLOBAL), s.global)
        assertIs<Modalidade.Suportada>(s.cartao)
        assertNull(s.saldoExistente)
    }

    @Test
    fun `saldo existente e o custo historico pelo VET informado`() {
        val s = MotorCalculo.simular(entrada(vetSaldo = "5.2"), diaUtilAberto, ptax, null, null)
        val saldo = assertNotNull(s.saldoExistente)
        assertDecimal("5.2", saldo.vetInformado)
        assertDecimal("520.00", saldo.valorTotalBrl)
    }

    @Test
    fun `VET de saldo nao positivo e ignorado`() {
        assertNull(MotorCalculo.simular(entrada(vetSaldo = "0"), diaUtilAberto, ptax, null, null).saldoExistente)
    }

    @Test
    fun `erro do backtest compara spot calibrada com PTAX`() {
        val s = MotorCalculo.simular(entrada(), diaUtilAberto, ptax, CotacaoSpotBruta(dec("5"), FonteSpot.AWESOME_API), null)
        // |4,9967 − 5| / 5 = 0,00066
        assertDecimal("0.00066", s.erroBacktest)
    }

    @Test
    fun `sensibilidade acompanha cada modalidade suportada`() {
        val s = MotorCalculo.simular(entrada(), diaUtilAberto, ptax, CotacaoSpotBruta(dec("5"), FonteSpot.AWESOME_API), null)
        val cartao = assertNotNull(s.sensibilidadeCartao)
        val global = assertNotNull(s.sensibilidadeGlobal)
        assertDecimal("545.96", cartao.base.valorTotalBrl)
        assertDecimal("521.19", global.base.valorTotalBrl)
        assertTrue(cartao.otimista.valorTotalBrl < cartao.base.valorTotalBrl)
    }

    @Test
    fun `sobreposicoes do usuario chegam ao calculo`() {
        val e = entrada().copy(parametros = Parametros.PADRAO.comSobreposicoes(spreadCartaoPercent = dec("0"), iofPercent = dec("0")))
        val s = MotorCalculo.simular(e, diaUtilAberto, ptax, null, null)
        val cartao = assertIs<Modalidade.Suportada>(s.cartao)
        assertDecimal("500.00", cartao.custo.valorTotalBrl)
        assertDecimal("5", cartao.custo.vet)
    }
}
