/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MelhorOpcaoTest {
    private val contexto = ContextoOperacional.em(Instant.parse("2026-03-23T15:30:00Z"))
    private val ptax = CotacaoPtax(dec("5"), LocalDate.of(2026, 3, 23))

    private fun simular(moeda: String = "USD", spot: String? = "5", vetSaldo: String? = null) = MotorCalculo.simular(
        EntradaSimulacao(moeda, dec("100"), LocalDate.of(2026, 3, 23), vetSaldo?.let(::dec)),
        contexto,
        ptax,
        spot?.let { CotacaoSpotBruta(dec(it), FonteSpot.AWESOME_API) },
        null,
    )

    @Test
    fun `conta global vence o cartao com a mesma taxa, porque o spread e menor`() {
        assertEquals(Opcao.CONTA_GLOBAL, melhorOpcao(simular()))
    }

    @Test
    fun `saldo existente barato vence os dois`() {
        assertEquals(Opcao.SALDO_EXISTENTE, melhorOpcao(simular(vetSaldo = "5.0")))
    }

    @Test
    fun `saldo existente caro perde`() {
        assertEquals(Opcao.CONTA_GLOBAL, melhorOpcao(simular(vetSaldo = "5.9")))
    }

    @Test
    fun `modalidade indisponivel nao concorre`() {
        assertEquals(Opcao.CARTAO, melhorOpcao(simular(moeda = "GBP")))
    }

    @Test
    fun `sem modalidade alguma nao ha melhor opcao`() {
        val s = MotorCalculo.simular(EntradaSimulacao("USD", dec("100"), LocalDate.of(2026, 3, 23)), contexto, null, null, null)
        assertNull(melhorOpcao(s))
    }

    @Test
    fun `validacao do formulario`() {
        assertEquals(ErroEntrada.VALOR_INVALIDO, validarEntrada("", temDataCompra = true, cobradoEmReais = false))
        assertEquals(ErroEntrada.VALOR_INVALIDO, validarEntrada("0", temDataCompra = true, cobradoEmReais = false))
        assertEquals(ErroEntrada.VALOR_INVALIDO, validarEntrada("-1", temDataCompra = true, cobradoEmReais = false))
        assertEquals(ErroEntrada.DATA_AUSENTE, validarEntrada("100", temDataCompra = false, cobradoEmReais = false))
        assertNull(validarEntrada("100", temDataCompra = false, cobradoEmReais = true))
        assertNull(validarEntrada("1.234,56", temDataCompra = true, cobradoEmReais = false))
    }
}
