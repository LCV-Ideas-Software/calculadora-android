/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.rede

import dev.lcv.calculadora.data.assertDecimal
import dev.lcv.calculadora.data.fixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LeitoresTest {
    @Test
    fun `dolar - um boletim por dia, cotacaoVenda exata`() {
        val boletins = assertNotNull(Leitores.olindaBoletins(fixture("olinda-dolar-2026-09-18.json")))
        assertEquals(1, boletins.size)
        assertDecimal("5.15750", Leitores.cotacaoVendaDolar(boletins))
    }

    @Test
    fun `dolar - sabado vem com value vazio, que e dia sem cotacao e nao falha`() {
        val boletins = assertNotNull(Leitores.olindaBoletins(fixture("olinda-dolar-2026-09-13-sabado.json")))
        assertEquals(emptyList(), boletins)
        assertNull(Leitores.cotacaoVendaDolar(boletins))
    }

    @Test
    fun `moeda - escolhe o boletim Fechamento PTAX entre os cinco do dia`() {
        val boletins = assertNotNull(Leitores.olindaBoletins(fixture("olinda-eur-2026-09-18.json")))
        assertEquals(5, boletins.size)
        assertDecimal("5.91260", Leitores.cotacaoVendaFechamento(boletins), "fechamento, nao a abertura 5.9005")
    }

    @Test
    fun `moeda - sem boletim de fechamento nao ha PTAX final`() {
        val corpo = """{"value":[{"cotacaoVenda":1.10,"tipoBoletim":"Abertura"},{"cotacaoVenda":1.20,"tipoBoletim":"Intermediário"}]}"""
        assertNull(Leitores.cotacaoVendaFechamento(assertNotNull(Leitores.olindaBoletins(corpo))))
    }

    @Test
    fun `olinda - corpo que nao e a resposta da Olinda e ilegivel, nao dia sem cotacao`() {
        assertNull(Leitores.olindaBoletins("<html>manutencao</html>"))
        assertNull(Leitores.olindaBoletins("""{"erro":"x"}"""))
        assertNull(Leitores.olindaBoletins("""{"value":"nao e lista"}"""))
    }

    @Test
    fun `awesome - bid do par da moeda, lido como texto`() {
        assertDecimal("5.1434", Leitores.awesome(fixture("awesome-usd-brl.json"), "USD"))
        assertNull(Leitores.awesome(fixture("awesome-usd-brl.json"), "EUR"), "par ausente")
    }

    @Test
    fun `yahoo - regularMarketPrice do primeiro resultado`() {
        assertDecimal("5.1421", Leitores.yahoo(fixture("yahoo-brl-x.json")))
        assertNull(Leitores.yahoo("""{"chart":{"result":[]}}"""))
        assertNull(Leitores.yahoo("""{"chart":{"result":null,"error":{"code":"Not Found"}}}"""))
    }

    @Test
    fun `numero nao positivo ou ausente e null, nunca zero`() {
        assertNull(Leitores.cotacaoVendaDolar(assertNotNull(Leitores.olindaBoletins("""{"value":[{"cotacaoVenda":0}]}"""))))
        assertNull(Leitores.cotacaoVendaDolar(assertNotNull(Leitores.olindaBoletins("""{"value":[{"cotacaoVenda":-1}]}"""))))
        assertNull(Leitores.cotacaoVendaDolar(assertNotNull(Leitores.olindaBoletins("""{"value":[{"cotacaoCompra":5.1}]}"""))))
        assertNull(Leitores.awesome("""{"USDBRL":{"bid":"abc"}}""", "USD"))
    }
}
