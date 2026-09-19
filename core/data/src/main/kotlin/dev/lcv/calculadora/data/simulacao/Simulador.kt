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
import javax.inject.Inject

/** A simulação e, quando houve observação nova, o resumo do backtest (como o produto web). */
data class ResultadoSimulacao(
    val simulacao: Simulacao,
    val backtest: ResumoBacktest?,
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
    suspend fun simular(entrada: EntradaSimulacao): ResultadoSimulacao {
        val contexto = ContextoOperacional.em(relogio.instant())
        val ptax = cotacoes.ptax(entrada.moeda, entrada.dataCompra)
        val temContaGlobal = Moedas.temContaGlobal(entrada.moeda)
        val spotBruta = if (temContaGlobal) cotacoes.spotBruta(entrada.moeda) else null
        val ultimoSpot = if (temContaGlobal) cotacoes.ultimoSpotCalibrado(entrada.moeda) else null

        val simulacao = MotorCalculo.simular(entrada, contexto, ptax, spotBruta, ultimoSpot)

        val global = simulacao.global as? Modalidade.Suportada
        if (global != null && (global.fonteSpot == FonteSpot.AWESOME_API || global.fonteSpot == FonteSpot.YAHOO_FINANCE)) {
            cotacoes.guardarUltimoSpotCalibrado(entrada.moeda, global.taxaUtilizada)
        }

        // Só uma spot de verdade (de fonte ou a última salva) é uma previsão a
        // conferir contra a PTAX. Na contingência, prevista e observada são o
        // mesmo número: o "erro" zero não mede calibragem e só maquiaria o MAPE
        // — o produto web grava esse zero; porte, não clone.
        val erro = simulacao.erroBacktest
        val cartao = simulacao.cartao as? Modalidade.Suportada
        val previsaoReal = global?.fonteSpot != null && global.fonteSpot != FonteSpot.PTAX_CONTINGENCIA
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
        return ResultadoSimulacao(simulacao, resumo)
    }
}
