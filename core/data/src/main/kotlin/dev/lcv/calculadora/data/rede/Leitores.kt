/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.rede

import java.time.Instant
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Leitura dos formatos de cada fonte, sem classes de dados nem plugin de
 * compilador: cada fonte devolve um único número, lido do JSON como texto e
 * convertido a `BigDecimal` sem passar por ponto flutuante.
 *
 * Formatos medidos em 19/09/2026:
 * - Olinda dólar: `{"value":[{"cotacaoCompra":5.1569,"cotacaoVenda":5.1575,...}]}`
 * - Olinda moeda: `{"value":[{...,"cotacaoVenda":5.9005,"tipoBoletim":"Abertura"}, ...]}`
 * - AwesomeAPI: `{"USDBRL":{"bid":"5.1434",...}}`
 * - Yahoo: `{"chart":{"result":[{"meta":{"regularMarketPrice":5.1421,...}}]}}`
 */
object Leitores {
    private val json = Json { ignoreUnknownKeys = true }

    /** Os boletins do dia (`value`), possivelmente nenhum; `null` se a resposta não for uma resposta da Olinda. */
    fun olindaBoletins(corpo: String): List<JsonElement>? =
        parse(corpo)?.objeto?.get("value")?.let { runCatching { it.jsonArray.toList() }.getOrNull() }

    /** `cotacaoVenda` do único boletim do dólar. */
    fun cotacaoVendaDolar(boletins: List<JsonElement>): BigDecimal? = boletins.firstOrNull()?.decimal("cotacaoVenda")

    /**
     * `cotacaoVenda` do boletim `Fechamento` (ou `Fechamento PTAX`); na sua
     * ausência, não há taxa final para persistir.
     */
    fun cotacaoVendaFechamento(boletins: List<JsonElement>): BigDecimal? {
        if (boletins.isEmpty()) return null
        val fechamento = boletins.firstOrNull { b ->
            val tipo = b.texto("tipoBoletim")
            tipo == "Fechamento" || tipo == "Fechamento PTAX"
        }
        return fechamento?.decimal("cotacaoVenda")
    }

    /** `bid` do par `<MOEDA>BRL`. */
    fun awesome(corpo: String, moeda: String): BigDecimal? = parse(corpo)?.objeto?.get("${moeda}BRL")?.decimal("bid")

    /** `chart.result[0].meta.regularMarketPrice`. */
    fun yahoo(corpo: String): BigDecimal? =
        parse(corpo)?.objeto?.get("chart")?.objeto?.get("result")
            ?.let { runCatching { it.jsonArray.firstOrNull() }.getOrNull() }
            ?.objeto?.get("meta")?.decimal("regularMarketPrice")

    fun instanteAwesome(corpo: String, moeda: String): Instant? =
        parse(corpo)?.objeto?.get("${moeda}BRL")?.instante("timestamp")

    fun instanteYahoo(corpo: String): Instant? =
        parse(corpo)?.objeto?.get("chart")?.objeto?.get("result")
            ?.let { runCatching { it.jsonArray.firstOrNull() }.getOrNull() }
            ?.objeto?.get("meta")?.instante("regularMarketTime")

    private fun JsonElement.instante(chave: String): Instant? =
        texto(chave)?.toLongOrNull()?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() }

    private fun parse(corpo: String): JsonElement? = runCatching { json.parseToJsonElement(corpo) }.getOrNull()

    private val JsonElement.objeto: JsonObject?
        get() = this as? JsonObject

    private fun JsonElement.texto(chave: String): String? =
        objeto?.get(chave)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    /** JSON não é entrada localizada: preserva precisão da fonte, com limites de custo. */
    private fun JsonElement.decimal(chave: String): BigDecimal? =
        texto(chave)?.takeIf { it.length <= 40 }?.toBigDecimalOrNull()?.takeIf {
            it.signum() > 0 && it.precision() <= 30 && it.scale() in -18..18 &&
                it.precision() - it.scale() <= 12
        }
}
