/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.persistencia

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PtaxCacheEntity::class, UltimoSpotEntity::class, ObservacaoBacktestEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Conversores::class)
abstract class CalculadoraDatabase : RoomDatabase() {
    abstract fun ptaxCache(): PtaxCacheDao

    abstract fun ultimoSpot(): UltimoSpotDao

    abstract fun backtest(): BacktestDao

    companion object {
        const val NOME = "calculadora.db"
        /** V1 não distinguia boletins provisórios nem preservava a idade da fonte.
         * Somente dados derivados são invalidados; nenhum formulário é armazenado aqui. */
        val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM ptax_cache")
                db.execSQL("DELETE FROM ultimo_spot")
                db.execSQL("DELETE FROM backtest_observacao")
                db.execSQL("CREATE UNIQUE INDEX index_backtest_observacao_moeda_dataCompra ON backtest_observacao (moeda, dataCompra)")
            }
        }
    }
}
