/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.cotacoes

import dev.lcv.calculadora.calc.CotacaoPtax
import dev.lcv.calculadora.calc.CotacaoSpotBruta
import dev.lcv.calculadora.data.persistencia.PtaxCacheDao
import dev.lcv.calculadora.data.persistencia.PtaxCacheEntity
import dev.lcv.calculadora.data.persistencia.UltimoSpotDao
import dev.lcv.calculadora.data.persistencia.UltimoSpotEntity
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cotações como o motor as consome: a PTAX do cartão, a spot bruta da conta
 * global e o último spot calibrado guardado no aparelho.
 */
@Singleton
class CotacoesRepository @Inject constructor(
    private val provedorPtax: ProvedorPtax,
    private val provedorSpot: ProvedorSpot,
    private val ptaxCache: PtaxCacheDao,
    private val ultimoSpotDao: UltimoSpotDao,
    private val relogio: Clock,
) {
    private val memoSpot = mutableMapOf<String, Pair<Instant, CotacaoSpotBruta>>()
    private val trava = Mutex()

    /**
     * PTAX do dia da compra ou do dia útil anterior mais próximo, recuando até
     * [RECUO_MAXIMO_DIAS] dias como o produto web. O cache é consultado antes de
     * cada fonte e alimentado por cada acerto; a PTAX é diária e não expira.
     */
    suspend fun ptax(moeda: String, dataCompra: LocalDate): CotacaoPtax? {
        for (recuo in 0 until RECUO_MAXIMO_DIAS) {
            val dia = dataCompra.minusDays(recuo.toLong())
            ptaxCache.buscar(moeda, dia)?.let { return CotacaoPtax(it.taxa, dia) }
            val taxa = provedorPtax.ptaxDoDia(moeda, dia) ?: continue
            ptaxCache.guardar(PtaxCacheEntity(moeda, dia, taxa))
            return CotacaoPtax(taxa, dia)
        }
        return null
    }

    /**
     * Spot bruta, com memo em memória de [JANELA_MEMO]: substitui a chave por
     * minuto que o produto web guardava no banco. `null` quando nenhuma fonte
     * respondeu — o motor então usa o último spot salvo ou a PTAX.
     */
    suspend fun spotBruta(moeda: String): CotacaoSpotBruta? = trava.withLock {
        val agora = relogio.instant()
        memoSpot[moeda]?.let { (quando, spot) ->
            // Idade negativa = relógio de parede ajustado para trás: o memo não
            // pode congelar até o relógio alcançar `quando`; conta como expirado.
            val idade = Duration.between(quando, agora)
            if (!idade.isNegative && idade < JANELA_MEMO) return spot
        }
        val spot = provedorSpot.spotBruta(moeda) ?: return null
        memoSpot[moeda] = agora to spot
        spot
    }

    suspend fun ultimoSpotCalibrado(moeda: String): BigDecimal? = ultimoSpotDao.buscar(moeda)?.taxaCalibrada

    suspend fun guardarUltimoSpotCalibrado(moeda: String, taxaCalibrada: BigDecimal) {
        ultimoSpotDao.guardar(UltimoSpotEntity(moeda, taxaCalibrada, relogio.millis()))
    }

    companion object {
        const val RECUO_MAXIMO_DIAS = 7
        val JANELA_MEMO: Duration = Duration.ofSeconds(60)
    }
}
