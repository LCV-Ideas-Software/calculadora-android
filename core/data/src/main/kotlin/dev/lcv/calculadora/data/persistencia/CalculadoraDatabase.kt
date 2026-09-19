/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.persistencia

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PtaxCacheEntity::class, UltimoSpotEntity::class, ObservacaoBacktestEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Conversores::class)
abstract class CalculadoraDatabase : RoomDatabase() {
    abstract fun ptaxCache(): PtaxCacheDao

    abstract fun ultimoSpot(): UltimoSpotDao

    abstract fun backtest(): BacktestDao

    companion object {
        const val NOME = "calculadora.db"
    }
}
