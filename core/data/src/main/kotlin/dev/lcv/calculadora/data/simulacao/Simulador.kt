/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.simulacao

import dev.lcv.calculadora.calc.ContextoOperacional
import dev.lcv.calculadora.calc.EntradaSimulacao
import dev.lcv.calculadora.calc.FonteSpot
import dev.lcv.calculadora.calc.Modalidade
import dev.lcv.calculadora.calc.Moedas
import dev.lcv.calculadora.calc.MotorCalculo
import dev.lcv.calculadora.calc.Simulacao
import dev.lcv.calculadora.data.backtest.BacktestRepository
import dev.lcv.calculadora.data.backtest.ResumoBacktest
import dev.lcv.calculadora.data.cotacoes.CotacoesRepository
import java.time.Clock
import java.time.LocalDate
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** A simulação e, quando houve observação nova, o resumo do backtest (como o produto web). */
data class ResultadoSimulacao(
    val simulacao: Simulacao,
    val backtest: ResumoBacktest?,
    val instanteSpot: Instant? = null,
)

/**
 * Único ponto de entrada da interface: obtém as cotações, roda o motor e
 * persiste o que a simulação produz — o último spot calibrado e a observação
 * do backtest. Reproduz a orquestração de `calcular.js`, sem servidor.
 */
class Simulador @Inject constructor(
    private val cotacoes: CotacoesRepository,
    private val backtest: BacktestRepository,
    private val relogio: Clock,
) {
    suspend fun simular(entrada: EntradaSimulacao): ResultadoSimulacao = coroutineScope {
        val contexto = ContextoOperacional.em(relogio.instant())
        val temContaGlobal = Moedas.temContaGlobal(entrada.moeda)
        val consultaPtax = async { withTimeoutOrNull(15_000) { cotacoes.ptax(entrada.moeda, entrada.dataCompra) } }
        val consultaSpot = async { if (temContaGlobal) withTimeoutOrNull(10_000) { cotacoes.spotBruta(entrada.moeda) } else null }
        val ptax = consultaPtax.await()
        val spotBruta = consultaSpot.await()
        val salvo = if (temContaGlobal) cotacoes.ultimoSpot(entrada.moeda) else null
        val ultimoSpot = salvo?.taxaCalibrada

        val simulacao = MotorCalculo.simular(entrada, contexto, ptax, spotBruta, ultimoSpot, salvo?.obtidoEm?.let(Instant::ofEpochMilli))

        val global = simulacao.global as? Modalidade.Suportada
        if (global != null && (global.fonteSpot == FonteSpot.AWESOME_API || global.fonteSpot == FonteSpot.YAHOO_FINANCE)) {
            cotacoes.guardarUltimoSpotCalibrado(entrada.moeda, global.taxaUtilizada, spotBruta?.instante ?: relogio.instant())
        }

        // Comparação diária, não avaliação de previsão: só fontes com instante
        // conhecido no mesmo dia da PTAX final. Contingências não são amostras.
        val erro = simulacao.erroBacktest
        val cartao = simulacao.cartao as? Modalidade.Suportada
        val diaSpot = spotBruta?.instante?.let { LocalDate.ofInstant(it, ContextoOperacional.FUSO_BRASILIA) }
        val previsaoReal = diaSpot != null && diaSpot == ptax?.data && diaSpot == entrada.dataCompra
        val resumo = if (erro != null && global != null && cartao != null && previsaoReal) {
            backtest.registrar(
                moeda = entrada.moeda,
                dataCompra = entrada.dataCompra,
                taxaPrevista = global.taxaUtilizada,
                taxaObservada = cartao.taxaUtilizada,
                erroPercentual = erro,
            )
            backtest.podar()
            backtest.resumo(entrada.parametros)
        } else {
            null
        }
        ResultadoSimulacao(simulacao, resumo, when (global?.fonteSpot) {
            FonteSpot.AWESOME_API, FonteSpot.YAHOO_FINANCE -> spotBruta?.instante
            FonteSpot.ULTIMO_SPOT_SALVO -> salvo?.obtidoEm?.let(Instant::ofEpochMilli)
            else -> null
        })
    }
}
