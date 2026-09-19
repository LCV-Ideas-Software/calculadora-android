/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.persistencia

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Dinheiro nunca em ponto flutuante (regra do operador): `BigDecimal` é
 * guardado como texto exato, e lido de volta sem perda. Datas em ISO-8601.
 */
class Conversores {
    @TypeConverter
    fun deDecimal(valor: BigDecimal?): String? = valor?.toPlainString()

    @TypeConverter
    fun paraDecimal(texto: String?): BigDecimal? = texto?.let(::BigDecimal)

    @TypeConverter
    fun deData(data: LocalDate?): String? = data?.toString()

    @TypeConverter
    fun paraData(texto: String?): LocalDate? = texto?.let(LocalDate::parse)
}
