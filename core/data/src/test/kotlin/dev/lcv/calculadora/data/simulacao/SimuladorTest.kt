/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.simulacao

import dev.lcv.calculadora.calc.CotacaoSpotBruta
import dev.lcv.calculadora.calc.EntradaSimulacao
import dev.lcv.calculadora.calc.FonteSpot
import dev.lcv.calculadora.calc.Modalidade
import dev.lcv.calculadora.calc.MotivoIndisponibilidade
import dev.lcv.calculadora.calc.Parametros
import dev.lcv.calculadora.data.BacktestEmMemoria
import dev.lcv.calculadora.data.PtaxCacheEmMemoria
import dev.lcv.calculadora.data.RelogioFixo
import dev.lcv.calculadora.data.UltimoSpotEmMemoria
import dev.lcv.calculadora.data.assertDecimal
import dev.lcv.calculadora.data.backtest.BacktestRepository
import dev.lcv.calculadora.data.cotacoes.CotacoesRepository
import dev.lcv.calculadora.data.dec
import dev.lcv.calculadora.data.persistencia.UltimoSpotEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * O fluxo inteiro sobre repositórios reais com fontes e bancos falsos:
 * quinta-feira 18/09/2026, 12:00 em Brasília (mercado aberto).
 */
class SimuladorTest {
    private val relogio = RelogioFixo(Instant.parse("2026-09-18T15:00:00Z"))
    private val ptaxCache = PtaxCacheEmMemoria()
    private val ultimoSpot = UltimoSpotEmMemoria()
    private val backtestDao = BacktestEmMemoria()
    private val dia = LocalDate.of(2026, 9, 18)
    private var ptax: BigDecimal? = dec("5.1575")
    private var spot: CotacaoSpotBruta? = CotacaoSpotBruta(dec("5.1434"), FonteSpot.AWESOME_API, relogio.instant())
    private var chamadasSpot = 0

    private fun simulador(): Simulador {
        val cotacoes = CotacoesRepository(
            provedorPtax = { _, _ -> ptax },
            provedorSpot = { chamadasSpot++; spot },
            ptaxCache = ptaxCache,
            ultimoSpotDao = ultimoSpot,
            relogio = relogio,
        )
        return Simulador(cotacoes, BacktestRepository(backtestDao, relogio), relogio)
    }

    private fun entrada(moeda: String = "USD") = EntradaSimulacao(moeda, dec("100"), dia)

    @Test
    fun `caminho feliz - cartao pela PTAX, conta global pela spot calibrada, spot salva e observacao registrada`() = runTest {
        val resultado = simulador().simular(entrada())
        val s = resultado.simulacao
        assertTrue(!s.contexto.plantao, "12:00 de quinta em Brasilia e mercado aberto")

        val cartao = assertIs<Modalidade.Suportada>(s.cartao)
        assertDecimal("5.1575", cartao.taxaUtilizada)
        assertEquals(dia, cartao.dataCotacao)

        val global = assertIs<Modalidade.Suportada>(s.global)
        assertEquals(FonteSpot.AWESOME_API, global.fonteSpot)
        val esperada = dec("5.1434") * Parametros.FATOR_CALIBRAGEM_GLOBAL_PADRAO
        assertDecimal(esperada.toPlainString(), global.taxaUtilizada)
        assertDecimal(esperada.toPlainString(), ultimoSpot.linhas["USD"]?.taxaCalibrada, "o spot calibrado vira o ultimo spot salvo")

        assertEquals(1, backtestDao.linhas.size)
        val obs = backtestDao.linhas.single()
        assertDecimal(esperada.toPlainString(), obs.taxaPrevista)
        assertDecimal("5.1575", obs.taxaObservada)
        assertDecimal(s.erroBacktest!!.toPlainString(), obs.erroPercentual)
        val resumo = assertNotNull(resultado.backtest)
        assertEquals(1, resumo.observacoes)
        assertNotNull(resumo.qualidade)
    }

    @Test
    fun `sem spot - usa o ultimo spot recente sem regravar nem registrar observacao`() = runTest {
        spot = null
        ultimoSpot.linhas["USD"] = UltimoSpotEntity("USD", dec("5.10"), obtidoEm = relogio.millis() - 1000)
        val resultado = simulador().simular(entrada())
        val global = assertIs<Modalidade.Suportada>(resultado.simulacao.global)
        assertEquals(FonteSpot.ULTIMO_SPOT_SALVO, global.fonteSpot)
        assertDecimal("5.10", global.taxaUtilizada)
        assertEquals(relogio.millis() - 1000, ultimoSpot.linhas["USD"]?.obtidoEm, "nao regravado")
        assertEquals(0, backtestDao.linhas.size)
        assertNull(resultado.backtest)
    }

    @Test
    fun `sem spot e sem ultimo salvo - PTAX de contingencia, erro zero por construcao, observacao nao entra`() = runTest {
        spot = null
        val resultado = simulador().simular(entrada())
        val global = assertIs<Modalidade.Suportada>(resultado.simulacao.global)
        assertEquals(FonteSpot.PTAX_CONTINGENCIA, global.fonteSpot)
        assertTrue(ultimoSpot.linhas.isEmpty(), "contingencia nao vira ultimo spot")
        // O motor calcula erro zero (previsto == observado); o produto web gravaria
        // esse zero. Porte, nao clone: uma observacao que nada mede nao entra.
        assertEquals(0, backtestDao.linhas.size)
        assertNull(resultado.backtest)
    }

    @Test
    fun `sem PTAX - cartao indisponivel, sem observacao e sem resumo`() = runTest {
        ptax = null
        val resultado = simulador().simular(entrada())
        assertEquals(MotivoIndisponibilidade.PTAX_INDISPONIVEL, assertIs<Modalidade.Indisponivel>(resultado.simulacao.cartao).motivo)
        assertEquals(0, backtestDao.linhas.size)
        assertNull(resultado.backtest)
    }

    @Test
    fun `libra - sem conta global, nenhuma fonte de spot e consultada`() = runTest {
        val resultado = simulador().simular(entrada("GBP"))
        assertEquals(MotivoIndisponibilidade.MOEDA_SEM_CONTA_GLOBAL, assertIs<Modalidade.Indisponivel>(resultado.simulacao.global).motivo)
        assertEquals(0, chamadasSpot)
        assertNull(resultado.backtest)
    }

    @Test fun `repetir simulacao nao multiplica amostras nem rejuvenesce a cotacao`() = runTest {
        val motor = simulador()
        motor.simular(entrada())
        val instante = ultimoSpot.linhas.getValue("USD").obtidoEm
        relogio.avancar(java.time.Duration.ofSeconds(30))
        motor.simular(entrada())
        assertEquals(1, backtestDao.linhas.size)
        assertEquals(instante, ultimoSpot.linhas.getValue("USD").obtidoEm)
    }

    @Test fun `spot sem instante nao gera amostra`() = runTest {
        spot = spot!!.copy(instante = null)
        assertNull(simulador().simular(entrada()).backtest)
        assertTrue(backtestDao.linhas.isEmpty())
    }
}
