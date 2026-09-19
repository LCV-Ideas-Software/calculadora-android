/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.persistencia

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDate

@Dao
interface PtaxCacheDao {
    @Query("SELECT * FROM ptax_cache WHERE moeda = :moeda AND data = :data")
    suspend fun buscar(moeda: String, data: LocalDate): PtaxCacheEntity?

    @Upsert
    suspend fun guardar(entidade: PtaxCacheEntity)
}

@Dao
interface UltimoSpotDao {
    @Query("SELECT * FROM ultimo_spot WHERE moeda = :moeda")
    suspend fun buscar(moeda: String): UltimoSpotEntity?

    @Upsert
    suspend fun guardar(entidade: UltimoSpotEntity)
}

@Dao
interface BacktestDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(observacao: ObservacaoBacktestEntity)

    /** Observações desde [desde] (inclusive), mais recentes primeiro, no máximo [limite]. */
    @Query("SELECT * FROM backtest_observacao WHERE criadoEm >= :desde ORDER BY criadoEm DESC LIMIT :limite")
    suspend fun desde(desde: Long, limite: Int): List<ObservacaoBacktestEntity>

    /** Apaga as observações anteriores a [antesDe]. Devolve quantas saíram. */
    @Query("DELETE FROM backtest_observacao WHERE criadoEm < :antesDe")
    suspend fun apagarAnterioresA(antesDe: Long): Int
}
