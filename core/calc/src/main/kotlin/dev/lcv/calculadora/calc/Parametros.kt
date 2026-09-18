/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal

/**
 * Parâmetros da simulação. Os padrões são constantes do aplicativo e reproduzem
 * o que o produto web aplica em produção (medido em 18/09/2026: os oito
 * parâmetros gravados no D1 são idênticos aos padrões do código).
 *
 * Todos os valores são frações (0.035 = 3,5 %), exceto os limiares do
 * backtest, que são porcentagens (1.0 = 1 %), como no produto web.
 */
data class Parametros(
    val iofCartao: BigDecimal = IOF_CARTAO_PADRAO,
    val iofGlobal: BigDecimal = IOF_GLOBAL_PADRAO,
    val spreadCartao: BigDecimal = SPREAD_CARTAO_PADRAO,
    val spreadGlobalAberto: BigDecimal = SPREAD_GLOBAL_ABERTO_PADRAO,
    val spreadGlobalFechado: BigDecimal = SPREAD_GLOBAL_FECHADO_PADRAO,
    val fatorCalibragemGlobal: BigDecimal = FATOR_CALIBRAGEM_GLOBAL_PADRAO,
    val backtestMapeBoaPercent: BigDecimal = BACKTEST_MAPE_BOA_PERCENT_PADRAO,
    val backtestMapeAtencaoPercent: BigDecimal = BACKTEST_MAPE_ATENCAO_PERCENT_PADRAO,
) {
    init {
        require(fatorCalibragemGlobal.signum() > 0) { "O fator de calibragem deve ser positivo." }
    }

    /** Spread da conta global vigente: `fechado` em plantão, `aberto` no horário de mercado. */
    fun spreadGlobal(plantao: Boolean): BigDecimal = if (plantao) spreadGlobalFechado else spreadGlobalAberto

    /**
     * Aplica as sobreposições que o usuário informa no formulário, em
     * porcentagem (3,5 informado = 0.035 aplicado). `null` mantém o padrão.
     * Um único IOF informado vale para o cartão e para a conta global, como no
     * produto web. Os limiares do backtest já são porcentagens e não se dividem.
     */
    fun comSobreposicoes(
        spreadCartaoPercent: BigDecimal? = null,
        iofPercent: BigDecimal? = null,
        spreadGlobalAbertoPercent: BigDecimal? = null,
        spreadGlobalFechadoPercent: BigDecimal? = null,
        backtestMapeBoaPercent: BigDecimal? = null,
        backtestMapeAtencaoPercent: BigDecimal? = null,
    ): Parametros = copy(
        spreadCartao = spreadCartaoPercent?.dePercentualParaFracao() ?: spreadCartao,
        iofCartao = iofPercent?.dePercentualParaFracao() ?: iofCartao,
        iofGlobal = iofPercent?.dePercentualParaFracao() ?: iofGlobal,
        spreadGlobalAberto = spreadGlobalAbertoPercent?.dePercentualParaFracao() ?: spreadGlobalAberto,
        spreadGlobalFechado = spreadGlobalFechadoPercent?.dePercentualParaFracao() ?: spreadGlobalFechado,
        backtestMapeBoaPercent = backtestMapeBoaPercent ?: this.backtestMapeBoaPercent,
        backtestMapeAtencaoPercent = backtestMapeAtencaoPercent ?: this.backtestMapeAtencaoPercent,
    )

    companion object {
        val IOF_CARTAO_PADRAO: BigDecimal = BigDecimal("0.035")
        val IOF_GLOBAL_PADRAO: BigDecimal = BigDecimal("0.035")
        val SPREAD_CARTAO_PADRAO: BigDecimal = BigDecimal("0.055")
        val SPREAD_GLOBAL_ABERTO_PADRAO: BigDecimal = BigDecimal("0.0078")
        val SPREAD_GLOBAL_FECHADO_PADRAO: BigDecimal = BigDecimal("0.0118")
        val FATOR_CALIBRAGEM_GLOBAL_PADRAO: BigDecimal = BigDecimal("0.99934")
        val BACKTEST_MAPE_BOA_PERCENT_PADRAO: BigDecimal = BigDecimal("1.0")
        val BACKTEST_MAPE_ATENCAO_PERCENT_PADRAO: BigDecimal = BigDecimal("2.0")

        val PADRAO: Parametros = Parametros()
    }
}

private fun BigDecimal.dePercentualParaFracao(): BigDecimal = dividirPor(CEM).stripTrailingZeros()
