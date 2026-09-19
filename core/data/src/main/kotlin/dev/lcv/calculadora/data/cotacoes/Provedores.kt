/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.cotacoes

import dev.lcv.calculadora.calc.CotacaoCsv
import dev.lcv.calculadora.calc.CotacaoSpotBruta
import dev.lcv.calculadora.calc.FonteSpot
import dev.lcv.calculadora.calc.Moedas
import dev.lcv.calculadora.data.rede.AwesomeApi
import dev.lcv.calculadora.data.rede.BcbFechamento
import dev.lcv.calculadora.data.rede.BcbOlinda
import dev.lcv.calculadora.data.rede.Leitores
import dev.lcv.calculadora.data.rede.YahooFinance
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.ResponseBody
import retrofit2.Response

/** A PTAX de venda de uma moeda num dia, ou `null` se aquele dia não tem cotação. */
fun interface ProvedorPtax {
    suspend fun ptaxDoDia(moeda: String, data: LocalDate): BigDecimal?
}

/** A taxa spot bruta (sem calibragem) e a fonte de onde veio, ou `null` se nenhuma respondeu. */
fun interface ProvedorSpot {
    suspend fun spotBruta(moeda: String): CotacaoSpotBruta?
}

/**
 * PTAX pelo Banco Central: Olinda primeiro; se a Olinda estiver indisponível
 * (erro, tempo esgotado, resposta ilegível), o CSV de fechamento do mesmo dia.
 * No produto web o CSV só servia às moedas fora da lista da Olinda — para USD,
 * EUR e GBP era caminho morto; aqui é contingência real. Um dia que a Olinda
 * responde sem boletim não tem PTAX (fim de semana, feriado): devolve `null`
 * sem consultar o CSV, e o chamador recua um dia.
 */
class ProvedorPtaxBcb @Inject constructor(
    private val olinda: BcbOlinda,
    private val fechamento: BcbFechamento,
) : ProvedorPtax {
    override suspend fun ptaxDoDia(moeda: String, data: LocalDate): BigDecimal? =
        when (val leitura = semFalha { pelaOlinda(moeda, data) } ?: Olinda.Indisponivel) {
            is Olinda.Cotacao -> leitura.taxa
            Olinda.DiaSemCotacao -> null
            Olinda.Indisponivel -> semFalha { peloCsv(moeda, data) }
        }

    private suspend fun pelaOlinda(moeda: String, data: LocalDate): Olinda {
        if (moeda !in Moedas.OLINDA) return Olinda.Indisponivel
        val dataBacen = "'" + data.format(FORMATO_OLINDA) + "'"
        val resposta =
            if (moeda == "USD") olinda.cotacaoDolarDia(dataBacen) else olinda.cotacaoMoedaDia("'$moeda'", dataBacen)
        val corpo = resposta.corpoOuNull() ?: return Olinda.Indisponivel
        val boletins = Leitores.olindaBoletins(corpo) ?: return Olinda.Indisponivel
        if (boletins.isEmpty()) return Olinda.DiaSemCotacao
        val taxa =
            if (moeda == "USD") Leitores.cotacaoVendaDolar(boletins) else Leitores.cotacaoVendaFechamento(boletins)
        return taxa?.let(Olinda::Cotacao) ?: Olinda.Indisponivel
    }

    private suspend fun peloCsv(moeda: String, data: LocalDate): BigDecimal? {
        val corpo = fechamento.csv(data.format(FORMATO_CSV)).corpoOuNull() ?: return null
        return CotacaoCsv.taxaVenda(corpo, moeda)
    }

    private sealed interface Olinda {
        data class Cotacao(val taxa: BigDecimal) : Olinda

        data object DiaSemCotacao : Olinda

        data object Indisponivel : Olinda
    }

    private companion object {
        val FORMATO_OLINDA: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
        val FORMATO_CSV: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}

/**
 * Spot pela AwesomeAPI, com o Yahoo Finance como contingência (spec, seção
 * 10). Devolve a taxa bruta; a calibragem é do motor.
 */
class ProvedorSpotWeb @Inject constructor(
    private val awesome: AwesomeApi,
    private val yahoo: YahooFinance,
) : ProvedorSpot {
    override suspend fun spotBruta(moeda: String): CotacaoSpotBruta? {
        semFalha { awesome.ultima("$moeda-BRL").corpoOuNull()?.let { Leitores.awesome(it, moeda) } }
            ?.let { return CotacaoSpotBruta(it, FonteSpot.AWESOME_API) }
        val simbolo = if (moeda == "USD") "BRL=X" else "${moeda}BRL=X"
        return semFalha { yahoo.grafico(simbolo).corpoOuNull()?.let(Leitores::yahoo) }
            ?.let { CotacaoSpotBruta(it, FonteSpot.YAHOO_FINANCE) }
    }
}

/** O corpo de uma resposta 2xx, ou `null` para qualquer outra (cujo corpo de erro é fechado). */
internal fun Response<ResponseBody>.corpoOuNull(): String? =
    if (isSuccessful) body()?.use { it.string() } else { errorBody()?.close(); null }

/**
 * Como `runCatching`, mas o cancelamento da corrotina passa adiante: cancelar
 * uma simulação não é "fonte indisponível" e não pode disparar a contingência.
 * Qualquer outra falha (rede, tempo esgotado, corpo ilegível) vira `null`.
 */
internal inline fun <T> semFalha(bloco: () -> T): T? =
    try {
        bloco()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
