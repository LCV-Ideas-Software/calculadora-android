/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.persistencia

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/** PTAX de venda encontrada para (moeda, dia). A PTAX é diária: o cache não expira. */
@Entity(tableName = "ptax_cache", primaryKeys = ["moeda", "data"])
data class PtaxCacheEntity(
    val moeda: String,
    val data: LocalDate,
    val taxa: BigDecimal,
)

/**
 * Último spot **calibrado** guardado por moeda: a contingência quando nenhuma
 * fonte responde (o `LATEST_SPOT` do produto web).
 */
@Entity(tableName = "ultimo_spot")
data class UltimoSpotEntity(
    @PrimaryKey val moeda: String,
    val taxaCalibrada: BigDecimal,
    /** Instante da obtenção, em milissegundos desde a época. */
    val obtidoEm: Long,
)

/** Uma observação do backtest: spot calibrado previsto contra a PTAX observada. */
@Entity(tableName = "backtest_observacao", indices = [Index("criadoEm")])
data class ObservacaoBacktestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Instante do registro, em milissegundos desde a época. */
    val criadoEm: Long,
    val moeda: String,
    val dataCompra: LocalDate,
    val taxaPrevista: BigDecimal,
    val taxaObservada: BigDecimal,
    val erroPercentual: BigDecimal,
)
