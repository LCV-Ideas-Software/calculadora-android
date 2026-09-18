/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextoOperacionalTest {
    @Test
    fun `dia util em horario de mercado - aberto`() {
        // 23/03/2026 15:30 UTC = 12:30 em Brasília, segunda-feira.
        val ctx = ContextoOperacional.em(Instant.parse("2026-03-23T15:30:00Z"))
        assertEquals(LocalDate.of(2026, 3, 23), ctx.data)
        assertEquals(DayOfWeek.MONDAY, ctx.diaSemana)
        assertEquals(12, ctx.hora)
        assertEquals(30, ctx.minuto)
        assertFalse(ctx.feriado)
        assertFalse(ctx.plantao)
    }

    @Test
    fun `fim de semana e plantao`() {
        val ctx = ContextoOperacional.em(Instant.parse("2026-03-22T15:30:00Z"))
        assertEquals(DayOfWeek.SUNDAY, ctx.diaSemana)
        assertTrue(ctx.plantao)
    }

    @Test
    fun `feriado fixo e plantao`() {
        val ctx = ContextoOperacional.em(Instant.parse("2026-12-25T15:00:00Z"))
        assertTrue(ctx.feriado)
        assertTrue(ctx.plantao)
    }

    @Test
    fun `terca de Carnaval calculada pela Pascoa e plantao`() {
        val ctx = ContextoOperacional.em(Instant.parse("2026-02-17T14:00:00Z"))
        assertEquals(LocalDate.of(2026, 2, 17), ctx.data)
        assertTrue(ctx.feriado)
        assertTrue(ctx.plantao)
    }

    @Test
    fun `janela de mercado - abre as 9h e fecha as 17h`() {
        // Quarta-feira 25/03/2026. 11:59 UTC = 08:59 → plantão; 12:00 UTC = 09:00 → aberto;
        // 19:59 UTC = 16:59 → aberto; 20:00 UTC = 17:00 → plantão.
        assertTrue(ContextoOperacional.em(Instant.parse("2026-03-25T11:59:00Z")).plantao)
        assertFalse(ContextoOperacional.em(Instant.parse("2026-03-25T12:00:00Z")).plantao)
        assertFalse(ContextoOperacional.em(Instant.parse("2026-03-25T19:59:00Z")).plantao)
        assertTrue(ContextoOperacional.em(Instant.parse("2026-03-25T20:00:00Z")).plantao)
    }

    @Test
    fun `a data e a de Brasilia, nao a UTC`() {
        // 01:00 UTC de sábado ainda é sexta-feira 22h em Brasília.
        val ctx = ContextoOperacional.em(Instant.parse("2026-03-28T01:00:00Z"))
        assertEquals(LocalDate.of(2026, 3, 27), ctx.data)
        assertEquals(DayOfWeek.FRIDAY, ctx.diaSemana)
        assertEquals(22, ctx.hora)
    }
}
