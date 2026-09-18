/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal
import java.time.LocalDate

/** PTAX de venda do BCB para um dia: a taxa que o cartão de crédito usa. */
data class CotacaoPtax(
    val taxa: BigDecimal,
    /** Dia da cotação efetivamente encontrada (pode ser anterior à compra). */
    val data: LocalDate,
)

enum class FonteSpot {
    /** Taxa spot da AwesomeAPI, calibrada. */
    AWESOME_API,

    /** Contingência da spot: Yahoo Finance, calibrada. */
    YAHOO_FINANCE,

    /** Último spot calibrado guardado no aparelho, quando nenhuma fonte responde. */
    ULTIMO_SPOT_SALVO,

    /** Sem spot alguma: a conta global usa a própria PTAX do cartão. */
    PTAX_CONTINGENCIA,
}

/** Taxa spot bruta, como veio da fonte, ainda sem calibragem. */
data class CotacaoSpotBruta(val taxa: BigDecimal, val fonte: FonteSpot)

enum class MotivoIndisponibilidade {
    PTAX_INDISPONIVEL,
    CAMBIO_GLOBAL_INDISPONIVEL,
    MOEDA_SEM_CONTA_GLOBAL,
}

sealed interface Modalidade {
    data class Suportada(
        val taxaUtilizada: BigDecimal,
        val custo: CustoConversao,
        val dataCotacao: LocalDate? = null,
        val fonteSpot: FonteSpot? = null,
        val plantao: Boolean? = null,
    ) : Modalidade

    data class Indisponivel(val motivo: MotivoIndisponibilidade) : Modalidade
}

/** Conta global com saldo já carregado: custo histórico pelo VET informado. */
data class SaldoExistente(
    val vetInformado: BigDecimal,
    val valorTotalBrl: BigDecimal,
)

data class EntradaSimulacao(
    val moeda: String,
    val valorOriginal: BigDecimal,
    val dataCompra: LocalDate,
    val vetSaldoExistente: BigDecimal? = null,
    val parametros: Parametros = Parametros.PADRAO,
) {
    init {
        require(valorOriginal.signum() > 0) { "O valor original deve ser positivo." }
    }
}

data class Simulacao(
    val entrada: EntradaSimulacao,
    val contexto: ContextoOperacional,
    val cartao: Modalidade,
    val global: Modalidade,
    val saldoExistente: SaldoExistente?,
    val sensibilidadeCartao: BandasSensibilidade?,
    val sensibilidadeGlobal: BandasSensibilidade?,
    /** Erro do spot calibrado contra a PTAX, quando ambos existem: a observação do backtest. */
    val erroBacktest: BigDecimal?,
)

/**
 * Monta a simulação completa a partir de cotações já obtidas. Não faz rede nem
 * persistência: quem chama fornece a PTAX, a spot bruta e o último spot salvo,
 * e recebe o resultado e a observação a registrar.
 */
object MotorCalculo {
    fun simular(
        entrada: EntradaSimulacao,
        contexto: ContextoOperacional,
        ptax: CotacaoPtax?,
        spotBruta: CotacaoSpotBruta?,
        ultimoSpotCalibrado: BigDecimal?,
    ): Simulacao {
        val p = entrada.parametros

        val cartao: Modalidade = if (ptax != null) {
            Modalidade.Suportada(
                taxaUtilizada = ptax.taxa,
                custo = calcularCusto(entrada.valorOriginal, ptax.taxa, p.spreadCartao, p.iofCartao),
                dataCotacao = ptax.data,
            )
        } else {
            Modalidade.Indisponivel(MotivoIndisponibilidade.PTAX_INDISPONIVEL)
        }

        val global: Modalidade = if (!Moedas.temContaGlobal(entrada.moeda)) {
            Modalidade.Indisponivel(MotivoIndisponibilidade.MOEDA_SEM_CONTA_GLOBAL)
        } else {
            val taxaGlobal = resolverTaxaGlobal(spotBruta, ultimoSpotCalibrado, ptax?.taxa, p.fatorCalibragemGlobal)
            if (taxaGlobal == null) {
                Modalidade.Indisponivel(MotivoIndisponibilidade.CAMBIO_GLOBAL_INDISPONIVEL)
            } else {
                val spread = p.spreadGlobal(contexto.plantao)
                Modalidade.Suportada(
                    taxaUtilizada = taxaGlobal.taxa,
                    custo = calcularCusto(entrada.valorOriginal, taxaGlobal.taxa, spread, p.iofGlobal),
                    fonteSpot = taxaGlobal.fonte,
                    plantao = contexto.plantao,
                )
            }
        }

        val saldo = entrada.vetSaldoExistente
            ?.takeIf { Moedas.temContaGlobal(entrada.moeda) && it.signum() > 0 }
            ?.let { vet -> SaldoExistente(vetInformado = vet, valorTotalBrl = (entrada.valorOriginal * vet).emReais()) }

        val erro = if (cartao is Modalidade.Suportada && global is Modalidade.Suportada) {
            Backtest.erroPercentual(previsto = global.taxaUtilizada, observado = cartao.taxaUtilizada)
        } else {
            null
        }

        return Simulacao(
            entrada = entrada,
            contexto = contexto,
            cartao = cartao,
            global = global,
            saldoExistente = saldo,
            sensibilidadeCartao = (cartao as? Modalidade.Suportada)?.bandas(),
            sensibilidadeGlobal = (global as? Modalidade.Suportada)?.bandas(),
            erroBacktest = erro,
        )
    }

    /** Taxa da conta global e a fonte de onde veio, já calibrada. */
    data class TaxaGlobal(val taxa: BigDecimal, val fonte: FonteSpot)

    /**
     * Ordem de preferência: spot bruta calibrada (AwesomeAPI ou Yahoo) →
     * último spot calibrado salvo → PTAX do cartão como contingência.
     */
    fun resolverTaxaGlobal(
        spotBruta: CotacaoSpotBruta?,
        ultimoSpotCalibrado: BigDecimal?,
        ptax: BigDecimal?,
        fatorCalibragem: BigDecimal,
    ): TaxaGlobal? = when {
        spotBruta != null -> TaxaGlobal(spotBruta.taxa * fatorCalibragem, spotBruta.fonte)
        ultimoSpotCalibrado != null -> TaxaGlobal(ultimoSpotCalibrado, FonteSpot.ULTIMO_SPOT_SALVO)
        ptax != null -> TaxaGlobal(ptax, FonteSpot.PTAX_CONTINGENCIA)
        else -> null
    }

    private fun Modalidade.Suportada.bandas(): BandasSensibilidade =
        calcularBandasSensibilidade(custo.valorOriginal, custo.taxaCambio, custo.spread, custo.iof)
}
