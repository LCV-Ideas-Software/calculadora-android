/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data

import dev.lcv.calculadora.data.persistencia.BacktestDao
import dev.lcv.calculadora.data.persistencia.ObservacaoBacktestEntity
import dev.lcv.calculadora.data.persistencia.PtaxCacheDao
import dev.lcv.calculadora.data.persistencia.PtaxCacheEntity
import dev.lcv.calculadora.data.persistencia.UltimoSpotDao
import dev.lcv.calculadora.data.persistencia.UltimoSpotEntity
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Compara pelo valor numérico, indiferente à escala (`1.50` == `1.5`). */
internal fun assertDecimal(esperado: String, obtido: BigDecimal?, mensagem: String? = null) {
    assertNotNull(obtido, mensagem ?: "esperado $esperado, obtido null")
    assertTrue(
        BigDecimal(esperado).compareTo(obtido) == 0,
        (mensagem?.let { "$it: " } ?: "") + "esperado $esperado, obtido ${obtido.toPlainString()}",
    )
}

internal fun dec(valor: String): BigDecimal = BigDecimal(valor)

/** Conteúdo de um arquivo em `src/test/resources/fixtures` — cargas reais gravadas em 19/09/2026. */
internal fun fixture(nome: String): String =
    checkNotNull(ClassLoader.getSystemClassLoader().getResourceAsStream("fixtures/$nome")) {
        "fixture ausente: $nome"
    }.use { it.readBytes().decodeToString() }

/** Relógio que só anda quando o teste manda. */
internal class RelogioFixo(inicio: Instant) : Clock() {
    var agora: Instant = inicio
        private set

    fun avancar(duracao: Duration) {
        agora += duracao
    }

    fun recuar(duracao: Duration) {
        agora -= duracao
    }

    override fun getZone(): ZoneOffset = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId): Clock = this

    override fun instant(): Instant = agora
}

/** Os DAOs são interfaces do Room; em memória, provam os repositórios sem banco. */
internal class PtaxCacheEmMemoria : PtaxCacheDao {
    val linhas = mutableMapOf<Pair<String, LocalDate>, PtaxCacheEntity>()

    override suspend fun buscar(moeda: String, data: LocalDate): PtaxCacheEntity? = linhas[moeda to data]

    override suspend fun guardar(entidade: PtaxCacheEntity) {
        linhas[entidade.moeda to entidade.data] = entidade
    }
}

internal class UltimoSpotEmMemoria : UltimoSpotDao {
    val linhas = mutableMapOf<String, UltimoSpotEntity>()

    override suspend fun buscar(moeda: String): UltimoSpotEntity? = linhas[moeda]

    override suspend fun guardar(entidade: UltimoSpotEntity) {
        linhas[entidade.moeda] = entidade
    }
}

internal class BacktestEmMemoria : BacktestDao {
    val linhas = mutableListOf<ObservacaoBacktestEntity>()
    var ultimaConsulta: Pair<Long, Int>? = null
        private set
    var ultimaPoda: Long? = null
        private set

    override suspend fun inserir(observacao: ObservacaoBacktestEntity) {
        linhas += observacao.copy(id = (linhas.size + 1).toLong())
    }

    override suspend fun desde(desde: Long, limite: Int): List<ObservacaoBacktestEntity> {
        ultimaConsulta = desde to limite
        return linhas.filter { it.criadoEm >= desde }.sortedByDescending { it.criadoEm }.take(limite)
    }

    override suspend fun apagarAnterioresA(antesDe: Long): Int {
        ultimaPoda = antesDe
        val antes = linhas.size
        linhas.removeAll { it.criadoEm < antesDe }
        return antes - linhas.size
    }
}
