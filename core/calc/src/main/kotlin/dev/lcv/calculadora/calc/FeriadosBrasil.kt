/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.time.LocalDate
import java.time.MonthDay

/**
 * Feriados nacionais brasileiros.
 *
 * Os fixos ficam em lista, porque são fixos. Os móveis — Carnaval, Sexta-feira
 * Santa e Corpus Christi — são derivados da data da Páscoa pelo algoritmo de
 * cômputo de Meeus/Jones/Butcher, válido para qualquer ano do calendário
 * gregoriano. Sem tabela por ano e sem terceiro: por decisão do operador em
 * 18/09/2026, onde a plataforma permite fazer melhor que o produto web,
 * faz-se melhor.
 */
object FeriadosBrasil {
    val FIXOS: List<MonthDay> = listOf(
        MonthDay.of(1, 1), // Confraternização Universal
        MonthDay.of(4, 21), // Tiradentes
        MonthDay.of(5, 1), // Dia do Trabalhador
        MonthDay.of(9, 7), // Independência do Brasil
        MonthDay.of(10, 12), // Nossa Senhora Aparecida
        MonthDay.of(11, 2), // Finados
        MonthDay.of(11, 15), // Proclamação da República
        MonthDay.of(11, 20), // Dia Nacional de Zumbi e da Consciência Negra
        MonthDay.of(12, 25), // Natal
    )

    /** Domingo de Páscoa pelo cômputo de Meeus/Jones/Butcher (calendário gregoriano). */
    fun pascoa(ano: Int): LocalDate {
        val a = ano % 19
        val b = ano / 100
        val c = ano % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val mes = (h + l - 7 * m + 114) / 31
        val dia = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(ano, mes, dia)
    }

    /** Segunda e terça-feira de Carnaval, Sexta-feira Santa e Corpus Christi. */
    fun moveis(ano: Int): List<LocalDate> {
        val pascoa = pascoa(ano)
        return listOf(
            pascoa.minusDays(48), // segunda-feira de Carnaval
            pascoa.minusDays(47), // terça-feira de Carnaval
            pascoa.minusDays(2), // Sexta-feira Santa
            pascoa.plusDays(60), // Corpus Christi
        )
    }

    fun isFeriado(data: LocalDate): Boolean =
        MonthDay.from(data) in FIXOS || data in moveis(data.year)
}
