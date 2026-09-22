/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.backtest

import dev.lcv.calculadora.calc.Parametros
import dev.lcv.calculadora.calc.QualidadeBacktest
import dev.lcv.calculadora.data.BacktestEmMemoria
import dev.lcv.calculadora.data.RelogioFixo
import dev.lcv.calculadora.data.assertDecimal
import dev.lcv.calculadora.data.dec
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class BacktestRepositoryTest {
    private val relogio = RelogioFixo(Instant.parse("2026-09-18T15:00:00Z"))
    private val dao = BacktestEmMemoria()
    private val repo = BacktestRepository(dao, relogio)
    private val dia = LocalDate.of(2026, 9, 18)

    private suspend fun registrar(erro: String) =
        repo.registrar("USD", dia.minusDays(dao.linhas.size.toLong()), taxaPrevista = dec("5.14"), taxaObservada = dec("5.1575"), erroPercentual = dec(erro))

    @Test
    fun `serie vazia - resumo sem MAPE nem qualidade`() = runTest {
        val resumo = repo.resumo()
        assertEquals(0, resumo.observacoes)
        assertNull(resumo.mape)
        assertNull(resumo.mapePercent)
        assertNull(resumo.qualidade)
        assertEquals(emptyList(), resumo.ultimas)
    }

    @Test
    fun `registrar grava o instante do relogio e os valores exatos`() = runTest {
        registrar("0.003394")
        val linha = dao.linhas.single()
        assertEquals(relogio.millis(), linha.criadoEm)
        assertEquals("USD", linha.moeda)
        assertEquals(dia, linha.dataCompra)
        assertDecimal("5.14", linha.taxaPrevista)
        assertDecimal("5.1575", linha.taxaObservada)
        assertDecimal("0.003394", linha.erroPercentual)
    }

    @Test
    fun `resumo - MAPE dos ultimos sete dias, em fracao e em porcentagem, classificado`() = runTest {
        registrar("0.01")
        relogio.avancar(Duration.ofHours(1))
        registrar("0.05")
        val resumo = repo.resumo()
        assertEquals(2, resumo.observacoes)
        assertDecimal("0.03", resumo.mape)
        assertDecimal("3.0000", resumo.mapePercent)
        assertEquals(QualidadeBacktest.ATENCAO, resumo.qualidade, "3 % > limiar de atencao padrao (2 %)")
        assertEquals(dao.linhas.map { it.id }.reversed(), resumo.ultimas.map { it.id }, "mais recente primeiro")
    }

    @Test
    fun `resumo - janela de sete dias e limite de 200 vao ao banco, nao a memoria`() = runTest {
        registrar("0.01")
        relogio.avancar(Duration.ofDays(7).plusSeconds(1))
        registrar("0.05")
        val resumo = repo.resumo()
        assertEquals(1, resumo.observacoes, "a de oito dias atras ficou fora")
        assertDecimal("0.05", resumo.mape)
        assertEquals(relogio.millis() - Duration.ofDays(7).toMillis() to 200, dao.ultimaConsulta)
    }

    @Test
    fun `resumo - limiares vem dos parametros da simulacao`() = runTest {
        registrar("0.02")
        val folgados = Parametros.PADRAO.comSobreposicoes(backtestMapeBoaPercent = dec("3"), backtestMapeAtencaoPercent = dec("5"))
        assertEquals(QualidadeBacktest.EXCELENTE, repo.resumo(folgados).qualidade, "2 % <= 3 %")
        assertEquals(QualidadeBacktest.BOA, repo.resumo().qualidade, "2 % > 1 % e nao > 2 %: limiares padrao")
    }

    @Test
    fun `ultimas - no maximo vinte`() = runTest {
        repeat(25) {
            registrar("0.01")
            relogio.avancar(Duration.ofMinutes(1))
        }
        val resumo = repo.resumo()
        assertEquals(25, resumo.observacoes)
        assertEquals(20, resumo.ultimas.size)
    }

    @Test
    fun `podar - apaga o que tem mais de trinta dias e devolve a contagem`() = runTest {
        registrar("0.01")
        relogio.avancar(Duration.ofDays(30).plusSeconds(1))
        registrar("0.01")
        assertEquals(1, repo.podar())
        assertEquals(1, dao.linhas.size)
        assertEquals(relogio.millis() - Duration.ofDays(30).toMillis(), dao.ultimaPoda)
    }
}
