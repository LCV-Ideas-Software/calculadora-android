/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeriadosBrasilTest {
    @Test
    fun `pascoa confere com datas conhecidas de varios anos`() {
        val esperadas = mapOf(
            1954 to LocalDate.of(1954, 4, 18),
            2000 to LocalDate.of(2000, 4, 23),
            2024 to LocalDate.of(2024, 3, 31),
            2025 to LocalDate.of(2025, 4, 20),
            2026 to LocalDate.of(2026, 4, 5),
            2027 to LocalDate.of(2027, 3, 28),
            2038 to LocalDate.of(2038, 4, 25),
            2100 to LocalDate.of(2100, 3, 28),
        )
        for ((ano, data) in esperadas) {
            assertEquals(data, FeriadosBrasil.pascoa(ano), "Páscoa de $ano")
        }
    }

    @Test
    fun `feriados moveis de 2026 batem com a tabela que o produto web mantinha`() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 2, 17),
                LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 6, 4),
            ),
            FeriadosBrasil.moveis(2026),
        )
    }

    @Test
    fun `feriados moveis existem para qualquer ano, nao so para 2026`() {
        // Páscoa de 2027 é 28/03: Carnaval 08 e 09/02, Sexta-feira Santa 26/03, Corpus Christi 27/05.
        assertEquals(
            listOf(
                LocalDate.of(2027, 2, 8),
                LocalDate.of(2027, 2, 9),
                LocalDate.of(2027, 3, 26),
                LocalDate.of(2027, 5, 27),
            ),
            FeriadosBrasil.moveis(2027),
        )
        assertTrue(FeriadosBrasil.isFeriado(LocalDate.of(2027, 2, 9)))
    }

    @Test
    fun `feriados fixos`() {
        assertTrue(FeriadosBrasil.isFeriado(LocalDate.of(2026, 12, 25)))
        assertTrue(FeriadosBrasil.isFeriado(LocalDate.of(2031, 11, 20)))
        assertTrue(FeriadosBrasil.isFeriado(LocalDate.of(2026, 4, 21)))
    }

    @Test
    fun `dia comum nao e feriado`() {
        assertFalse(FeriadosBrasil.isFeriado(LocalDate.of(2026, 3, 23)))
        // Domingo de Páscoa não é feriado nacional; os dias ao redor são.
        assertFalse(FeriadosBrasil.isFeriado(LocalDate.of(2026, 4, 5)))
    }
}
