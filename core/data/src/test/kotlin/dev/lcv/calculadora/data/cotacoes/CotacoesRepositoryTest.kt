/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.cotacoes

import dev.lcv.calculadora.calc.CotacaoSpotBruta
import dev.lcv.calculadora.calc.FonteSpot
import dev.lcv.calculadora.data.PtaxCacheEmMemoria
import dev.lcv.calculadora.data.RelogioFixo
import dev.lcv.calculadora.data.UltimoSpotEmMemoria
import dev.lcv.calculadora.data.assertDecimal
import dev.lcv.calculadora.data.dec
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class CotacoesRepositoryTest {
    private val relogio = RelogioFixo(Instant.parse("2026-09-18T15:00:00Z"))
    private val ptaxCache = PtaxCacheEmMemoria()
    private val ultimoSpot = UltimoSpotEmMemoria()
    private val quinta = LocalDate.of(2026, 9, 17)
    private val sabado = LocalDate.of(2026, 9, 19)
    private val ptaxPorDia = mutableMapOf<LocalDate, BigDecimal>()
    private var chamadasPtax = 0
    private var chamadasSpot = 0
    private var spotDaFonte: CotacaoSpotBruta? = CotacaoSpotBruta(dec("5.1434"), FonteSpot.AWESOME_API)

    private fun repositorio() = CotacoesRepository(
        provedorPtax = { _, data -> chamadasPtax++; ptaxPorDia[data] },
        provedorSpot = { chamadasSpot++; spotDaFonte },
        ptaxCache = ptaxCache,
        ultimoSpotDao = ultimoSpot,
        relogio = relogio,
    )

    @Test
    fun `PTAX do dia da compra quando existe, e vai para o cache`() = runTest {
        ptaxPorDia[quinta] = dec("5.1575")
        val ptax = assertNotNull(repositorio().ptax("USD", quinta))
        assertDecimal("5.1575", ptax.taxa)
        assertEquals(quinta, ptax.data)
        assertDecimal("5.1575", ptaxCache.linhas["USD" to quinta]?.taxa)
    }

    @Test
    fun `compra no sabado usa a PTAX de sexta - recua um dia por vez e guarda so o dia encontrado`() = runTest {
        val sexta = LocalDate.of(2026, 9, 18)
        ptaxPorDia[sexta] = dec("5.1575")
        val ptax = assertNotNull(repositorio().ptax("USD", sabado))
        assertEquals(sexta, ptax.data, "dia da cotacao efetivamente encontrada")
        assertEquals(2, chamadasPtax, "sabado e sexta")
        assertEquals(setOf("USD" to sexta), ptaxCache.linhas.keys)
    }

    @Test
    fun `cache responde antes da fonte`() = runTest {
        ptaxPorDia[quinta] = dec("5.1575")
        val repo = repositorio()
        repo.ptax("USD", quinta)
        repo.ptax("USD", quinta)
        assertEquals(1, chamadasPtax)
    }

    @Test
    fun `sete dias sem cotacao - null depois de exatamente sete tentativas`() = runTest {
        assertNull(repositorio().ptax("USD", sabado))
        assertEquals(CotacoesRepository.RECUO_MAXIMO_DIAS, chamadasPtax)
        assertEquals(7, chamadasPtax)
    }

    @Test
    fun `oitavo dia para tras nao e consultado mesmo que exista`() = runTest {
        ptaxPorDia[sabado.minusDays(7)] = dec("5.0")
        assertNull(repositorio().ptax("USD", sabado))
    }

    @Test
    fun `spot - memo de 60 s por moeda, e cada moeda tem o seu`() = runTest {
        val repo = repositorio()
        repo.spotBruta("USD")
        relogio.avancar(Duration.ofSeconds(59))
        repo.spotBruta("USD")
        assertEquals(1, chamadasSpot, "dentro da janela")
        repo.spotBruta("EUR")
        assertEquals(2, chamadasSpot, "outra moeda, outra consulta")
        relogio.avancar(Duration.ofSeconds(2))
        repo.spotBruta("USD")
        assertEquals(3, chamadasSpot, "61 s depois da primeira, consulta de novo")
    }

    @Test
    fun `spot - fonte sem resposta nao entra no memo`() = runTest {
        spotDaFonte = null
        val repo = repositorio()
        assertNull(repo.spotBruta("USD"))
        assertNull(repo.spotBruta("USD"))
        assertEquals(2, chamadasSpot)
    }

    @Test
    fun `ultimo spot calibrado - grava com o instante do relogio e le de volta`() = runTest {
        val repo = repositorio()
        assertNull(repo.ultimoSpotCalibrado("USD"))
        repo.guardarUltimoSpotCalibrado("USD", dec("5.140006"))
        assertDecimal("5.140006", repo.ultimoSpotCalibrado("USD"))
        assertEquals(relogio.millis(), ultimoSpot.linhas["USD"]?.obtidoEm)
    }
}
