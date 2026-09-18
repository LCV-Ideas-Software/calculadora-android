/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Contexto operacional no fuso de Brasília: decide se a conta global opera com
 * o spread de mercado aberto ou o de plantão. Plantão é fim de semana, feriado
 * nacional ou fora da janela das 9h às 17h.
 */
data class ContextoOperacional(
    val hora: Int,
    val minuto: Int,
    val diaSemana: DayOfWeek,
    val data: LocalDate,
    val feriado: Boolean,
    val plantao: Boolean,
) {
    companion object {
        val FUSO_BRASILIA: ZoneId = ZoneId.of("America/Sao_Paulo")
        const val ABERTURA_MERCADO_HORA = 9
        const val FECHAMENTO_MERCADO_HORA = 17

        fun em(instante: Instant, fuso: ZoneId = FUSO_BRASILIA): ContextoOperacional {
            val local = instante.atZone(fuso)
            val data = local.toLocalDate()
            val feriado = FeriadosBrasil.isFeriado(data)
            val fimDeSemana = local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY
            val foraDaJanela = local.hour < ABERTURA_MERCADO_HORA || local.hour >= FECHAMENTO_MERCADO_HORA
            return ContextoOperacional(
                hora = local.hour,
                minuto = local.minute,
                diaSemana = local.dayOfWeek,
                data = data,
                feriado = feriado,
                plantao = fimDeSemana || feriado || foraDaJanela,
            )
        }
    }
}
